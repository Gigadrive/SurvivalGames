package eu.thechest.survivalgames;

import com.gmail.filoghost.holographicdisplays.api.Hologram;
import com.gmail.filoghost.holographicdisplays.api.HologramsAPI;
import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.game.GameManager;
import eu.thechest.chestapi.maps.*;
import eu.thechest.chestapi.maps.Map;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.server.GameState;
import eu.thechest.chestapi.server.GameType;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.server.ServerUtil;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.user.Rank;
import eu.thechest.chestapi.util.BountifulAPI;
import eu.thechest.chestapi.util.ParticleEffect;
import eu.thechest.chestapi.util.StringUtils;
import eu.thechest.survivalgames.cmd.MainExecutor;
import eu.thechest.survivalgames.listener.MainListener;
import eu.thechest.survivalgames.user.SurvivalUser;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import sun.dc.pr.PRError;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * Created by zeryt on 27.02.2017.
 */
public class SurvivalGames extends JavaPlugin {
    public static ArrayList<Map> VOTING_MAPS = new ArrayList<Map>();
    public static Location lobbyLocation;
    private static SurvivalGames instance;

    public static ArrayList<Player> PARTICIPANTS = new ArrayList<Player>();
    public static ArrayList<Player> SPECTATORS = new ArrayList<Player>();

    public ArrayList<Material> DISALLOWED_BLOCKS = new ArrayList<Material>();

    public static ArrayList<MapVote> VOTES = new ArrayList<MapVote>();

    public static SurvivalGamesPhase CURRENT_PHASE = SurvivalGamesPhase.VOTE_MAP;
    public static Map FINAL_MAP;
    public static String MAP_WORLDNAME;
    public static Map SHOWDOWN_MAP;
    public static String SHOWDOWN_MAP_WORLDNAME;

    public static int lobbyCountdownCount = 60;
    public static BukkitTask lobbyCountdownTask;

    public static int warmupCountdownCount = 30;
    public static BukkitTask warmupCountdownTask;

    public static int gracePeriodCount = 20;
    public static BukkitTask gracePeriodTask = null;

    public static boolean gracePeriod = true;

    public static boolean VOTING_OPEN = true;
    public static boolean SHOWDOWN_ENABLED = true;
    public static boolean MAY_START_SHOWDOWN = true;
    public static int SHOWDOWN_TASK_COUNT = 60;
    public static BukkitTask SHOWDOWN_TASK;
    public static int SHOWDOWN_MIN_PLAYERS = 2;

    public static int MIN_PLAYERS = 4;

    public static Player FIRST_BLOOD = null;

    public void onEnable(){
        ServerSettingsManager.updateGameState(GameState.LOBBY);
        ServerSettingsManager.setMaxPlayers(24);
        ServerSettingsManager.ENABLE_CHAT = true;
        ServerSettingsManager.UPDATE_TAB_NAME_WITH_SCOREBOARD = true;
        ServerSettingsManager.VIP_JOIN = true;
        ServerSettingsManager.AUTO_OP = true;
        ServerSettingsManager.PROTECT_ITEM_FRAMES = true;
        ServerSettingsManager.RUNNING_GAME = GameType.SURVIVAL_GAMES;
        ServerSettingsManager.SHOW_FAME_TITLE_ABOVE_HEAD = false;
        ServerSettingsManager.MIN_PLAYERS = MIN_PLAYERS;
        ServerSettingsManager.MAP_VOTING = true;
        ServerUtil.updateMapName("Voting..");

        DISALLOWED_BLOCKS.add(Material.BREWING_STAND);
        DISALLOWED_BLOCKS.add(Material.FURNACE);
        DISALLOWED_BLOCKS.add(Material.BURNING_FURNACE);
        DISALLOWED_BLOCKS.add(Material.WORKBENCH);
        DISALLOWED_BLOCKS.add(Material.TRAP_DOOR);
        DISALLOWED_BLOCKS.add(Material.CHEST);
        DISALLOWED_BLOCKS.add(Material.TRAPPED_CHEST);
        DISALLOWED_BLOCKS.add(Material.FENCE_GATE);
        DISALLOWED_BLOCKS.add(Material.SPRUCE_FENCE_GATE);
        DISALLOWED_BLOCKS.add(Material.BIRCH_FENCE_GATE);
        DISALLOWED_BLOCKS.add(Material.JUNGLE_FENCE_GATE);
        DISALLOWED_BLOCKS.add(Material.DARK_OAK_FENCE_GATE);
        DISALLOWED_BLOCKS.add(Material.ACACIA_FENCE_GATE);
        DISALLOWED_BLOCKS.add(Material.DIODE_BLOCK_OFF);
        DISALLOWED_BLOCKS.add(Material.DIODE_BLOCK_ON);
        DISALLOWED_BLOCKS.add(Material.REDSTONE_COMPARATOR_OFF);
        DISALLOWED_BLOCKS.add(Material.REDSTONE_COMPARATOR_ON);
        DISALLOWED_BLOCKS.add(Material.HOPPER);
        DISALLOWED_BLOCKS.add(Material.DROPPER);
        DISALLOWED_BLOCKS.add(Material.DISPENSER);
        DISALLOWED_BLOCKS.add(Material.BED_BLOCK);
        DISALLOWED_BLOCKS.add(Material.BEACON);
        DISALLOWED_BLOCKS.add(Material.ANVIL);
        DISALLOWED_BLOCKS.add(Material.ENCHANTMENT_TABLE);
        //DISALLOWED_BLOCKS.add(Material.STONE_BUTTON);
        //DISALLOWED_BLOCKS.add(Material.WOOD_BUTTON);
        DISALLOWED_BLOCKS.add(Material.JUKEBOX);
        DISALLOWED_BLOCKS.add(Material.NOTE_BLOCK);
        DISALLOWED_BLOCKS.add(Material.LEVER);

        chooseMapsForVoting();

        MainExecutor exec = new MainExecutor();

        Bukkit.getPluginManager().registerEvents(new MainListener(), this);

        lobbyLocation = new Location(Bukkit.getWorld(getConfig().getString("lobbyLocation.world")), getConfig().getDouble("lobbyLocation.x"), getConfig().getDouble("lobbyLocation.y"), getConfig().getDouble("lobbyLocation.z"), getConfig().getInt("lobbyLocation.yaw"), getConfig().getInt("lobbyLocation.pitch"));

        instance = this;
    }

    public static SurvivalGames getInstance(){
        return instance;
    }

    public void startShowdown(){
        if(SHOWDOWN_MIN_PLAYERS > PARTICIPANTS.size()) return;
        if(MAY_START_SHOWDOWN && SHOWDOWN_ENABLED){
            SHOWDOWN_TASK = new BukkitRunnable(){
                @Override
                public void run() {
                    if(SHOWDOWN_TASK_COUNT == 0){
                        cancel();
                        SHOWDOWN_TASK = null;
                        CURRENT_PHASE = SurvivalGamesPhase.SHOWDOWN;
                        MapRatingManager.MAP_TO_RATE = SHOWDOWN_MAP;

                        Location spectatorLoc = SHOWDOWN_MAP.getSpawnpoints().get(0).toBukkitLocation(SHOWDOWN_MAP_WORLDNAME);

                        ArrayList<MapLocationData> spawnpoints = SHOWDOWN_MAP.getSpawnpoints();
                        int i = 0;
                        for(Player p : Bukkit.getOnlinePlayers()){
                            if(i >= spawnpoints.size()) i = 0;

                            if(PARTICIPANTS.contains(p)){
                                p.teleport(spawnpoints.get(i).toBukkitLocation(SurvivalGames.SHOWDOWN_MAP_WORLDNAME));
                                spawnpoints.remove(spawnpoints.get(i));
                                p.setGameMode(GameMode.SURVIVAL);
                                SurvivalUser.get(p).addShowdowns(1);

                                i++;
                            } else if(SPECTATORS.contains(p)){
                                p.teleport(spectatorLoc);
                                p.setGameMode(GameMode.SPECTATOR);
                            }
                        }

                        for(Player all : Bukkit.getOnlinePlayers()){
                            ChestUser a = ChestUser.getUser(all);

                            SHOWDOWN_MAP.sendMapCredits(all);
                            SHOWDOWN_MAP.sendRateMapInfo(all);
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.DARK_RED.toString() + ChatColor.BOLD + a.getTranslatedMessage("The final showdown has started!"));
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + a.getTranslatedMessage("Teams are no longer allowed!"));

                            BountifulAPI.sendTitle(all,0,5*20,0,ChatColor.DARK_RED.toString() + ChatColor.BOLD.toString() + a.getTranslatedMessage("Showdown"),ChatColor.RED + a.getTranslatedMessage("Teams are no longer allowed!"));

                        }
                    } else {
                        if(SHOWDOWN_TASK_COUNT == 60 || SHOWDOWN_TASK_COUNT == 30 || SHOWDOWN_TASK_COUNT == 20 || SHOWDOWN_TASK_COUNT == 10 || SHOWDOWN_TASK_COUNT == 5 || SHOWDOWN_TASK_COUNT == 4 || SHOWDOWN_TASK_COUNT == 3 || SHOWDOWN_TASK_COUNT == 2 || SHOWDOWN_TASK_COUNT == 1){
                            for(Player all : Bukkit.getOnlinePlayers()){
                                ChestUser a = ChestUser.getUser(all);

                                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("The final showdown starts in %s seconds!").replace("%s",ChatColor.AQUA.toString() + SHOWDOWN_TASK_COUNT + ChatColor.GOLD.toString()));
                                all.playSound(all.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                            }
                        }

                        if(SHOWDOWN_TASK_COUNT == 30){
                            // load showdown map

                            try {
                                PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT `id` FROM `maps` WHERE `mapType` = ? AND `active` = ? ORDER BY RAND()");
                                ps.setString(1,MapType.SG_SHOWDOWN.toString());
                                ps.setBoolean(2,true);
                                ResultSet rs = ps.executeQuery();
                                if(rs.first()){
                                    final Map map = Map.getMap(rs.getInt("id"));
                                    SHOWDOWN_MAP = map;
                                    SHOWDOWN_MAP_WORLDNAME = map.loadMapToServer();
                                } else {
                                    System.err.println("UNABLE TO LOAD SHOWDOWN MAP!");
                                    Bukkit.shutdown();
                                }
                                MySQLManager.getInstance().closeResources(rs,ps);
                            } catch(Exception e){
                                e.printStackTrace();
                            }
                        }

                        if(SHOWDOWN_TASK_COUNT == 10){
                            prepareWorld(Bukkit.getWorld(SHOWDOWN_MAP_WORLDNAME));
                        }

                        SHOWDOWN_TASK_COUNT--;
                    }
                }
            }.runTaskTimer(this,1L,1*20);

            MAY_START_SHOWDOWN = false;
        }
    }

    public int getMapVotes(Map m){
        /*if(VOTING_MAPS.contains(m)){
            int slot = 0;
            for(Map ma : VOTING_MAPS){
                if(ma == m) break;
                slot++;
            }

            int votes = 0;
            for(String ss : SurvivalGames.VOTES){
                if(SurvivalGames.VOTES.get(ss) == slot) votes++;
            }

            return votes;
        } else {
            return 0;
        }*/

        if(VOTING_MAPS.contains(m)){
            int votes = 0;

            for(MapVote v : SurvivalGames.VOTES){
                if(v.map == m) votes++;
            }

            return votes;
        } else {
            return 0;
        }
    }

    public boolean hasVoted(Player p){
        for(MapVote v : VOTES){
            if(v.p == p) return true;
        }

        return false;
    }

    public Player getNearestPlayer(Player p){
        Player p2 = null;

        for(Player a : Bukkit.getOnlinePlayers()){
            if(a != p && PARTICIPANTS.contains(a)){
                if(p2 == null){
                    p2 = a;
                } else {
                    if(a.getWorld() == p.getWorld()){
                        if(a.getLocation().distance(p.getLocation()) < p2.getLocation().distance(p.getLocation())){
                            p2 = a;
                        }
                    }
                }
            }
        }

        return p2;
    }

    public void startGracePeriodCountdown(){
        if(gracePeriodTask == null){
            gracePeriodCount = 20;

            gracePeriodTask = new BukkitRunnable(){
                @Override
                public void run() {
                    if(gracePeriodCount == 0){
                        cancel();
                        gracePeriodCount = 20;
                        gracePeriodTask = null;

                        gracePeriod = false;

                        for(Player all : Bukkit.getOnlinePlayers()){
                            ChestUser a = ChestUser.getUser(all);

                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("The grace period has ended!"));
                        }

                        Bukkit.getScheduler().scheduleSyncRepeatingTask(SurvivalGames.getInstance(),new Runnable(){
                            public void run(){
                                for(Player all : Bukkit.getOnlinePlayers()){
                                    ChestUser a = ChestUser.getUser(all);

                                    if(PARTICIPANTS.size() > 1){
                                        all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p players are still alive.").replace("%p",String.valueOf(PARTICIPANTS.size())));
                                        all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.AQUA + a.getTranslatedMessage("%s spectators are currently watching.").replace("%s",String.valueOf(SPECTATORS.size())));
                                    }
                                }
                            }
                        },1L,5*60*20);

                        Bukkit.getScheduler().scheduleSyncRepeatingTask(SurvivalGames.getInstance(),new Runnable(){
                            public void run(){
                                for(Player all : Bukkit.getOnlinePlayers()){
                                    Player p2 = getNearestPlayer(all);
                                    if(p2 != null){
                                        all.setCompassTarget(p2.getLocation());
                                    }
                                }
                            }
                        },1L,2*20);

                        Bukkit.getScheduler().scheduleSyncDelayedTask(SurvivalGames.getInstance(),new Runnable(){
                            public void run(){
                                startShowdown();
                            }
                        },30*60*20);

                        if(PARTICIPANTS.size() <= SHOWDOWN_MIN_PLAYERS) {
                            startShowdown();
                        }

                        CURRENT_PHASE = SurvivalGamesPhase.INGAME;
                        ServerSettingsManager.updateGameState(GameState.INGAME);
                    } else {
                        if(gracePeriodCount == 20 || gracePeriodCount == 10 || gracePeriodCount == 5 || gracePeriodCount == 4 || gracePeriodCount == 3 || gracePeriodCount == 2 || gracePeriodCount == 1){
                            for(Player all : Bukkit.getOnlinePlayers()){
                                ChestUser a = ChestUser.getUser(all);

                                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("The grace period ends in %s seconds!").replace("%s",ChatColor.AQUA.toString() + gracePeriodCount + ChatColor.GOLD.toString()));
                            }
                        }

                        gracePeriodCount--;
                    }
                }
            }.runTaskTimer(this,20,20);
        }
    }

    public void startWarmupCountdown(){
        if(warmupCountdownTask == null){
            warmupCountdownCount = 30;

            warmupCountdownTask = new BukkitRunnable(){
                @Override
                public void run() {
                    if(warmupCountdownCount == 0){
                        cancel();
                        warmupCountdownCount = 30;
                        warmupCountdownTask = null;

                        for(Player all : Bukkit.getOnlinePlayers()){
                            all.setLevel(0);
                            all.setExp(0);
                            all.playSound(all.getEyeLocation(),Sound.NOTE_BASS,1f,2f);
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + ChestUser.getUser(all).getTranslatedMessage("The game starts NOW!"));

                            BountifulAPI.sendTitle(all,10,20,10,ChatColor.DARK_GREEN.toString() + ChatColor.BOLD + ChestUser.getUser(all).getTranslatedMessage("GO!"));

                            updateScoreboard(true,all);
                        }

                        CURRENT_PHASE = SurvivalGamesPhase.GRACE_PERIOD;
                        startGracePeriodCountdown();

                        ServerSettingsManager.KILL_EFFECTS = true;
                        ServerSettingsManager.ARROW_TRAILS = true;

                        Bukkit.getWorld(SurvivalGames.MAP_WORLDNAME).setTime(0L);
                        Bukkit.getScheduler().scheduleSyncDelayedTask(SurvivalGames.getInstance(), new Runnable(){
                            @Override
                            public void run() {
                                MainListener.CHESTS.clear();

                                for(Player all : Bukkit.getOnlinePlayers()){
                                    all.playSound(all.getEyeLocation(),Sound.LEVEL_UP,1f,0.5f);
                                    all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + ChestUser.getUser(all).getTranslatedMessage("All chests have been refilled!"));
                                }
                            }
                        },(long)(10+1.5)*60*20);
                    } else {
                        for(Player all : Bukkit.getOnlinePlayers()){
                            ChestUser a = ChestUser.getUser(all);
                            all.setExp((float) ((double) warmupCountdownCount / 30D));
                            all.setLevel(warmupCountdownCount);

                            if(warmupCountdownCount == 60 || warmupCountdownCount == 30 || warmupCountdownCount == 20 || warmupCountdownCount == 10 || warmupCountdownCount == 5 || warmupCountdownCount == 4 || warmupCountdownCount == 3 || warmupCountdownCount == 2 || warmupCountdownCount == 1){
                                all.playSound(all.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("The game starts in %s seconds!").replace("%s",ChatColor.AQUA.toString() + warmupCountdownCount + ChatColor.GOLD.toString()));
                            }

                            if(warmupCountdownCount == 10){
                                BountifulAPI.sendTitle(all,10,10,10,ChatColor.YELLOW.toString() + warmupCountdownCount,"");
                            } else if(warmupCountdownCount == 5){
                                BountifulAPI.sendTitle(all,10,10,10,ChatColor.YELLOW.toString() + warmupCountdownCount,"");
                            } else if(warmupCountdownCount == 4){
                                BountifulAPI.sendTitle(all,10,10,10,ChatColor.YELLOW.toString() + warmupCountdownCount,"");
                            } else if(warmupCountdownCount == 3){
                                BountifulAPI.sendTitle(all,10,10,10,ChatColor.GOLD.toString() + warmupCountdownCount,"");
                            } else if(warmupCountdownCount == 2){
                                BountifulAPI.sendTitle(all,10,10,10,ChatColor.RED.toString() + warmupCountdownCount,"");
                            } else if(warmupCountdownCount == 1){
                                BountifulAPI.sendTitle(all,10,10,10,ChatColor.DARK_RED.toString() + warmupCountdownCount,"");
                            }
                        }

                        warmupCountdownCount--;
                    }
                }
            }.runTaskTimer(this,20,20);
        }
    }

    // outcome = [ 1 = win, 0,5 = tie, 0 = lose ]
    // k = 20 [ https://de.wikipedia.org/wiki/Elo-Zahl ]
    public static int calculateRating(int p1Rating, int p2Rating, int outcome, double k){
        int diff = p1Rating - p2Rating;
        double expected = (double) (1.0 / (Math.pow(10.0, -diff / 400.0) + 1));
        //return (int) Math.round(p1Rating + k*(outcome - expected));
        return (int) Math.round(k*(outcome - expected));
    }

    /*public void startLobbyCountdown(){
        if(lobbyCountdownTask == null){
            lobbyCountdownCount = 60;

            lobbyCountdownTask = new BukkitRunnable(){
                @Override
                public void run() {
                    if(lobbyCountdownCount == 0){
                        cancel();
                        lobbyCountdownCount = 60;
                        lobbyCountdownTask = null;

                        ArrayList<MapLocationData> spawnpoints = FINAL_MAP.getSpawnpoints();
                        Collections.shuffle(spawnpoints);

                        CURRENT_PHASE = SurvivalGamesPhase.WARMUP;
                        ServerSettingsManager.updateGameState(GameState.WARMUP);
                        ServerSettingsManager.VIP_JOIN = false;
                        MapRatingManager.MAP_TO_RATE = FINAL_MAP;

                        GameManager.initializeNewGame(GameType.SURVIVAL_GAMES,FINAL_MAP);

                        for(Player p : Bukkit.getOnlinePlayers()){
                            ChestUser.getUser(p).clearScoreboard();
                            GameManager.getCurrentGames().get(0).getParticipants().add(p.getUniqueId());
                            p.teleport(spawnpoints.get(0).toBukkitLocation());
                            spawnpoints.remove(spawnpoints.get(0));
                            p.setGameMode(GameMode.SURVIVAL);
                            p.getInventory().clear();
                            p.getInventory().setArmorContents(null);
                            PARTICIPANTS.add(p);
                            SurvivalUser.get(p).addPlayedGames(1);

                            Collections.shuffle(spawnpoints);
                            FINAL_MAP.sendMapCredits(p);
                            FINAL_MAP.sendRateMapInfo(p);
                        }

                        startWarmupCountdown();
                    } else {
                        for(Player all : Bukkit.getOnlinePlayers()){
                            ChestUser a = ChestUser.getUser(all);
                            all.setExp((float) ((double) lobbyCountdownCount / 60D));
                            all.setLevel(lobbyCountdownCount);

                            if(lobbyCountdownCount == 60 || lobbyCountdownCount == 30 || lobbyCountdownCount == 20 || lobbyCountdownCount == 10 || lobbyCountdownCount == 5 || lobbyCountdownCount == 4 || lobbyCountdownCount == 3 || lobbyCountdownCount == 2 || lobbyCountdownCount == 1){
                                all.playSound(all.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("The lobby phase ends in %s seconds!").replace("%s",ChatColor.AQUA.toString() + lobbyCountdownCount + ChatColor.GOLD.toString()));
                            }
                        }

                        if(lobbyCountdownCount == 30){
                            Map map = VOTING_MAPS.get(0);
                            int maxVotes = 0;

                            for(Map m : VOTING_MAPS){
                                int v = getMapVotes(m);

                                if(v > maxVotes){
                                    map = m;
                                    maxVotes = v;
                                }
                            }

                            FINAL_MAP = map;
                            map.loadMapToServer();
                            VOTING_OPEN = false;
                            for(Player all : Bukkit.getOnlinePlayers()){
                                updateScoreboard(false,all);
                                all.playSound(all.getEyeLocation(),Sound.ANVIL_LAND, 1f, 1f);
                                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + ChestUser.getUser(all).getTranslatedMessage("The map %m has won the voting!").replace("%m",ChatColor.AQUA + map.getName() + ChatColor.GOLD));
                            }
                            Bukkit.getScheduler().scheduleSyncDelayedTask(SurvivalGames.getInstance(),new Runnable(){
                                public void run(){
                                    prepareWorld(Bukkit.getWorld(FINAL_MAP.getWorldName()));
                                }
                            }, 20*20);
                        }

                        lobbyCountdownCount--;
                    }
                }
            }.runTaskTimer(this,20L,20L);
        }
    }

    public void cancelLobbyCountdown(){
        if(lobbyCountdownTask != null){
            lobbyCountdownCount = 60;
            lobbyCountdownTask.cancel();
            lobbyCountdownTask = null;

            for(Player all : Bukkit.getOnlinePlayers()){
                all.setExp((float) ((double) lobbyCountdownCount / 60D));
                all.setLevel(lobbyCountdownCount);
                all.playSound(all.getEyeLocation(),Sound.NOTE_BASS,1f,0.5f);
                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + ChestUser.getUser(all).getTranslatedMessage("The countdown has been cancelled."));
            }
        }
    }*/

    public static int randomInteger(int min, int max){
        Random rdm = new Random();
        int rdmNm = rdm.nextInt((max - min) + 1) + min;

        return rdmNm;
    }

    public void prepareWorld(World w){
        prepareWorld(w,false);
    }

    public void prepareWorld(World w, boolean lobby){
        if(lobby){
            w.setGameRuleValue("doMobSpawning","false");
            w.setGameRuleValue("doDaylightCycle","false");
            w.setTime(5000);
            w.setStorm(false);
            w.setThundering(false);

            for(Entity e : w.getEntities()){
                if(e.getType() != EntityType.ARMOR_STAND && e.getType() != EntityType.PAINTING && e.getType() != EntityType.ITEM_FRAME && e.getType() != EntityType.PLAYER){
                    e.remove();
                }
            }
        } else {
            w.setGameRuleValue("doMobSpawning","false");
            w.setGameRuleValue("doDaylightCycle","true");
            w.setTime(0);
            w.setStorm(false);
            w.setThundering(false);

            for(Entity e : w.getEntities()){
                if(e.getType() != EntityType.ARMOR_STAND && e.getType() != EntityType.PAINTING && e.getType() != EntityType.ITEM_FRAME && e.getType() != EntityType.PLAYER){
                    e.remove();
                }
            }
        }
    }

    public void death(Player p){
        death(p,true);
    }

    public void death(Player p, boolean spectate){
        SurvivalUser s = SurvivalUser.get(p);
        ChestUser u = ChestUser.getUser(p);

        u.showFameTitlePlate = false;
        u.removeFameTitleAboveHead();

        if(s.lastDamager == null){
            // SUICIDE

            if(spectate){
                spectate(p);
            } else {
                PARTICIPANTS.remove(p);
            }

            for(Player all : Bukkit.getOnlinePlayers()){
                updateScoreboard(true,all);
                if(SurvivalUser.get(all).lastDamager == p) SurvivalUser.get(all).lastDamager = null;

                ChestUser a = ChestUser.getUser(all);
                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p died.").replace("%p",p.getDisplayName() + ChatColor.GOLD));

                if(PARTICIPANTS.size() > 1){
                    all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p tributes remain.").replace("%p",String.valueOf(PARTICIPANTS.size())));
                }
            }

            s.reducePoints(7);
            s.addDeaths(1);
            GameManager.getCurrentGames().get(0).addPlayerDeathEvent(p);
        } else {
            // KILLED BY PLAYER
            Player killer = s.lastDamager;

            if(spectate){
                spectate(p);
            } else {
                PARTICIPANTS.remove(p);
            }

            //s.reducePoints(11);
            //SurvivalUser.get(killer).addPoints(7);

            int p1 = SurvivalGames.calculateRating(s.getPoints(),SurvivalUser.get(killer).getPoints(),1,20);
            int p2 = -SurvivalGames.calculateRating(SurvivalUser.get(killer).getPoints(),s.getPoints(),0,20);

            SurvivalUser.get(killer).addPoints(p1);
            s.reducePoints(p2);

            s.addDeaths(1);
            SurvivalUser.get(killer).addKills(1);
            SurvivalUser.get(killer).getUser().giveExp(2);
            SurvivalUser.get(killer).getUser().achieve(27);

            for(Player all : Bukkit.getOnlinePlayers()){
                ChestUser a = ChestUser.getUser(all);
                updateScoreboard(true,all);
                if(SurvivalUser.get(all).lastDamager == p) SurvivalUser.get(all).lastDamager = null;

                if(SurvivalGames.FIRST_BLOOD == null){
                    SurvivalGames.FIRST_BLOOD = killer;
                    all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.DARK_RED.toString() + ChatColor.BOLD.toString() + a.getTranslatedMessage("First Blood") + ": " + killer.getDisplayName());
                    ChestUser.getUser(killer).achieve(12);
                }

                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p was killed by %k.").replace("%p",p.getDisplayName() + ChatColor.GOLD).replace("%k",killer.getDisplayName() + ChatColor.GOLD));

                if(PARTICIPANTS.size() > 1){
                    all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p tributes remain.").replace("%p",String.valueOf(PARTICIPANTS.size())));
                }
            }

            if(p.getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.VOID) SurvivalUser.get(killer).getUser().achieve(49);
            GameManager.getCurrentGames().get(0).addPlayerDeathEvent(p,killer);

            if((killer.getInventory().getHelmet() == null || killer.getInventory().getHelmet().getType() == Material.AIR) && (killer.getInventory().getChestplate() == null || killer.getInventory().getChestplate().getType() == Material.AIR) && (killer.getInventory().getLeggings() == null || killer.getInventory().getLeggings().getType() == Material.AIR) && (killer.getInventory().getBoots() == null || killer.getInventory().getBoots().getType() == Material.AIR)){
                SurvivalUser.get(killer).getUser().achieve(47);
            }

            killer.playSound(p.getEyeLocation(),Sound.LEVEL_UP,1f,2f);
            Hologram h = HologramsAPI.createHologram(SurvivalGames.getInstance(),p.getLocation().clone().add(0,1,0));
            h.appendTextLine(ChatColor.GREEN + "+" + ChatColor.YELLOW.toString() + p1 + " " + ChatColor.GREEN + SurvivalUser.get(killer).getUser().getTranslatedMessage("Punkte"));
            h.getVisibilityManager().setVisibleByDefault(false);
            h.getVisibilityManager().showTo(killer);

            Bukkit.getScheduler().scheduleSyncDelayedTask(SurvivalGames.getInstance(), new Runnable(){
                public void run(){
                    h.delete();
                }
            },2*20);
        }

        if(PARTICIPANTS.size() == 1){
            chooseWinner();
        } else if(PARTICIPANTS.size() == 0){
            Bukkit.broadcastMessage(ChatColor.RED + "An error occured.");
            for(Player all : Bukkit.getOnlinePlayers()){
                ChestUser.getUser(all).connectToLobby();
            }

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"restart");
        } else if(PARTICIPANTS.size() <= SHOWDOWN_MIN_PLAYERS) {
            startShowdown();
        }
    }

    public void spectate(Player p){
        SurvivalUser s = SurvivalUser.get(p);
        ChestUser u = ChestUser.getUser(p);
        u.mayChat = false;

        if(PARTICIPANTS.contains(p)) PARTICIPANTS.remove(p);
        if(!SPECTATORS.contains(p)) SPECTATORS.add(p);

        p.setGameMode(GameMode.SPECTATOR);
        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("You are now a spectator."));
    }

    public void updateScoreboard(boolean ingameScoreboard, Player p){
        updateScoreboard(ingameScoreboard,p,0,0);
    }

    public void updateScoreboard(boolean ingameScoreboard, Player p, int reducePlayerAmount, int pointsDifference){
        ChestAPI.sync(() -> {
            SurvivalUser s = SurvivalUser.get(p);
            ChestUser u = ChestUser.getUser(p);

            Scoreboard b = u.getScoreboard();
            Objective o = null;
        /*if(b.getObjective(DisplaySlot.SIDEBAR) == null){
            o = b.registerNewObjective("side","dummy");
        } else {
            o = b.getObjective(DisplaySlot.SIDEBAR);
        }*/
            if(b.getObjective(DisplaySlot.SIDEBAR) != null) b.getObjective(DisplaySlot.SIDEBAR).unregister();
            o = b.registerNewObjective("side","dummy");

            if(ingameScoreboard){
                o.setDisplayName(ChatColor.DARK_RED + "Survival Games");
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                o.getScore("      ").setScore(15);
                o.getScore(ChatColor.AQUA + u.getTranslatedMessage("Points") + ":").setScore(14);
            /*if(pointsDifference != 0){
                b.resetScores(ChatColor.YELLOW.toString() + (s.getPoints()-pointsDifference) + "   ");
                b.resetScores(ChatColor.YELLOW.toString() + (s.getPoints()+pointsDifference) + "   ");
            }*/
                o.getScore(ChatColor.YELLOW.toString() + s.getPoints() + "    ").setScore(13);
                o.getScore("     ").setScore(12);
                o.getScore(ChatColor.RED + u.getTranslatedMessage("Kills") + ":").setScore(11);
                o.getScore(ChatColor.YELLOW.toString() + s.getCurrentKills() + "   ").setScore(10);
                o.getScore("  ").setScore(9);
                o.getScore(ChatColor.AQUA + u.getTranslatedMessage("Remaining") + ":").setScore(8);
                o.getScore(ChatColor.YELLOW.toString() + PARTICIPANTS.size() + "  ").setScore(7);
                o.getScore(" ").setScore(6);
                o.getScore(ChatColor.AQUA + u.getTranslatedMessage("Game ID") + ":").setScore(5);
                o.getScore(ChatColor.YELLOW.toString() + GameManager.getCurrentGames().get(0).getID() + " ").setScore(4);
                o.getScore("   ").setScore(3);
                o.getScore(StringUtils.SCOREBOARD_LINE_SEPERATOR).setScore(2);
                o.getScore(StringUtils.SCOREBOARD_FOOTER_IP).setScore(1);
            } else {
                o.setDisplayName(ChatColor.DARK_RED + "Survival Games");
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                o.getScore(" ").setScore(11);
                b.resetScores(ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Players") + ": " + ChatColor.YELLOW.toString() + (Bukkit.getOnlinePlayers().size()-1));
                b.resetScores(ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Players") + ": " + ChatColor.YELLOW.toString() + (Bukkit.getOnlinePlayers().size()+1));
                if(reducePlayerAmount != 0) b.resetScores(ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Players") + ": " + ChatColor.YELLOW.toString() + (Bukkit.getOnlinePlayers().size()));
                o.getScore(ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Players") + ": " + ChatColor.YELLOW.toString() + (Bukkit.getOnlinePlayers().size()-reducePlayerAmount)).setScore(10);
                o.getScore(ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Min. Players") + ": " + ChatColor.YELLOW.toString() + MIN_PLAYERS).setScore(9);
                o.getScore("  ").setScore(8);
                int i = 7;
                ArrayList<Map> maps = new ArrayList<Map>();
                for(Map ma : VOTING_MAPS) maps.add(ma);
                Collections.sort(maps, new Comparator<Map>() {
                    public int compare(Map m1, Map m2) {
                        return getMapVotes(m2) - getMapVotes(m1);
                    }
                });

                for(Map m : maps){
                /*if(added != 0){
                    //b.resetScores(ChatColor.GREEN + StringUtils.limitString(m.getName(),7) + ": " + ChatColor.YELLOW + (getMapVotes(m)-added));
                    b.resetScores(StringUtils.limitString(m.getName(),16));
                }*/

                    //o.getScore(ChatColor.GREEN + StringUtils.limitString(m.getName(),8) + ": " + ChatColor.YELLOW + getMapVotes(m)).setScore(i);
                    o.getScore(StringUtils.limitString(m.getName(),16).trim()).setScore(i);
                    u.setPlayerPrefix(StringUtils.limitString(m.getName(),16).trim(),ChatColor.GREEN.toString());
                    u.setPlayerSuffix(StringUtils.limitString(m.getName(),16).trim(),": " + ChatColor.YELLOW + getMapVotes(m));

                    i--;
                }

                o.getScore("    ").setScore(3);
                o.getScore(StringUtils.SCOREBOARD_LINE_SEPERATOR).setScore(2);
                o.getScore(StringUtils.SCOREBOARD_FOOTER_IP).setScore(1);
            }
        });
    }

    public void chooseWinner(){
        if(PARTICIPANTS.size() == 0){
            for(Player all : Bukkit.getOnlinePlayers()){
                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + ChestUser.getUser(all).getTranslatedMessage("No winner could be determined."));
                ChestUser.getUser(all).connectToLobby();
            }

            //Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"restart");
            ChestAPI.stopServer();
        } else {
            ServerSettingsManager.updateGameState(GameState.ENDING);
            CURRENT_PHASE = SurvivalGamesPhase.ENDING;

            Player p = PARTICIPANTS.get(0);
            SurvivalUser s = SurvivalUser.get(p);
            ChestUser u = s.getUser();

            s.addPoints(50);
            u.addCoins(50);
            s.addVictories(1);
            p.playSound(p.getEyeLocation(),Sound.LEVEL_UP,1f,1f);
            u.giveExp(25);

            if(s.getVictories() >= 10) u.achieve(9);
            if(s.getVictories() >= 25) u.achieve(10);
            if(s.getVictories() >= 50) u.achieve(11);

            GameManager.getCurrentGames().get(0).getWinners().add(p.getUniqueId());
            GameManager.getCurrentGames().get(0).setCompleted(true);
            GameManager.getCurrentGames().get(0).saveData();

            boolean fireworkSound = true;

            u.playVictoryEffect();

            for(Player all : Bukkit.getOnlinePlayers()){
                String display = p.getDisplayName();
                if(ChestUser.getUser(all).hasPermission(Rank.VIP)) display = ChestUser.getUser(p).getRank().getColor() + p.getName();

                all.sendMessage("");
                all.sendMessage(ChatColor.DARK_GREEN + ChestUser.getUser(all).getTranslatedMessage("%p has won the Survival Games! Congratulations!").replace("%p",display + ChatColor.DARK_GREEN));
                all.sendMessage("");

                ChestUser.getUser(all).mayChat = true;
                ChestUser.getUser(all).clearScoreboard();

                BountifulAPI.sendTitle(all,1*20,5*20,1*20,p.getDisplayName(),ChatColor.GRAY + ChestUser.getUser(all).getTranslatedMessage("is the WINNER!"));
            }

            final boolean f = fireworkSound;

            Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
                @Override
                public void run() {
                    for(Player all : Bukkit.getOnlinePlayers()){
                        if(f) all.playSound(all.getEyeLocation(), Sound.FIREWORK_LAUNCH,1f,1f);
                        all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.AQUA + ChestUser.getUser(all).getTranslatedMessage("This server restarts in 10 seconds."));
                    }

                    ChestAPI.giveAfterGameCrate(new Player[]{p});
                }
            }, 2*20);

            Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
                @Override
                public void run() {
                    for(Player all : Bukkit.getOnlinePlayers()){
                        ChestUser.getUser(all).sendGameLogMessage(GameManager.getCurrentGames().get(0).getID());
                    }
                }
            }, 3*20);

            Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
                @Override
                public void run() {
                    for(Player all : Bukkit.getOnlinePlayers()){
                        ChestUser a = ChestUser.getUser(all);

                        a.sendAfterGamePremiumAd();
                        a.connectToLobby();
                    }
                }
            }, 12*20);

            Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
                @Override
                public void run() {
                    //Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"restart");
                    ChestAPI.stopServer();
                }
            }, 18*20);
        }
    }

    public void setLobbyLocation(Location loc){
        this.lobbyLocation = loc;

        getConfig().set("lobbyLocation.world", loc.getWorld().getName());
        getConfig().set("lobbyLocation.x", loc.getX());
        getConfig().set("lobbyLocation.y", loc.getY());
        getConfig().set("lobbyLocation.z", loc.getZ());
        getConfig().set("lobbyLocation.yaw", loc.getYaw());
        getConfig().set("lobbyLocation.pitch", loc.getPitch());
        saveConfig();
    }

    public void onDisable(){
        for(Player all : Bukkit.getOnlinePlayers()){
            SurvivalUser.unregister(all);
        }
    }

    private void chooseMapsForVoting(){
        /*try {
            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `maps` WHERE `gamemode` = ? AND `active` = ? ORDER BY RAND() DESC LIMIT 4");
            ps.setString(1,GameType.SURVIVAL_GAMES.toString());
            ps.setBoolean(2,true);
            ResultSet rs = ps.executeQuery();
            rs.beforeFirst();

            while(rs.next()){
                ServerSettingsManager.VOTING_MAPS.add(Map.getMap(rs.getInt("id")));
            }

            MySQLManager.getInstance().closeResources(rs,ps);

            if(ServerSettingsManager.VOTING_MAPS.size() == 0){
                System.err.print("NO MAPS COULD BE LOADED!");
                System.err.print("SHUTTING DOWN!");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"restart");
            } else {
                Collections.shuffle(ServerSettingsManager.VOTING_MAPS);
            }
        } catch (Exception e){
            e.printStackTrace();
        }*/

        MapVotingManager.chooseMapsForVoting();
    }
}
