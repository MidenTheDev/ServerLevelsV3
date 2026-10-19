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


    // Connection settings from config.yml, read on the main thread so the database thread never touches the live config
    private record DatabaseSettings(String dataFormat, String host, String database, String username, String password) {
        static DatabaseSettings fromConfig(FileConfiguration config, String dataFormat) {
            return new DatabaseSettings(dataFormat,
                    config.getString("mysql.host"),
                    config.getString("mysql.database"),
                    config.getString("mysql.username"),
                    config.getString("mysql.password"));
        }

        static DatabaseSettings fromConfig(FileConfiguration config) {
            return fromConfig(config, config.getString("data-format", "h2"));
        }
    }

    public void setupConnectionPool(String dataFormat) {
        setupConnectionPool(DatabaseSettings.fromConfig(plugin.getConfig(), dataFormat));
    }

    private void setupConnectionPool(DatabaseSettings settings) {
        HikariConfig hkconfig = new HikariConfig();
        this.dataFormat = settings.dataFormat().toLowerCase(Locale.ROOT);
        switch (this.dataFormat) {
            case ("mysql"):
                hkconfig.setJdbcUrl("jdbc:mysql://" + settings.host() + "/" + settings.database()); // Address of your running MySQL database
                hkconfig.setUsername(settings.username()); // Username
                hkconfig.setPassword(settings.password()); // Password
                hkconfig.setMaximumPoolSize(10); // Pool size defaults to 10
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
            // Generate a table for every level system.
            systemNames.forEach(system -> {
                // System names go directly into the table name, so skip anything that isn't a safe identifier
                if (!LevelSystemRegistry.isValidSystemName(system)) {
                    return;
                }

                // Each table holds a player's uuid, their current exp, and their current level in its records.
                String createTable = "CREATE TABLE IF NOT EXISTS sys_" + system + " ( "
                        + "uuid CHAR(36) PRIMARY KEY, "
                        + "exp DOUBLE, "
                        + "level INT);";
                try (Statement stmt = connection.createStatement()) {
                    // sys_ prefix for table names for consistency

                    stmt.execute(createTable);

                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        } catch (SQLException e) {
            e.printStackTrace();
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
            case ("mysql") -> "INSERT IGNORE INTO sys_" + system + " (uuid, exp, level) "
                    + "VALUES(?, 0.0, 0)";
            case ("sqlite") -> "INSERT OR IGNORE INTO sys_" + system + " (uuid, exp, level) "
                    + "VALUES(?, 0.0, 0)";
            default ->
                // Default is h2
                    "MERGE INTO sys_" + system + " t USING (SELECT CAST(? AS CHAR(36)) AS uuid) s "
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
        String selectPlayerData = "SELECT uuid,exp,level FROM sys_" + system + " WHERE uuid=?";
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
                        expMultiplier);
            }
        }
    }

    public void saveLeveledPlayer(LeveledPlayer lp) {
        // Capture the values now, the LeveledPlayer may change before the async task runs
        String system = lp.getSystemName();
        double exp = lp.getExp();
        int level = lp.getLevel();
        String uuid = lp.getUuid().toString();

        runOnDbThread(() -> {
            try (Connection connection = dataSource.getConnection()) {
                String updatePlayerData = "UPDATE sys_" + system + " SET exp=?, level=? WHERE uuid=?";
                try (PreparedStatement pstmt = connection.prepareStatement(updatePlayerData)) {
                    pstmt.setDouble(1, exp);
                    pstmt.setInt(2, level);
                    pstmt.setString(3, uuid);
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
                String deleteRecord = "DELETE FROM sys_" + levelSystem + " WHERE uuid = ?";

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

