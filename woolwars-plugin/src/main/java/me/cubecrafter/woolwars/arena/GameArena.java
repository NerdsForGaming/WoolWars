package me.cubecrafter.woolwars.arena;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import lombok.Setter;
import me.cubecrafter.woolwars.WoolWars;
import me.cubecrafter.woolwars.api.arena.Arena;
import me.cubecrafter.woolwars.api.arena.Cuboid;
import me.cubecrafter.woolwars.api.arena.GameState;
import me.cubecrafter.woolwars.api.events.arena.GameStateChangeEvent;
import me.cubecrafter.woolwars.api.events.player.PlayerJoinArenaEvent;
import me.cubecrafter.woolwars.api.events.player.PlayerLeaveArenaEvent;
import me.cubecrafter.woolwars.api.nms.NMS;
import me.cubecrafter.woolwars.api.powerup.PowerUp;
import me.cubecrafter.woolwars.api.team.Team;
import me.cubecrafter.woolwars.api.team.TeamColor;
import me.cubecrafter.woolwars.arena.setup.SetupSession;
import me.cubecrafter.woolwars.config.Configuration;
import me.cubecrafter.woolwars.config.Messages;
import me.cubecrafter.woolwars.powerup.GamePowerUp;
import me.cubecrafter.woolwars.tasks.*;
import me.cubecrafter.woolwars.team.GameTeam;
import me.cubecrafter.woolwars.utils.ArenaUtil;
import me.cubecrafter.woolwars.utils.ItemBuilder;
import me.cubecrafter.woolwars.utils.PlayerScoreboard;
import me.cubecrafter.woolwars.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public class GameArena implements Arena {

    private final YamlConfiguration config;
    private final String id;
    private final String displayName;
    private final String group;
    private final Location lobby;
    private final int maxPlayersPerTeam;
    private final int minPlayers;
    private final int winPoints;
    private final List<Player> players = new ArrayList<>();
    private final List<Player> deadPlayers = new ArrayList<>();
    private final List<Block> placedBlocks = new ArrayList<>();
    private final List<Team> teams = new ArrayList<>();
    private final List<PowerUp> powerUps = new ArrayList<>();
    private final Map<Player, Integer> kills = new HashMap<>();
    private final Map<Player, Integer> deaths = new HashMap<>();
    private final Map<Player, Integer> woolPlaced = new HashMap<>();
    private final Map<Player, Integer> blocksBroken = new HashMap<>();
    private final Cuboid center;
    private final Cuboid arenaRegion;
    private StartingTask startingTask;
    private RoundTask roundTask;
    private PreRoundTask preRoundTask;
    private RoundOverTask roundOverTask;
    private GameEndedTask gameEndedTask;
    private GameState gameState = GameState.WAITING;
    @Setter private int round = 0;
    @Setter private int timer = 0;
    @Setter private boolean centerLocked = false;

    public GameArena(String id, YamlConfiguration arenaConfig) {
        this.id = id;
        this.config = arenaConfig;
        group = arenaConfig.getString("group");
        lobby = TextUtil.deserializeLocation(arenaConfig.getString("lobby-location"));
        displayName = TextUtil.color(arenaConfig.getString("displayname"));
        maxPlayersPerTeam = arenaConfig.getInt("max-players-per-team");
        minPlayers = arenaConfig.getInt("min-players");
        winPoints = arenaConfig.getInt("win-points");
        for (String key : arenaConfig.getConfigurationSection("teams").getKeys(false)) {
            TeamColor color = TeamColor.valueOf(key.toUpperCase());
            String name = arenaConfig.getString("teams." + key + ".name");
            Location spawn = TextUtil.deserializeLocation(arenaConfig.getString("teams." + key + ".spawn-location"));
            Location barrier1 = TextUtil.deserializeLocation(arenaConfig.getString("teams." + key + ".barrier.pos1"));
            Location barrier2 = TextUtil.deserializeLocation(arenaConfig.getString("teams." + key + ".barrier.pos2"));
            Location base1 = TextUtil.deserializeLocation(arenaConfig.getString("teams." + key + ".base.pos1"));
            Location base2 = TextUtil.deserializeLocation(arenaConfig.getString("teams." + key + ".base.pos2"));
            GameTeam team = new GameTeam(name, this, spawn, color, new Cuboid(barrier1, barrier2), new Cuboid(base1, base2));
            teams.add(team);
        }
        Location point1 = TextUtil.deserializeLocation(arenaConfig.getString("center.pos1"));
        Location point2 = TextUtil.deserializeLocation(arenaConfig.getString("center.pos2"));
        center = new Cuboid(point1, point2);
        Location point3 = TextUtil.deserializeLocation(arenaConfig.getString("arena.pos1"));
        Location point4 = TextUtil.deserializeLocation(arenaConfig.getString("arena.pos2"));
        arenaRegion = new Cuboid(point3, point4);
        for (String line : arenaConfig.getStringList("powerups")) {
            Location location = TextUtil.deserializeLocation(line);
            GamePowerUp powerUp = new GamePowerUp(location, this);
            powerUps.add(powerUp);
        }
        killEntities();
    }

    @Override
    public void addPlayer(Player player) {
        PlayerJoinArenaEvent event = new PlayerJoinArenaEvent(player, this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        if (SetupSession.isActive(player)) {
            TextUtil.sendMessage(player, "{prefix}&cYou can't join an arena while you are in setup mode!");
            return;
        }
        if (players.contains(player)) {
            TextUtil.sendMessage(player, Messages.ALREADY_IN_ARENA.getAsString());
            return;
        }
        if (!gameState.equals(GameState.WAITING) && !gameState.equals(GameState.STARTING)) {
            TextUtil.sendMessage(player, Messages.GAME_ALREADY_STARTED.getAsString());
            return;
        }
        if (players.size() >= maxPlayersPerTeam * teams.size()) {
            TextUtil.sendMessage(player, Messages.ARENA_FULL.getAsString());
            return;
        }
        players.add(player);
        player.teleport(lobby);
        player.setGameMode(GameMode.ADVENTURE);
        player.setFoodLevel(20);
        player.setHealth(20);
        player.getInventory().setArmorContents(null);
        player.getInventory().clear();
        player.setFlying(false);
        player.setAllowFlight(false);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        NMS nms = WoolWars.getInstance().getNms();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (players.contains(online)) {
                nms.showPlayer(player, online);
                nms.showPlayer(online, player);
            } else {
                nms.hidePlayer(player, online);
                nms.hidePlayer(online, player);
            }
        }
        ItemStack leaveItem = ItemBuilder.fromConfig(Configuration.LEAVE_ITEM.getAsConfigSection()).setTag("leave-item").build();
        player.getInventory().setItem(Configuration.LEAVE_ITEM.getAsConfigSection().getInt("slot"), leaveItem);
        TextUtil.sendMessage(players, Messages.PLAYER_JOIN_ARENA.getAsString()
                .replace("{player}", player.getName())
                .replace("{current}", String.valueOf(players.size()))
                .replace("{max}", String.valueOf(getTeams().size() * maxPlayersPerTeam)));
        ArenaUtil.playSound(players, Configuration.SOUNDS_PLAYER_JOINED.getAsString());
        if (gameState.equals(GameState.WAITING) && getPlayers().size() >= getMinPlayers()) {
            setGameState(GameState.STARTING);
        }
    }

    @Override
    public void forceStart() {
        if (gameState.equals(GameState.WAITING)) setGameState(GameState.STARTING);
    }

    @Override
    public void removePlayer(Player player, boolean teleportToLobby) {
        PlayerLeaveArenaEvent event = new PlayerLeaveArenaEvent(player, this);
        Bukkit.getPluginManager().callEvent(event);
        players.remove(player);
        GameTeam playerTeam = (GameTeam) getTeamByPlayer(player);
        if (playerTeam != null) {
            PlayerScoreboard scoreboard = PlayerScoreboard.getScoreboard(player);
            if (scoreboard != null) {
                scoreboard.removeGamePrefix(playerTeam);
            }
            playerTeam.removeMember(player);
        }
        player.getInventory().setArmorContents(null);
        player.getInventory().clear();
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setFoodLevel(20);
        player.setHealth(20);
        player.setGameMode(GameMode.SURVIVAL);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        if (teleportToLobby) ArenaUtil.teleportToLobby(player);
        NMS nms = WoolWars.getInstance().getNms();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (players.contains(online)) {
                nms.hidePlayer(player, online);
                nms.hidePlayer(online, player);
            } else {
                nms.showPlayer(player, online);
                nms.showPlayer(online, player);
            }
        }
        TextUtil.sendMessage(players, Messages.PLAYER_LEAVE_ARENA.getAsString()
                .replace("{player}", player.getDisplayName())
                .replace("{current}", String.valueOf(players.size()))
                .replace("{max}", String.valueOf(getTeams().size()*getMaxPlayersPerTeam())));
        if (gameState.equals(GameState.WAITING) || gameState.equals(GameState.STARTING)) {
            ArenaUtil.playSound(players, Configuration.SOUNDS_PLAYER_LEFT.getAsString());
        }
        if (gameState.equals(GameState.STARTING) && getPlayers().size() < getMinPlayers()) {
            cancelTasks();
            TextUtil.sendMessage(players, Messages.START_CANCELLED.getAsString());
            setGameState(GameState.WAITING);
        }
        if (!gameState.equals(GameState.WAITING) && !gameState.equals(GameState.STARTING) && !gameState.equals(GameState.GAME_ENDED) && teams.stream().filter(team -> team.getMembers().size() == 0).count() > teams.size() - 2) {
            cancelTasks();
            setGameState(GameState.GAME_ENDED);
        }
    }

    public void removeAllPlayers() {
        for (Player player : players) {
            PlayerLeaveArenaEvent event = new PlayerLeaveArenaEvent(player, this);
            Bukkit.getPluginManager().callEvent(event);
            player.getInventory().setArmorContents(null);
            player.getInventory().clear();
            player.setFlying(false);
            player.setAllowFlight(false);
            player.setFoodLevel(20);
            player.setHealth(20);
            player.setGameMode(GameMode.SURVIVAL);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            NMS nms = WoolWars.getInstance().getNms();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (players.contains(online)) {
                    nms.hidePlayer(player, online);
                    nms.hidePlayer(online, player);
                } else {
                    nms.showPlayer(player, online);
                    nms.showPlayer(online, player);
                }
            }
            ArenaUtil.teleportToLobby(player);
        }
        players.clear();
    }

    @Override
    public void setGameState(GameState gameState) {
        GameStateChangeEvent event = new GameStateChangeEvent(this, this.gameState, gameState);
        Bukkit.getServer().getPluginManager().callEvent(event);
        this.gameState = gameState;
        switch (gameState) {
            case WAITING:
                cancelTasks();
                break;
            case STARTING:
                startingTask = new StartingTask(this);
                break;
            case PRE_ROUND:
                preRoundTask = new PreRoundTask(this);
                break;
            case ACTIVE_ROUND:
                roundTask = new RoundTask(this);
                break;
            case ROUND_OVER:
                roundOverTask = new RoundOverTask(this);
                break;
            case GAME_ENDED:
                gameEndedTask = new GameEndedTask(this);
                break;
        }
    }

    @Override
    public void restart() {
        cancelTasks();
        removeAllPlayers();
        getTeams().forEach(Team::reset);
        removePlacedBlocks();
        fillCenter();
        powerUps.forEach(PowerUp::remove);
        setRound(0);
        setTimer(0);
        killEntities();
        deadPlayers.clear();
        kills.clear();
        deaths.clear();
        woolPlaced.clear();
        blocksBroken.clear();
        setGameState(GameState.WAITING);
    }

    public void assignTeams() {
        for (Player player : players) {
            Team minPlayers = getTeams().stream().min(Comparator.comparing(team -> team.getMembers().size())).orElse(teams.get(new Random().nextInt(teams.size())));
            minPlayers.addMember(player);
        }
    }

    @Override
    public List<Player> getAlivePlayers() {
        return players.stream().filter(player -> !deadPlayers.contains(player)).collect(Collectors.toList());
    }

    @Override
    public Team getTeamByName(String name) {
        return teams.stream().filter(team -> team.getName().equals(name)).findAny().orElse(null);
    }

    @Override
    public Team getTeamByPlayer(Player player) {
        return teams.stream().filter(team -> team.getMembers().contains(player)).findAny().orElse(null);
    }

    @Override
    public boolean isTeammate(Player player, Player other) {
        return getTeamByPlayer(player).getMembers().contains(other);
    }

    @Override
    public boolean isDead(Player player) {
        return deadPlayers.contains(player);
    }

    @Override
    public boolean isAlive(Player player) {
        return !deadPlayers.contains(player);
    }

    @Override
    public String getTimerFormatted() {
        int minutes = (timer / 60) % 60;
        int seconds = (timer) % 60;
        return (minutes > 9 ? minutes : "0" + minutes) + ":" + (seconds > 9 ? seconds : "0" + seconds);
    }

    public void killEntities() {
        for (Entity entity : arenaRegion.getWorld().getEntities()) {
            if (entity.getType().equals(EntityType.ITEM_FRAME) || entity.getType().equals(EntityType.ARMOR_STAND) || entity.getType().equals(EntityType.PAINTING) || entity.getType().equals(EntityType.PLAYER)) continue;
            if (arenaRegion.isInside(entity.getLocation())) entity.remove();
        }
    }

    @Override
    public void removePlacedBlocks() {
        placedBlocks.forEach(block -> block.setType(Material.AIR));
        placedBlocks.clear();
    }

    @Override
    public void fillCenter() {
        List<String> defaultBlocks = new ArrayList<>(Arrays.asList("QUARTZ_BLOCK", "SNOW_BLOCK", "WHITE_WOOL"));
        Random random = new Random();
        center.getBlocks().forEach(block -> block.setType(XMaterial.matchXMaterial(defaultBlocks.get(random.nextInt(defaultBlocks.size()))).get().parseMaterial()));
    }

    @Override
    public String getPointsFormatted() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            if (i > 0) {
                builder.append(" &7- ");
            }
            builder.append(team.getTeamColor().getChatColor()).append(team.getPoints());
        }
        return TextUtil.color(builder.toString());
    }

    @Override
    public int getKills(Player player) {
        return 0;
    }

    @Override
    public int getDeaths(Player player) {
        return 0;
    }

    @Override
    public int getWoolPlaced(Player player) {
        return 0;
    }

    @Override
    public int getBlocksBroken(Player player) {
        return 0;
    }

    @Override
    public void addKills(Player player, int number) {
        kills.merge(player, number, Integer::sum);
    }

    @Override
    public void addDeaths(Player player, int number) {
        deaths.merge(player, number, Integer::sum);
    }

    @Override
    public void addWoolPlaced(Player player, int number) {
        woolPlaced.merge(player, number, Integer::sum);
    }

    @Override
    public void addBlocksBroken(Player player, int number) {
        blocksBroken.merge(player, number, Integer::sum);
    }

    @Override
    public void cancelTasks() {
        if (startingTask != null) startingTask.cancel();
        if (preRoundTask != null) preRoundTask.cancel();
        if (roundTask != null) roundTask.cancel();
        if (roundOverTask != null) roundOverTask.cancel();
        if (gameEndedTask != null) gameEndedTask.cancel();
    }

}
