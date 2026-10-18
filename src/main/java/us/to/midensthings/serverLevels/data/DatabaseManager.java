package us.to.midensthings.serverLevels.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.ApiStatus;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;
import us.to.midensthings.serverLevels.util.NamespacedKeys;

import java.io.File;
import java.sql.*;
import java.util.Locale;

public class DatabaseManager {

    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    // volatile because async tasks read these while a reload may be swapping them out
    private volatile HikariDataSource dataSource;
    private volatile String dataFormat;


    public void setupConnectionPool(String dataFormat) {
        HikariConfig hkconfig = new HikariConfig();
        this.dataFormat = dataFormat.toLowerCase(Locale.ROOT);
        switch (this.dataFormat) {
            case ("mysql"):
                hkconfig.setJdbcUrl("jdbc:mysql://" + plugin.getConfig().getString("mysql.host") + "/" + plugin.getConfig().getString("mysql.database")); // Address of your running MySQL database
                hkconfig.setUsername(plugin.getConfig().getString("mysql.username")); // Username
                hkconfig.setPassword(plugin.getConfig().getString("mysql.password")); // Password
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

        // Swap in the new pool before closing the old one so in-flight async tasks aren't cut off on reload
        HikariDataSource oldDataSource = dataSource;
        dataSource = new HikariDataSource(hkconfig);
        if (oldDataSource != null) {
            oldDataSource.close();
        }
    }

    public void setupDatabase() {
        /*
        1. Check for what database system the plugin is currently using
        2. Initialize database and database manager accoring to the proper system
            - create Table for every level system
         */

        setupConnectionPool(plugin.getConfig().getString("data-format", "h2").toLowerCase(Locale.ROOT));

        // Create the connection
        try (Connection connection = dataSource.getConnection()) {
            // Generate a table for every level system.
            plugin.getLevelSystemsConf().getConfigurationSection("").getKeys(false).forEach(system -> {
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
    public void initializePlayer(Player p, String system) {

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Create the connection
            try (Connection connection = dataSource.getConnection()) {
                // Generate default player stats for each level system.

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
                    pstmt.setString(1, p.getUniqueId().toString());
                    pstmt.execute();

                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

    }


    /***
     Get the player's stats from the database.
     Returns null if the player isn't in the level system.
     */
    public LeveledPlayer getLeveledPlayer(Player p, String system) {
        // Create the connection
        try (Connection connection = dataSource.getConnection()) {

            // select player all data from the table according to player's uuid
            String selectPlayerData = "SELECT uuid,exp,level FROM sys_" + system + " WHERE uuid=?";
            try (PreparedStatement pstmt = connection.prepareStatement(selectPlayerData)) {
                pstmt.setString(1, p.getUniqueId().toString());
                try (ResultSet resultSet = pstmt.executeQuery()) {
                    if (!resultSet.next()) {
                        // Player has no record in this system
                        return null;
                    }
                    double expMultiplier = p.getPersistentDataContainer().getOrDefault(NamespacedKeys.generatePlayerExpMultiplierKey(system), PersistentDataType.DOUBLE,1.0);
                    return new LeveledPlayer(system,
                            resultSet.getString("uuid"),
                            resultSet.getDouble("exp"),
                            resultSet.getInt("level"),
                            expMultiplier);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void saveLeveledPlayer(LeveledPlayer lp) {
        // Capture the values now, the LeveledPlayer may change before the async task runs
        String system = lp.getSystemName();
        double exp = lp.getExp();
        int level = lp.getLevel();
        String uuid = lp.getUuid().toString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
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
        // Get connection
        try (Connection connection = dataSource.getConnection()) {
            // try and find a record for the player for any system.

            String getFirstRecordWithUUID = "SELECT EXISTS(SELECT 1 FROM sys_" + system + " WHERE uuid = ?)";

            try (PreparedStatement pstmt = connection.prepareStatement(getFirstRecordWithUUID)) {
                pstmt.setString(1, p.getUniqueId().toString());

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        // SQLite returns 1 for true, 0 for false
                        if (rs.getInt(1) == 1) {
                            return true;
                        }
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }


    public void removePlayer(Player p, String levelSystem) {
        // Get connection
        try (Connection connection = dataSource.getConnection()) {
            // try and find a record for the player for any system.

            String deleteRecord = "DELETE FROM sys_" + levelSystem + " WHERE uuid = ?";

            try (PreparedStatement pstmt = connection.prepareStatement(deleteRecord)) {
                pstmt.setString(1, p.getUniqueId().toString());

                pstmt.executeUpdate();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void closeHikariConnections() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}

