package us.to.midensthings.serverLevels.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

import javax.sql.DataSource;
import java.sql.*;

public class DatabaseManager {

    String dataFormat;
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    YamlConfiguration levelSystemsConf = plugin.getLevelSystemsConf();
    DataSource dataSource;



    public void setupConnectionPool() {
        HikariConfig hkconfig = new HikariConfig();
        switch (dataFormat) {
            case ("mysql"):
                hkconfig.setJdbcUrl("jdbc:mysql://"+plugin.getConfig().getString("mysql.host")+"/"+plugin.getConfig().getString("mysql.database")); // Address of your running MySQL database
                hkconfig.setUsername(plugin.getConfig().getString("mysql.username")); // Username
                hkconfig.setPassword(plugin.getConfig().getString("mysql.password")); // Password
                hkconfig.setMaximumPoolSize(10); // Pool size defaults to 10
                dataSource = new HikariDataSource(hkconfig);

                break;
            case ("sqlite"):
                hkconfig.setDriverClassName("org.sqlite.JDBC");
                hkconfig.setJdbcUrl("jdbc:sqlite:plugins/ServerLevels/database.db");
                hkconfig.setMaximumPoolSize(10);
                dataSource = new HikariDataSource(hkconfig);
                break;
        }
    }

    public void setupDatabase() {
        /*
        1. Check for what database system the plugin is currently using
        2. Initialize database and database manager accoring to the proper system
            - create Table for every level system
         */
        dataFormat = plugin.getConfig().getString("data-format");

        setupConnectionPool();

        // Try to set up database not on main thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Create the connection
            try (Connection connection = dataSource.getConnection()) {
                // Generate a table for every level system.
                levelSystemsConf.getConfigurationSection("").getKeys(false).forEach(system -> {

                    // Each table holds a player's uuid, their current exp, and their current level in its records.
                    String createTable = "CREATE TABLE IF NOT EXISTS sys_"+system+" ( "
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




        });


    }

    public void initializePlayer(Player p) {

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    // Create the connection
                    try (Connection connection = dataSource.getConnection()) {
                        // Generate default player stats for each level system.
                        levelSystemsConf.getConfigurationSection("").getKeys(false).forEach(system -> {

                            String createDefaultStats = "INSERT INTO sys_" + system + " (uuid, exp, level) "
                                    + "VALUES(?, '0.0', '0')";
                            try (PreparedStatement pstmt = connection.prepareStatement(createDefaultStats)) {
                                pstmt.setString(1, p.getUniqueId().toString());
                                pstmt.execute();

                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                        });
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
        });

    }


    /***
     Get the player's stats from the database.
     Returns null if no data was found.
     */
    public LeveledPlayer getLeveledPlayer(Player p, String system) {
        LeveledPlayer lp = null;
        // get connection
        // Create the connection
        try (Connection connection = dataSource.getConnection()) {

            // select player all data from the table according to player's uuid
            String selectPlayerData = "SELECT uuid,exp,level FROM sys_"+system+" WHERE uuid=?";
            try(PreparedStatement pstmt = connection.prepareStatement(selectPlayerData)) {
                pstmt.setString(1, p.getUniqueId().toString());
                try (ResultSet resultSet = pstmt.executeQuery()) {
                    resultSet.next();
                    lp = new LeveledPlayer(system,
                                resultSet.getString("uuid"),
                                resultSet.getDouble("exp"),
                                resultSet.getInt("level"));



                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

    return lp;
    }

    public void saveLeveledPlayer(LeveledPlayer lp) {


        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = dataSource.getConnection()) {
                String system = lp.getSystemName();

                // select player all data from the table according to player's uuid
                String selectTestValues = "UPDATE sys_"+system+" SET exp=?, level=? WHERE uuid=?";
                try(PreparedStatement pstmt = connection.prepareStatement(selectTestValues)) {
                    pstmt.setDouble(1,lp.getExp());
                    pstmt.setInt(2, lp.getLevel());
                    pstmt.setString(3, lp.getUuid().toString());
                    pstmt.executeUpdate();
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

    }

    public boolean playerHasRecord(Player p) {
        // Get connection
        try (Connection connection = dataSource.getConnection()) {
            // try and find a record for the player for any system.
            for (String system : levelSystemsConf.getConfigurationSection("").getKeys(false)) {
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

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }



}
