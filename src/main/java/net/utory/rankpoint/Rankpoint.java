package net.utory.rankpoint;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.milkbowl.vault.permission.Permission;
import net.utory.rankpoint.data.DatabaseManager;
import net.utory.rankpoint.data.MigrateManager;
import net.utory.rankpoint.data.PlayerDataManager;
import net.utory.rankpoint.data.PlayerDataManager.PlayerListener;
import net.utory.rankpoint.data.database.Database;
import net.utory.rankpoint.data.database.Mysql;
import net.utory.rankpoint.data.database.Sqlite;
import net.utory.rankpoint.placeholderapi.RankpointExpansion;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class Rankpoint extends JavaPlugin {

    private Permission perms;
    private PlayerDataManager playerDataManager;
    private DatabaseManager databaseManager;
    private Message message;
    private GroupConfig groupConfig;

    @Override
    public void onEnable() {
        if (!setupPermission()) {
            getLogger().severe("Vault-Permission 에 연결할 수 없습니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!setupConfig()) {
            getLogger().severe("Config 를 불러올 수 없습니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!setupDatabase()) {
            getLogger().severe("데이터베이스에 연결할 수 없습니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (setupPlaceholders()) {
            getLogger().info("Placeholder API와 연결되었습니다.");
        }
        PluginCommand command = getCommand("rankpoint");
        RPCommandExecutor executor = new RPCommandExecutor(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
    }

    public boolean configReload() {
        Bukkit.getScheduler().cancelTasks(this);
        playerDataManager.close();
        if (setupConfig() && setupDatabase()) {
            playerDataManager.allPlayerDataLoad(Bukkit.getOnlinePlayers());
            return true;
        }
        return false;
    }

    public void migrate() {
        Bukkit.getScheduler().cancelTasks(this);
        playerDataManager.close();
        reloadConfig();
        FileConfiguration cf = getConfig();
        boolean isSqlite = cf.getString("player-data.storage", "sqlite").equalsIgnoreCase("sqlite");

        String hostName = cf.getString("player-data.MySQL.hostname");
        int port = cf.getInt("player-data.MySQL.port");
        String databaseName = cf.getString("player-data.MySQL.database");
        String tableNameMySQL = cf.getString("player-data.MySQL.tablename");
        String parameters = cf.getString("player-data.MySQL.parameters");
        String userName = cf.getString("player-data.MySQL.username");
        String password = cf.getString("player-data.MySQL.password");

        DatabaseManager mysql;
        try {
            mysql = new DatabaseManager(new Mysql(hostName, port, databaseName, parameters, tableNameMySQL,
                userName, password));
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe("Mysql 데이터베이스에 연결을 실패했습니다.");
            if (!migrateFinish()) {
                getLogger().severe("데이터베이스를 리로드하는데 실패했습니다. 플러그인을 비활성화합니다.");
                Bukkit.getPluginManager().disablePlugin(this);
            }
            return;
        }

        String tableName = cf.getString("player-data.SQLite.tablename");
        File file = new File(cf.getString("player-data.SQLite.file"));

        DatabaseManager sqlite;
        try {
            sqlite = new DatabaseManager(new Sqlite(tableName, file));
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe("Sqlite 데이터베이스에 연결을 실패했습니다.");
            if (!migrateFinish()) {
                getLogger().severe("데이터베이스를 리로드하는데 실패했습니다. 플러그인을 비활성화합니다.");
                Bukkit.getPluginManager().disablePlugin(this);
            }
            return;
        }

        MigrateManager migrateManager;
        if (isSqlite) {
            migrateManager = new MigrateManager(mysql, sqlite);
        } else {
            migrateManager = new MigrateManager(sqlite, mysql);
        }
        migrateManager.migrate();
        cf.set("player-data.storage", isSqlite ? "mysql" : "sqlite");
        saveConfig();
        migrateFinish();
        getLogger().info("정상적으로 데이터베이스가 변경되었습니다.");
    }

    private boolean migrateFinish() {
        if (setupDatabase()) {
            playerDataManager.allPlayerDataLoad(Bukkit.getOnlinePlayers());
            return true;
        }
        return false;
    }

    private boolean setupPermission() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager()
            .getRegistration(Permission.class);
        if (rsp == null) {
            return false;
        }
        perms = rsp.getProvider();
        return true;
    }

    private boolean setupConfig() {
        saveDefaultConfig();
        reloadConfig();
        File msgConf = new File(getDataFolder(), "message.yml");
        if (!msgConf.exists()) {
            saveResource("message.yml", false);
        }
        message = new Message();
        message.loadMessages(YamlConfiguration.loadConfiguration(msgConf));
        ConfigurationSection groups = getConfig().getConfigurationSection("groups");
        if (groups == null) {
            getLogger().severe("config의 groups 설정을 불러오는데 실패했습니다.");
            return false;
        }
        List<String> allGroups = Arrays.asList(perms.getGroups());
        List<String> groupNames = new ArrayList<>();
        List<Integer> pointConditions = new ArrayList<>();
        Map<String, String> displayGroupNamesMap = new HashMap<>();
        // 람다에서는 final 변수만 접근 가능
        final int[] latest = {0};
        groups.getKeys(false).stream().mapToInt(Integer::parseInt).sorted()
            .mapToObj(String::valueOf).map(groups::getConfigurationSection).filter(Objects::nonNull)
            .forEach(section -> {
                String groupName = section.getString("group").toLowerCase();
                if (allGroups.contains(groupName)) {
                    groupNames.add(groupName);
                    displayGroupNamesMap.put(groupName, section.getString("group"));
                    latest[0] += section.getInt("point");
                    pointConditions.add(latest[0]);
                } else {
                    getLogger().severe(groupName + " 는(은) 없는 그룹입니다.");
                }
            });
        if (groupNames.isEmpty() || pointConditions.isEmpty()) {
            return false;
        }
        groupConfig = new GroupConfig(this, groupNames, pointConditions, displayGroupNamesMap);
        return true;
    }

    private boolean setupDatabase() {
        FileConfiguration cf = getConfig();
        String storageType = cf.getString("player-data.storage");
        long saveInterval = cf.getLong("player-data.save-interval");
        switch (storageType.toLowerCase()) {
            case "mysql":
                String hostName = cf.getString("player-data.MySQL.hostname");
                int port = cf.getInt("player-data.MySQL.port");
                String databaseName = cf.getString("player-data.MySQL.database");
                String tableNameMySQL = cf.getString("player-data.MySQL.tablename");
                String parameters = cf.getString("player-data.MySQL.parameters");
                String userName = cf.getString("player-data.MySQL.username");
                String password = cf.getString("player-data.MySQL.password");
                try {
                    databaseManager = new DatabaseManager(
                        new Mysql(hostName, port, databaseName, parameters, tableNameMySQL,
                            userName, password));
                } catch (SQLException e) {
                    e.printStackTrace();
                    return false;
                }
                break;
            case "sqlite":
            default:
                String tableName = cf.getString("player-data.SQLite.tablename");
                File file = new File(cf.getString("player-data.SQLite.file"));
                try {
                    databaseManager = new DatabaseManager(new Sqlite(tableName, file));
                } catch (SQLException e) {
                    e.printStackTrace();
                    return false;
                }
                break;
        }
        playerDataManager = new PlayerDataManager(this);
        Bukkit.getScheduler()
            .runTaskTimer(this, playerDataManager::saveAllData, saveInterval * 20,
                saveInterval * 20);
        return true;
    }

    private boolean setupPlaceholders() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return new RankpointExpansion(this).register();
        }
        return false;
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (playerDataManager != null)
            playerDataManager.close();
    }

    public Permission getPermission() {
        return perms;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public Message getMessage() {
        return message;
    }

    public GroupConfig getGroupConfig() {
        return groupConfig;
    }
}
