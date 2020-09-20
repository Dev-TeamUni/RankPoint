package net.teamuni.rankpoint;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.teamuni.rankpoint.data.PlayerDataManager;
import net.teamuni.rankpoint.data.PlayerDataManager.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class RPCommandExecutor implements TabExecutor {

    public static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{3,16}");

    private final Rankpoint instance;

    RPCommandExecutor(Rankpoint instance) {
        this.instance = instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean senderIsPlayer = sender instanceof Player;
        boolean senderHasPerm = sender.hasPermission("rankpoint.admin");
        Message msg = instance.getMessage();
        if (args.length >= 1) {
            switch (args[0]) {
                case "me":
                    if (senderIsPlayer) {
                        loadAndRun(((Player) sender).getUniqueId(),
                            data -> sender.sendMessage(msg.getMsg("command.me", data.getPoint())));
                    }
                    break;
                case "look":
                    if (args.length == 2 && checkPlayerName(args[1])) {
                        loadAndRun(args[1], (data, player) -> sender
                            .sendMessage(
                                msg.getMsg("command.look", data.getPoint(), player.getName())));
                        return true;
                    }
                    sender.sendMessage(msg.getMsg("command.help.look"));
                    break;
                case "give":
                    if (senderHasPerm) {
                        if (args.length == 3 && checkPlayerName(args[1]) && isIntegerAndPositive(args[2])) {
                            int point = Integer.parseInt(args[2]);
                            loadAndRun(args[1], (data, player) -> {
                                data.addPoint(point);
                                sender.sendMessage(
                                    msg.getMsg("command.give.sender", point, player.getName()));
                                if (player.isOnline()) {
                                    ((Player) player).sendMessage(
                                        msg.getMsg("command.give.receiver", point,
                                            sender.getName()));
                                }
                            });
                            return true;
                        }
                        sender.sendMessage(msg.getMsg("command.help.give"));
                    } else {
                        sender.sendMessage(msg.getMsg("command.donthaveperm"));
                    }
                    break;
                case "giveall":
                    if (senderHasPerm) {
                        if (args.length == 2 && isIntegerAndPositive(args[1])) {
                            int point = Integer.parseInt(args[1]);
                            Bukkit.getOnlinePlayers().forEach(
                                (player) -> loadAndRun(player.getUniqueId(),
                                    (data) -> data.addPoint(point)));
                            Bukkit.broadcastMessage(msg.getMsg("command.giveall", point));
                            return true;
                        }
                        sender.sendMessage(msg.getMsg("command.help.giveall"));
                    } else {
                        sender.sendMessage(msg.getMsg("command.donthaveperm"));
                    }
                    break;
                case "take":
                    if (senderHasPerm) {
                        if (args.length == 3 && checkPlayerName(args[1]) && isIntegerAndPositive(args[2])) {
                            int point = Integer.parseInt(args[2]);
                            loadAndRun(args[1], (data, player) -> {
                                if (data.getPoint() - point < 0) {
                                    data.setPoint(0);
                                } else {
                                    data.removePoint(point);
                                }
                                sender.sendMessage(
                                    msg.getMsg("command.take.sender", point, player.getName()));
                                if (player.isOnline()) {
                                    ((Player) player).sendMessage(
                                        msg.getMsg("command.take.receiver", point,
                                            sender.getName()));
                                }
                            });
                            return true;
                        }
                        sender.sendMessage(msg.getMsg("command.help.take"));
                    } else {
                        sender.sendMessage(msg.getMsg("command.donthaveperm"));
                    }
                    break;
                case "set":
                    if (senderHasPerm) {
                        if (args.length == 3 && checkPlayerName(args[1]) && isIntegerAndPositive(args[2])) {
                            int point = Integer.parseInt(args[2]);
                            loadAndRun(args[1], (data, player) -> {
                                data.setPoint(point);
                                sender.sendMessage(
                                    msg.getMsg("command.set.sender", point, player.getName()));
                                if (player.isOnline()) {
                                    ((Player) player).sendMessage(
                                        msg.getMsg("command.set.receiver", point,
                                            sender.getName()));
                                }
                            });
                            return true;
                        }
                        sender.sendMessage(msg.getMsg("command.help.set"));
                    } else {
                        sender.sendMessage(msg.getMsg("command.donthaveperm"));
                    }
                    break;
                case "reset":
                    if (senderHasPerm) {
                        if (args.length == 2 && checkPlayerName(args[1])) {
                            loadAndRun(args[1], (data, player) -> {
                                data.setPoint(0);
                                sender.sendMessage(msg.getMsg("command.reset", player.getName()));
                            });
                            return true;
                        }
                        sender.sendMessage(msg.getMsg("command.help.reset"));
                    } else {
                        sender.sendMessage(msg.getMsg("command.donthaveperm"));
                    }
                    break;
                case "reload":
                    if (senderHasPerm) {
                        // TODO Reload 추가
                    } else {
                        sender.sendMessage(msg.getMsg("command.donthaveperm"));
                    }
                    break;
                default:
                    sender.sendMessage(msg.getMsg("command.help.me"));
                    sender.sendMessage(msg.getMsg("command.help.look"));
                    if (senderHasPerm) {
                        sender.sendMessage(msg.getMsg("command.help.give"));
                        sender.sendMessage(msg.getMsg("command.help.giveall"));
                        sender.sendMessage(msg.getMsg("command.help.take"));
                        sender.sendMessage(msg.getMsg("command.help.set"));
                        sender.sendMessage(msg.getMsg("command.help.reset"));
                        sender.sendMessage(msg.getMsg("command.help.reload"));
                    }
                    break;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias,
        String[] args) {
        return null;
    }

    private boolean checkPlayerName(String name) {
        return USERNAME_PATTERN.matcher(name).matches();
    }

    private boolean isIntegerAndPositive(String st) {
        try {
            int i = Integer.parseInt(st);
            if (i > 0) {
                return true;
            }
        } catch (NumberFormatException ignored) {
        }
        return false;
    }

    private void loadAndRun(UUID uuid, Consumer<PlayerData> consumer) {
        PlayerDataManager dataManager = instance.getPlayerDataManager();
        if (dataManager.isLoaded(uuid)) {
            consumer.accept(dataManager.getPlayerData(uuid));
        } else {
            dataManager.loadPlayerData(uuid);
            consumer.accept(dataManager.getPlayerData(uuid));
            dataManager.unloadPlayerData(uuid);
        }
    }

    @SuppressWarnings("deprecation")
    private void loadAndRun(String name, BiConsumer<PlayerData, OfflinePlayer> consumer) {
        PlayerDataManager dataManager = instance.getPlayerDataManager();
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) {
            consumer.accept(dataManager.getPlayerData(p.getUniqueId()), p);
        } else {
            CompletableFuture.supplyAsync(() -> Bukkit.getOfflinePlayer(name)).thenAccept(
                player -> Bukkit.getScheduler().runTask(instance,
                    () -> consumer
                        .accept(dataManager.getPlayerData(player.getUniqueId()), player)));
        }
    }
}
