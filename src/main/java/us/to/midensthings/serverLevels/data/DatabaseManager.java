package us.to.midensthings.serverLevels.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class DatabaseManager {

    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    // volatile because async tasks read these while a reload may be swapping them out
    private volatile HikariDataSource dataSource;
    private volatile String dataFormat;
    // Every database operation goes through this single thread, so they always run in the order they were queued.
    // e.g. a player's quit save always finishes before their next login loads their data.
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "ServerLevels-DB"));

    /***
     * A level system to load a player's data from.
     * @param system the level system's name
     * @param autoAdd whether to add the player to the system if they aren't in it yet
     * @param expMultiplier the player's exp multiplier for the system
     */
    @ApiStatus.Internal
    public record SystemLoad(String system, boolean autoAdd, double expMultiplier) {}


    private static final String DEFAULT_TABLE_PREFIX = "sys_";
    private static final java.util.regex.Pattern VALID_TABLE_PREFIX = java.util.regex.Pattern.compile("[A-Za-z0-9_]*");
    // Put in front of each level system's name for its table name
    private volatile String tablePrefix = DEFAULT_TABLE_PREFIX;

    /***
     * @return the database table that holds the level system's player data
     */
    public String getTableName(String system) {
        return tablePrefix + system;
    }

    // Connection settings from config.yml, read on the main thread so the database thread never touches the live config
    private record DatabaseSettings(String dataFormat, String host, String database, String username, String password,
                                    int poolSize, String sslMode, String tablePrefix) {
        static DatabaseSettings fromConfig(FileConfiguration config, String dataFormat) {
            return new DatabaseSettings(dataFormat,
                    config.getString("mysql.host"),
                    config.getString("mysql.database"),
                    config.getString("mysql.username"),
                    config.getString("mysql.password"),
                    config.getInt("mysql.pool-size", 10),
                    config.getString("mysql.ssl-mode", "PREFERRED"),
                    config.getString("table-prefix", DEFAULT_TABLE_PREFIX));
        }

        static DatabaseSettings fromConfig(FileConfiguration config) {
            return fromConfig(config, config.getString("data-format", "h2"));
        }
    }

    public void setupConnectionPool(String dataFormat) {
        setupConnectionPool(DatabaseSettings.fromConfig(plugin.getConfig(), dataFormat));
    }

    private void setupConnectionPool(DatabaseSettings settings) {
        // The prefix goes directly into table names, so only allow safe identifier characters
        String prefix = settings.tablePrefix() == null ? DEFAULT_TABLE_PREFIX : settings.tablePrefix();
        if (!VALID_TABLE_PREFIX.matcher(prefix).matches()) {
            plugin.getLogger().warning("Invalid table-prefix '" + prefix + "' in config.yml, using " + DEFAULT_TABLE_PREFIX
                    + ". It can only contain letters, numbers and underscores");
            prefix = DEFAULT_TABLE_PREFIX;
        }
        tablePrefix = prefix;

        HikariConfig hkconfig = new HikariConfig();
        this.dataFormat = settings.dataFormat().toLowerCase(Locale.ROOT);
        switch (this.dataFormat) {
            case ("mysql"):
                hkconfig.setJdbcUrl("jdbc:mysql://" + settings.host() + "/" + settings.database()); // Address of your running MySQL database
                hkconfig.setUsername(settings.username()); // Username
                hkconfig.setPassword(settings.password()); // Password
                hkconfig.setMaximumPoolSize(Math.max(1, settings.poolSize()));
                // Passed straight to the MySQL driver: DISABLED, PREFERRED, REQUIRED, VERIFY_CA or VERIFY_IDENTITY
                if (settings.sslMode() != null && !settings.sslMode().isBlank()) {
                    hkconfig.addDataSourceProperty("sslMode", settings.sslMode().trim().toUpperCase(Locale.ROOT));
                }
                break;
            case ("sqlite"):
                hkconfig.setDriverClassName("org.sqlite.JDBC");
                hkconfig.setJdbcUrl("jdbc:sqlite:" + new File(plugin.getDataFolder(), "database.db").getAbsolutePath());
                // SQLite only allows one writer at a time, more connections just cause "database is locked" errors
                hkconfig.setMaximumPoolSize(1);
                break;
            default:
                // default to h2
                this.dataFormat = "h2";
                hkconfig.setDriverClassName("org.h2.Driver");
                hkconfig.setJdbcUrl("jdbc:h2:" + plugin.getDataFolder().getAbsolutePath() + "/database");
                hkconfig.setMaximumPoolSize(10);
                break;
        }

        // Swap in the new pool before closing the old one
        HikariDataSource oldDataSource = dataSource;
        dataSource = new HikariDataSource(hkconfig);
        if (oldDataSource != null) {
            oldDataSource.close();
        }
    }

    /***
     * Sets up the connection pool and creates any missing tables.
     * Runs on the database thread and waits for it, so any queued saves finish against the old pool first on reload.
     */
    public void setupDatabase() {
        DatabaseSettings settings = DatabaseSettings.fromConfig(plugin.getConfig());
        Set<String> systemNames = plugin.getLevelSystemsConf().getKeys(false);
        callOnDbThread(() -> {
            setupDatabaseNow(settings, systemNames);
            return null;
        });
    }

    /***
     * Sets up the connection pool and creates any missing tables in the background.
     * Runs on the database thread, so any queued saves finish against the old pool first.
     * Settings are read from the given configs before this returns.
     * @param config the plugin's config.yml
     * @param levelSystemsConf the levelsystems.yml to create tables for
     * @return a future that completes on the database thread once the database is ready
     */
    @ApiStatus.Internal
    public CompletableFuture<Void> setupDatabaseAsync(FileConfiguration config, YamlConfiguration levelSystemsConf) {
        DatabaseSettings settings = DatabaseSettings.fromConfig(config);
        Set<String> systemNames = levelSystemsConf.getKeys(false);
        return CompletableFuture.runAsync(() -> setupDatabaseNow(settings, systemNames), dbExecutor);
    }

    private void setupDatabaseNow(DatabaseSettings settings, Set<String> systemNames) {
        /*
        1. Check for what database system the plugin is currently using
        2. Initialize database and database manager accoring to the proper system
            - create Table for every level system
         */
        setupConnectionPool(settings);

        // Create the connection
        try (Connection connection = dataSource.getConnection()) {
            createSchemaVersionTable(connection);
            // Generate a table for every level system, and bring older tables up to the current schema
            systemNames.forEach(system -> createTable(connection, system));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /***
     * Creates the table for a new level system, if it doesn't exist yet. Runs on the database thread,
     * so anything queued for the system afterwards (like adding players) happens after the table exists.
     */
    public void createTable(String system) {
        runOnDbThread(() -> {
            try (Connection connection = dataSource.getConnection()) {
                createSchemaVersionTable(connection);
                createTable(connection, system);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /*
     Schema versions of the level system tables (sys_<system>):
       1: uuid, exp, level. Tables from before schema versions were tracked
       2: adds paused
     When changing the schema, bump CURRENT_SCHEMA_VERSION, update createSystemTableSql, and add a migration step in migrateTable.
     */
    private static final int CURRENT_SCHEMA_VERSION = 2;
    // Stores the schema version of each level system table
    private static final String SCHEMA_VERSION_TABLE = "sl_schema_version";
    // Temporary tables used while migrating, named after the full table name. A different prefix from the table prefix, so they
    // can't clash with a level system's table
    private static final String MIGRATION_NEW_PREFIX = "slmig_new_";
    private static final String MIGRATION_OLD_PREFIX = "slmig_old_";

    // The SQL for a level system table with the current schema
    private static String createSystemTableSql(String table) {
        // Each table holds a player's uuid, their current exp, their current level, and whether their exp gains are paused
        return "CREATE TABLE " + table + " ( "
                + "uuid CHAR(36) PRIMARY KEY, "
                + "exp DOUBLE, "
                + "level INT, "
                + "paused BOOLEAN NOT NULL DEFAULT FALSE)";
    }

    private void createSchemaVersionTable(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + SCHEMA_VERSION_TABLE + " ("
                    + "table_name VARCHAR(64) PRIMARY KEY, "
                    + "version INT NOT NULL)");
        }
    }

    /***
     * Creates the level system's table if it doesn't exist yet, or migrates it to the current schema if it's outdated
     */
    private void createTable(Connection connection, String system) {
        // System names go directly into the table name, so skip anything that isn't a safe identifier
        if (!LevelSystemRegistry.isValidSystemName(system)) {
            return;
        }
        // sys_ prefix for table names for consistency
        String table = getTableName(system);

        try {
            recoverInterruptedMigration(connection, system);

            if (!tableExists(connection, table)) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(createSystemTableSql(table));
                }
                setSchemaVersion(connection, table, CURRENT_SCHEMA_VERSION);
                return;
            }

            int version = getSchemaVersion(connection, table);
            if (version < CURRENT_SCHEMA_VERSION) {
                migrateTable(connection, system, version);
            } else if (version > CURRENT_SCHEMA_VERSION) {
                plugin.getLogger().warning("Table " + table + " uses database schema version " + version
                        + ", which is newer than this version of ServerLevels supports (" + CURRENT_SCHEMA_VERSION + "). Did you downgrade the plugin?");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not set up the table for level system " + system + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /***
     * Migrates a level system's table to the current schema. The data is copied into a new table with the current schema,
     * the row counts are checked, then the new table replaces the old one.
     */
    private void migrateTable(Connection connection, String system, int fromVersion) throws SQLException {
        String table = getTableName(system);
        String newTable = MIGRATION_NEW_PREFIX + table;
        String oldTable = MIGRATION_OLD_PREFIX + table;
        plugin.getLogger().info("Migrating table " + table + " from database schema version " + fromVersion + " to " + CURRENT_SCHEMA_VERSION + "...");

        try (Statement stmt = connection.createStatement()) {
            // Clear out a copy left by an earlier migration that failed before the swap
            if (tableExists(connection, newTable)) {
                stmt.execute("DROP TABLE " + newTable);
            }
            stmt.execute(createSystemTableSql(newTable));

            // Copy every row, filling in columns the old schema doesn't have
            String copiedColumns = switch (fromVersion) {
                // Version 1 has no paused column, nobody is paused
                case 1 -> "uuid, exp, level, FALSE";
                default -> throw new SQLException("No migration from database schema version " + fromVersion);
            };
            stmt.executeUpdate("INSERT INTO " + newTable + " (uuid, exp, level, paused) SELECT " + copiedColumns + " FROM " + table);

            long oldRows = countRows(connection, table);
            long newRows = countRows(connection, newTable);
            if (oldRows != newRows) {
                stmt.execute("DROP TABLE " + newTable);
                throw new SQLException("Copied " + newRows + " of " + oldRows + " rows, leaving " + table + " unchanged");
            }

            // Swap the tables. If the server stops partway through, recoverInterruptedMigration finishes or undoes it on the next start
            stmt.execute("ALTER TABLE " + table + " RENAME TO " + oldTable);
            stmt.execute("ALTER TABLE " + newTable + " RENAME TO " + table);
            stmt.execute("DROP TABLE " + oldTable);
        }
        setSchemaVersion(connection, table, CURRENT_SCHEMA_VERSION);
        plugin.getLogger().info("Migrated table " + table + " (" + countRows(connection, table) + " players)");
    }

    /***
     * Cleans up after a migration that was interrupted, e.g. by a crash, while swapping the tables
     */
    private void recoverInterruptedMigration(Connection connection, String system) throws SQLException {
        String table = getTableName(system);
        String newTable = MIGRATION_NEW_PREFIX + table;
        String oldTable = MIGRATION_OLD_PREFIX + table;
        if (!tableExists(connection, oldTable)) {
            // The swap never started. A leftover copy is cleared when the migration runs again
            return;
        }

        try (Statement stmt = connection.createStatement()) {
            if (tableExists(connection, table)) {
                // Stopped after the new table was swapped in, only the old table's removal is left
                stmt.execute("DROP TABLE " + oldTable);
                setSchemaVersion(connection, table, CURRENT_SCHEMA_VERSION);
            } else if (tableExists(connection, newTable)) {
                // Stopped between the two renames. The new table has every row (checked before the swap), so finish the swap
                stmt.execute("ALTER TABLE " + newTable + " RENAME TO " + table);
                stmt.execute("DROP TABLE " + oldTable);
                setSchemaVersion(connection, table, CURRENT_SCHEMA_VERSION);
            } else {
                // Shouldn't happen, but put the original table back rather than lose it
                stmt.execute("ALTER TABLE " + oldTable + " RENAME TO " + table);
            }
        }
        plugin.getLogger().warning("Recovered table " + table + " from an interrupted database migration");
    }

    // Works the same on H2, SQLite and MySQL, unlike DatabaseMetaData which differs in how it handles name case
    private boolean tableExists(Connection connection, String table) {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeQuery("SELECT 1 FROM " + table + " WHERE 1=0").close();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private long countRows(Connection connection, String table) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // Tables without a recorded version were created before versions were tracked, so they're version 1
    private int getSchemaVersion(Connection connection, String table) throws SQLException {
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT version FROM " + SCHEMA_VERSION_TABLE + " WHERE table_name=?")) {
            pstmt.setString(1, table);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    private void setSchemaVersion(Connection connection, String table, int version) throws SQLException {
        // Delete then insert, since each database has its own upsert syntax
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SCHEMA_VERSION_TABLE + " WHERE table_name=?");
             PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SCHEMA_VERSION_TABLE + " (table_name, version) VALUES (?, ?)")) {
            delete.setString(1, table);
            delete.executeUpdate();
            insert.setString(1, table);
            insert.setInt(2, version);
            insert.executeUpdate();
        }
    }

    /***
     Adds the player to the level system with 0 exp and level 0.
     Does nothing if the player is already in the system.
     */
    public void initializePlayer(UUID uuid, String system) {
        runOnDbThread(() -> {
            // Create the connection
            try (Connection connection = dataSource.getConnection()) {
                insertIfMissing(connection, uuid, system);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }


    /***
     Get the player's stats from the database. Waits for any queued saves to finish first.
     Prefer LeveledPlayer.fromUuid(), which uses the cache for online players.
     Returns null if the player isn't in the level system.
     */
    public LeveledPlayer getLeveledPlayer(UUID uuid, String system) {
        double expMultiplier = PlayerLoader.readExpMultiplier(Bukkit.getOfflinePlayer(uuid).getPersistentDataContainer(), system);
        return callOnDbThread(() -> selectPlayer(uuid, system, expMultiplier));
    }

    /***
     Get the player's stats from the database in the background, after any queued saves finish.
     Prefer LeveledPlayer.fromUuidAsync(), which uses the cache for online players.
     @return a future that completes on the database thread with the player's stats, or null if they aren't in the level system
     */
    public CompletableFuture<LeveledPlayer> getLeveledPlayerAsync(UUID uuid, String system) {
        double expMultiplier = PlayerLoader.readExpMultiplier(Bukkit.getOfflinePlayer(uuid).getPersistentDataContainer(), system);
        try {
            return CompletableFuture.supplyAsync(() -> selectPlayer(uuid, system, expMultiplier), dbExecutor);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(new IllegalStateException("The database has already been shut down", e));
        }
    }

    // Opens a connection and selects the player. Returns null if they have no record or the query fails
    private LeveledPlayer selectPlayer(UUID uuid, String system, double expMultiplier) {
        try (Connection connection = dataSource.getConnection()) {
            return selectPlayer(connection, uuid, system, expMultiplier);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /***
     * Loads the player's data for each of the systems, adding them to auto add systems first.
     * Blocks until done, so never call this on the main thread.
     * @return the loaded data by system name. Systems the player isn't in are left out.
     */
    @ApiStatus.Internal
    public Map<String, LeveledPlayer> loadPlayerNow(UUID uuid, List<SystemLoad> systems) {
        return callOnDbThread(() -> loadPlayerRecords(uuid, systems));
    }

    /***
     * Loads the player's data for each of the systems, adding them to auto add systems first.
     * @param onLoaded called on the main thread with the loaded data by system name. Systems the player isn't in are left out.
     */
    @ApiStatus.Internal
    public void loadPlayerAsync(UUID uuid, List<SystemLoad> systems, Consumer<Map<String, LeveledPlayer>> onLoaded) {
        runOnDbThread(() -> {
            Map<String, LeveledPlayer> loaded = loadPlayerRecords(uuid, systems);
            // Can't schedule tasks once the plugin is shutting down
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> onLoaded.accept(loaded));
            }
        });
    }

    private Map<String, LeveledPlayer> loadPlayerRecords(UUID uuid, List<SystemLoad> systems) {
        Map<String, LeveledPlayer> loaded = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            for (SystemLoad load : systems) {
                if (load.autoAdd()) {
                    insertIfMissing(connection, uuid, load.system());
                }
                LeveledPlayer lp = selectPlayer(connection, uuid, load.system(), load.expMultiplier());
                if (lp != null) {
                    loaded.put(load.system(), lp);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return loaded;
    }

    private void insertIfMissing(Connection connection, UUID uuid, String system) throws SQLException {
        // Generate default player stats for the level system.
        String createDefaultStats = switch (dataFormat) {
            case ("mysql") -> "INSERT IGNORE INTO " + getTableName(system) + " (uuid, exp, level) "
                    + "VALUES(?, 0.0, 0)";
            case ("sqlite") -> "INSERT OR IGNORE INTO " + getTableName(system) + " (uuid, exp, level) "
                    + "VALUES(?, 0.0, 0)";
            default ->
                // Default is h2
                    "MERGE INTO " + getTableName(system) + " t USING (SELECT CAST(? AS CHAR(36)) AS uuid) s "
                            + "ON t.uuid = s.uuid "
                            + "WHEN NOT MATCHED THEN INSERT (uuid, exp, level) VALUES (s.uuid, 0.0, 0)";
        };

        try (PreparedStatement pstmt = connection.prepareStatement(createDefaultStats)) {
            pstmt.setString(1, uuid.toString());
            pstmt.execute();
        }
    }

    // Returns null if the player has no record in the system
    private LeveledPlayer selectPlayer(Connection connection, UUID uuid, String system, double expMultiplier) throws SQLException {
        // select player all data from the table according to player's uuid
        String selectPlayerData = "SELECT uuid,exp,level,paused FROM " + getTableName(system) + " WHERE uuid=?";
        try (PreparedStatement pstmt = connection.prepareStatement(selectPlayerData)) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet resultSet = pstmt.executeQuery()) {
                if (!resultSet.next()) {
                    // Player has no record in this system
                    return null;
                }
                return new LeveledPlayer(system,
                        resultSet.getString("uuid"),
                        resultSet.getDouble("exp"),
                        resultSet.getInt("level"),
                        expMultiplier,
                        resultSet.getBoolean("paused"));
            }
        }
    }

    public void saveLeveledPlayer(LeveledPlayer lp) {
        // Capture the values now, the LeveledPlayer may change before the async task runs
        String system = lp.getSystemName();
        double exp = lp.getExp();
        int level = lp.getLevel();
        boolean paused = lp.isPaused();
        String uuid = lp.getUuid().toString();

        runOnDbThread(() -> {
            try (Connection connection = dataSource.getConnection()) {
                String updatePlayerData = "UPDATE " + getTableName(system) + " SET exp=?, level=?, paused=? WHERE uuid=?";
                try (PreparedStatement pstmt = connection.prepareStatement(updatePlayerData)) {
                    pstmt.setDouble(1, exp);
                    pstmt.setInt(2, level);
                    pstmt.setBoolean(3, paused);
                    pstmt.setString(4, uuid);
                    pstmt.executeUpdate();
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

    }

    @Deprecated
    public boolean playerHasRecord(Player p, String system) {
        return getLeveledPlayer(p.getUniqueId(), system) != null;
    }


    public void removePlayer(Player p, String levelSystem) {
        UUID uuid = p.getUniqueId();
        runOnDbThread(() -> {
            // Get connection
            try (Connection connection = dataSource.getConnection()) {
                String deleteRecord = "DELETE FROM " + getTableName(levelSystem) + " WHERE uuid = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(deleteRecord)) {
                    pstmt.setString(1, uuid.toString());

                    pstmt.executeUpdate();
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /***
     * Waits for all queued database operations to finish, then closes the connection pool.
     */
    public void shutDownDatabase() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Timed out waiting for player data to save! Some progress may be lost.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private void runOnDbThread(Runnable task) {
        try {
            dbExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            plugin.getLogger().warning("Tried to use the database after it was shut down, ignoring.");
        }
    }

    // Runs the task on the database thread and waits for its result. Never call this from the database thread itself.
    private <T> T callOnDbThread(Callable<T> task) {
        try {
            return dbExecutor.submit(task).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the database", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Database task failed", e.getCause());
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("The database has already been shut down", e);
        }
    }
}

