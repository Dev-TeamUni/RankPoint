package net.utory.rankpoint.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Consumer;
import net.utory.rankpoint.GroupConfig;
import net.utory.rankpoint.Message;
import net.utory.rankpoint.Rankpoint;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerDataManager {

    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();
    private final Map<UUID, List<Consumer<PlayerData>>> loadingTask = new HashMap<>();
    private final Rankpoint instance;

    public PlayerDataManager(Rankpoint instance) {
        this.instance = instance;
    }

    private void loadPlayerData(UUID uuid) {
        if (playerDataMap.containsKey(uuid) || loadingTask.containsKey(uuid)) {
            return;
        }

        DatabaseManager databaseManager = instance.getDatabaseManager();

        loadingTask.put(uuid, new ArrayList<>());
        databaseManager.loadPoint(uuid, (i) ->
            Bukkit.getScheduler().runTask(instance, () -> {
                PlayerData pd = new PlayerData(uuid, i);
                playerDataMap.put(uuid, pd);
                for (Consumer<PlayerData> consumer : loadingTask.remove(uuid)) {
                    consumer.accept(pd);
                }
            }));
    }

    public void usePlayerData(UUID uuid, Consumer<PlayerData> consumer) {
        if (playerDataMap.containsKey(uuid)) {
            consumer.accept(playerDataMap.get(uuid));
            return;
        }
        if (loadingTask.containsKey(uuid)) {
            loadingTask.get(uuid).add(consumer);
            return;
        }
        loadPlayerData(uuid);
        if (loadingTask.containsKey(uuid)) {
            loadingTask.get(uuid).add(consumer);
        } else if (playerDataMap.containsKey(uuid)) {
            consumer.accept(playerDataMap.get(uuid));
        } else {
            throw new RuntimeException("Faild to load player point.");
        }
    }

    public PlayerData getPlayerDataOrNull(UUID uuid) {
        return playerDataMap.getOrDefault(uuid, null);
    }

    public void saveAllData() {
        if (playerDataMap.isEmpty()) {
            return;
        }
        Map<UUID, Integer> points = new HashMap<>();

        for (Entry<UUID, PlayerData> entry : new HashMap<>(playerDataMap).entrySet()) {
            if (entry.getValue().isChanged) {
                points.put(entry.getKey(), entry.getValue().point);
            }
        }

        if (!points.isEmpty()) {
            DatabaseManager databaseManager = instance.getDatabaseManager();
            databaseManager.savePoint(points);
        }
    }

    public void close() {
        unloadAllData();
        DatabaseManager databaseManager = instance.getDatabaseManager();
        databaseManager.closeDatabase();
    }

    public void allPlayerDataLoad(Collection<? extends Player> players) {
        for (Player player : players) {
            loadPlayerData(player.getUniqueId());
        }
    }

    private void unloadAllData() {
        if (playerDataMap.isEmpty()) {
            return;
        }

        Map<UUID, Integer> willSaveData = new HashMap<>();
        for (Map.Entry<UUID, PlayerData> entry : playerDataMap.entrySet()) {
            if (entry.getValue().isChanged) {
                willSaveData.put(entry.getKey(), entry.getValue().point);
            }
        }
        playerDataMap.clear();
        if (willSaveData.isEmpty()) {
            return;
        }

        DatabaseManager databaseManager = instance.getDatabaseManager();
        databaseManager.savePoint(willSaveData);
    }

    public static final class PlayerData {

        private static final Rankpoint instance = Rankpoint.getPlugin(Rankpoint.class);

        private final UUID uuid;
        private int point;
        private int group = -1;
        private String displayGroupName;
        private int prettyPoint;
        private String totalPoint;
        private String needPoint;
        private boolean isChanged = false;

        private PlayerData(UUID uuid, int point) {
            this.uuid = uuid;
            this.point = point;
            update();
        }

        public int getPoint() {
            return point;
        }

        public void setPoint(int point) {
            if (point < 0) {
                throw new IllegalArgumentException(
                    "point cannot be less than 0 (point: " + point + ")");
            }
            this.point = point;
            isChanged = true;
            update();
        }

        public void addPoint(int point) {
            if (point < 0) {
                throw new IllegalArgumentException(
                    "point cannot be less than 0 (point: " + point + ")");
            }
            try {
                this.point = Math.addExact(this.point, point);
            } catch (ArithmeticException ignored) { }
            isChanged = true;
            update();
        }

        public void removePoint(int point) {
            if (this.point - point < 0) {
                throw new IllegalArgumentException(
                    "point cannot be less than 0 (point: " + (this.point - point) + ")");
            }
            this.point -= point;
            isChanged = true;
            update();
        }

        private void update() {
            GroupConfig groupConfig = instance.getGroupConfig();
            Message message = instance.getMessage();

            int oldGroup = group;
            group = groupConfig.findGroup(point);
            displayGroupName = groupConfig.getGroupName(group);

            prettyPoint = groupConfig.getPrretyPoint(point);
            int tmp = groupConfig.getTotalPoint(group);
            totalPoint = tmp == -1 ? message.RankMaxTotalPoint() : tmp + "";
            tmp = groupConfig.getNeedPoint(group, point);
            needPoint = tmp == -1 ? message.RankMaxNeedPoint() : tmp + "";

            if (group != -1 && oldGroup != group) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    groupConfig.updatePlayerRank(p, this);
                }
            }
        }

        public int getPrettyPoint() {
            return prettyPoint;
        }

        public String getTotalPoint() {
            return totalPoint;
        }

        public String getNeedPoint() {
            return needPoint;
        }

        public int getGroupInt() {
            return group;
        }

        public String getGroup() {
            return displayGroupName;
        }
    }

    public static final class PlayerListener implements Listener {

        private final Rankpoint instance;

        public PlayerListener(Rankpoint instance) {
            this.instance = instance;
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            PlayerDataManager playerDataManager = instance.getPlayerDataManager();
            playerDataManager.loadPlayerData(event.getPlayer().getUniqueId());
        }
    }
}
