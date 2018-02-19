package eu.thechest.survivalgames.listener;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.event.FameTitlePlateDamageByEntityEvent;
import eu.thechest.chestapi.event.FinalMapLoadedEvent;
import eu.thechest.chestapi.event.PlayerDataLoadedEvent;
import eu.thechest.chestapi.event.VotingEndEvent;
import eu.thechest.chestapi.game.GameManager;
import eu.thechest.chestapi.items.ItemUtil;
import eu.thechest.chestapi.maps.Map;
import eu.thechest.chestapi.maps.MapLocationData;
import eu.thechest.chestapi.maps.MapRatingManager;
import eu.thechest.chestapi.maps.MapVote;
import eu.thechest.chestapi.server.GameState;
import eu.thechest.chestapi.server.GameType;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.user.Rank;
import eu.thechest.chestapi.util.StringUtils;
import eu.thechest.survivalgames.SurvivalGames;
import eu.thechest.survivalgames.SurvivalGamesPhase;
import eu.thechest.survivalgames.cmd.MainExecutor;
import eu.thechest.survivalgames.user.SurvivalUser;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static eu.thechest.survivalgames.SurvivalGames.PARTICIPANTS;
import static eu.thechest.survivalgames.SurvivalGames.SPECTATORS;
import static eu.thechest.survivalgames.SurvivalGames.lobbyCountdownCount;

/**
 * Created by zeryt on 27.02.2017.
 */
public class MainListener implements Listener {
    public static HashMap<Location,Inventory> CHESTS = new HashMap<Location,Inventory>();
    public static HashMap<Location,Inventory> PERMANENT_CHESTS = new HashMap<Location,Inventory>();

    @EventHandler
    public void onLogin(PlayerLoginEvent e){
        Player p = e.getPlayer();

        if(SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.ENDING){
            e.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            e.setKickMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "The game is currently ending.");
        } else if(SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.WARMUP){
            e.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            e.setKickMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "Warmup has already started.");
        }
    }

    @EventHandler
    public void onVoteFinish(VotingEndEvent e){
        SurvivalGames.FINAL_MAP = e.getFinalMap();
        SurvivalGames.getInstance().startWarmupCountdown();
        SurvivalGames.CURRENT_PHASE = SurvivalGamesPhase.WARMUP;

        ArrayList<MapLocationData> spawnpoints = SurvivalGames.FINAL_MAP.getSpawnpoints();
        Collections.shuffle(spawnpoints);

        ServerSettingsManager.updateGameState(GameState.WARMUP);
        ServerSettingsManager.VIP_JOIN = false;
        MapRatingManager.MAP_TO_RATE = SurvivalGames.FINAL_MAP;

        GameManager.initializeNewGame(GameType.SURVIVAL_GAMES,SurvivalGames.FINAL_MAP);

        for(Player p : Bukkit.getOnlinePlayers()){
            ChestUser.getUser(p).clearScoreboard();
            GameManager.getCurrentGames().get(0).getParticipants().add(p.getUniqueId());
            p.teleport(spawnpoints.get(0).toBukkitLocation(SurvivalGames.MAP_WORLDNAME));
            spawnpoints.remove(spawnpoints.get(0));
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);

            Collections.shuffle(spawnpoints);
            SurvivalGames.FINAL_MAP.sendMapCredits(p);
            SurvivalGames.FINAL_MAP.sendRateMapInfo(p);
            SurvivalUser.get(p).addPlayedGames(1);
        }

        PARTICIPANTS.addAll(Bukkit.getOnlinePlayers());
    }

    @EventHandler
    public void onFinalMapLoaded(FinalMapLoadedEvent e){
        //SurvivalGames.getInstance().prepareWorld(Bukkit.getWorld(e.getFinalMap().getWorldName()));
        SurvivalGames.FINAL_MAP = e.getFinalMap();
        SurvivalGames.MAP_WORLDNAME = e.getWorld().getName();
        SurvivalGames.getInstance().prepareWorld(e.getWorld());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e){
        Player p = e.getPlayer();

        e.setJoinMessage(null);
    }

    @EventHandler
    public void onLoaded(PlayerDataLoadedEvent e){
        Player p = e.getPlayer();

        ChestAPI.async(() -> {
            SurvivalUser s = SurvivalUser.get(p);
            ChestUser u = ChestUser.getUser(p);

            if(s.getVictories() >= 10) u.achieve(9);
            if(s.getVictories() >= 25) u.achieve(10);
            if(s.getVictories() >= 50) u.achieve(11);

            if(SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.VOTE_MAP){
                // moved to ChestAPI
            } else if(SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.INGAME){
                p.getInventory().clear();
                p.getInventory().setArmorContents(null);
                p.setFireTicks(0);
                p.setFoodLevel(20);

                ChestAPI.sync(() -> {
                    if(SurvivalGames.FINAL_MAP.getSpawnpoints().size() > 0){
                        p.teleport(SurvivalGames.FINAL_MAP.getSpawnpoints().get(0).toBukkitLocation(SurvivalGames.MAP_WORLDNAME));
                    } else {
                        p.teleport(Bukkit.getWorld(SurvivalGames.MAP_WORLDNAME).getSpawnLocation());
                    }
                });

                SurvivalGames.getInstance().spectate(p);
            } else if(SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.SHOWDOWN){
                p.getInventory().clear();
                p.getInventory().setArmorContents(null);
                p.setFireTicks(0);
                p.setFoodLevel(20);

                ChestAPI.sync(() -> {
                    if(SurvivalGames.SHOWDOWN_MAP.getSpawnpoints().size() > 0){
                        p.teleport(SurvivalGames.SHOWDOWN_MAP.getSpawnpoints().get(0).toBukkitLocation(SurvivalGames.SHOWDOWN_MAP_WORLDNAME));
                    } else {
                        p.teleport(Bukkit.getWorld(SurvivalGames.SHOWDOWN_MAP_WORLDNAME).getSpawnLocation());
                    }
                });

                SurvivalGames.getInstance().spectate(p);
            }
        });
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e){
        if(SurvivalGames.CURRENT_PHASE != SurvivalGamesPhase.INGAME){
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e){
        Player p = e.getPlayer();

        if(SurvivalGames.PARTICIPANTS.contains(p)){
            if(SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.WARMUP){
                Location from = e.getFrom();
                Location to = e.getTo();
                double x = Math.floor(from.getX());
                double z = Math.floor(from.getZ());

                if(Math.floor(to.getX()) != x || Math.floor(to.getZ()) != z){
                    x += .5;
                    z += .5;
                    e.getPlayer().teleport(new Location(from.getWorld(),x,from.getY(),z,from.getYaw(),from.getPitch()));
                }
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e){
        if(SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.VOTE_MAP){
            // moved to ChestAPI
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e){
        Player p = e.getPlayer();
        if(SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.SHOWDOWN || SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.INGAME){
            if(SPECTATORS.contains(p)){
                e.setCancelled(true);
                for(Player s : SPECTATORS){
                    if(ChestUser.getUser(s).hasPermission(Rank.VIP)){
                        s.sendMessage(ChatColor.AQUA + "[SPECTATOR] " + ChatColor.GRAY + p.getName() + ": " + ChatColor.WHITE + e.getMessage());
                    } else {
                        s.sendMessage(ChatColor.AQUA + "[SPECTATOR] " + ChatColor.GRAY + ChatColor.stripColor(p.getDisplayName()) + ": " + ChatColor.WHITE + e.getMessage());
                    }
                }
                GameManager.getCurrentGames().get(0).addSpectatorChatEvent(p,e.getMessage());
            } else if(PARTICIPANTS.contains(p)){
                GameManager.getCurrentGames().get(0).addPlayerChatEvent(p,e.getMessage());
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e){
        Player p = e.getPlayer();

        if(SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.GRACE_PERIOD || SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.INGAME || SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.SHOWDOWN){
            if((e.getBlock().getType() != Material.LEAVES && e.getBlock().getType() != Material.LEAVES_2 && e.getBlock().getType() != Material.BROWN_MUSHROOM && e.getBlock().getType() != Material.RED_MUSHROOM && e.getBlock().getType() != Material.VINE && e.getBlock().getType() != Material.CROPS) || (!SurvivalGames.PARTICIPANTS.contains(p))){
                e.setCancelled(true);
            }
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e){
        if(e.getPlayer() instanceof Player){
            Player p = (Player)e.getPlayer();

            if(e.getInventory().getType() == InventoryType.CHEST && SurvivalGames.PARTICIPANTS.contains(p)){
                p.playSound(p.getEyeLocation(),Sound.CHEST_CLOSE,1f,1f);
            }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent e){
        if(e.getWhoClicked() instanceof Player){
            Player p = (Player)e.getWhoClicked();
            ChestUser u = ChestUser.getUser(p);

            if(e.getCurrentItem().getType() == Material.DIAMOND_HOE) u.achieve(30);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e){
        Player p = e.getPlayer();
        SurvivalUser s = SurvivalUser.get(p);
        ChestUser u = ChestUser.getUser(p);

        if(SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.INGAME || SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.GRACE_PERIOD || SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.SHOWDOWN){
            if(e.getAction() == Action.RIGHT_CLICK_BLOCK){
                if(e.getClickedBlock() != null && e.getClickedBlock().getType() != null && e.getClickedBlock().getType() == Material.REDSTONE_BLOCK && SurvivalGames.PARTICIPANTS.contains(p)){
                    if(p.getItemInHand() != null && p.getItemInHand().getType() != null && (p.getItemInHand().getType() == Material.WOOD_SWORD || p.getItemInHand().getType() == Material.STONE_SWORD || p.getItemInHand().getType() == Material.IRON_SWORD || p.getItemInHand().getType() == Material.GOLD_SWORD || p.getItemInHand().getType() == Material.DIAMOND_SWORD || p.getItemInHand().getType() == Material.BOW)){
                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You cannot use that item to open a chest."));
                        e.setCancelled(true);
                        return;
                    }

                    if(CHESTS.containsKey(e.getClickedBlock().getLocation())){
                        e.setCancelled(true);
                        e.setUseItemInHand(Event.Result.DENY);
                        e.setUseInteractedBlock(Event.Result.DENY);
                        p.openInventory(CHESTS.get(e.getClickedBlock().getLocation()));
                        p.getWorld().playSound(p.getEyeLocation(), Sound.CHEST_OPEN,1f,1f);
                    } else if(PERMANENT_CHESTS.containsKey(e.getClickedBlock().getLocation())){
                        e.setCancelled(true);
                        e.setUseItemInHand(Event.Result.DENY);
                        e.setUseInteractedBlock(Event.Result.DENY);
                        p.openInventory(PERMANENT_CHESTS.get(e.getClickedBlock().getLocation()));
                        p.getWorld().playSound(p.getEyeLocation(), Sound.CHEST_OPEN,1f,1f);
                    } else {
                        Random rnd = new Random();
                        int n = 1;
                        n = SurvivalGames.randomInteger(1, 7);
                        Inventory inv = Bukkit.createInventory(null, InventoryType.CHEST);
                        List<ItemStack> items = new ArrayList<ItemStack>();

                        for(int i = 1; i <= 51; i++) {
                            if(u.hasGamePerk(3)){
                                items.add(ItemUtil.namedItem(Material.WOOD_SWORD,u.getRank().getColor() + p.getName(),null));
                            } else {
                                items.add(new ItemStack(Material.WOOD_SWORD));
                            }
                            items.add(new ItemStack(Material.WOOD_AXE));
                            if(u.hasGamePerk(3)){
                                items.add(ItemUtil.namedItem(Material.GOLD_SWORD,u.getRank().getColor() + p.getName(),null));
                            } else {
                                items.add(new ItemStack(Material.GOLD_SWORD));
                            }
                            items.add(new ItemStack(Material.GOLD_INGOT));
                            if(u.hasGamePerk(3)){
                                items.add(ItemUtil.namedItem(Material.STONE_SWORD,u.getRank().getColor() + p.getName(),null));
                            } else {
                                items.add(new ItemStack(Material.STONE_SWORD));
                            }
                            items.add(new ItemStack(Material.STONE_AXE));
                            items.add(new ItemStack(Material.BREAD));
                            items.add(new ItemStack(Material.PORK, SurvivalGames.randomInteger(1, 3)));
                            items.add(new ItemStack(Material.APPLE, SurvivalGames.randomInteger(1, 3)));
                            items.add(new ItemStack(Material.GOLD_HELMET));
                            items.add(new ItemStack(Material.GOLD_BOOTS));
                            items.add(new ItemStack(Material.LEATHER_HELMET));
                            items.add(new ItemStack(Material.CHAINMAIL_HELMET));
                            items.add(new ItemStack(Material.CHAINMAIL_LEGGINGS));
                        }

                        for(int i = 1; i <= 41; i++) {
                            items.add(new ItemStack(Material.FISHING_ROD));
                            //items.add(new ItemStack(Material.FLINT_AND_STEEL));
                            items.add(new ItemStack(Material.STICK, SurvivalGames.randomInteger(1, 4)));
                            items.add(new ItemStack(Material.TNT, SurvivalGames.randomInteger(1, 3)));
                            items.add(new ItemStack(Material.GRILLED_PORK, SurvivalGames.randomInteger(1, 5)));
                            items.add(new ItemStack(Material.RAW_BEEF));
                            items.add(new ItemStack(Material.COOKED_BEEF));
                            items.add(new ItemStack(Material.RAW_CHICKEN, SurvivalGames.randomInteger(1, 3)));
                            items.add(new ItemStack(Material.PUMPKIN_PIE, SurvivalGames.randomInteger(1, 5)));
                            items.add(new ItemStack(Material.GOLD_CHESTPLATE));
                            items.add(new ItemStack(Material.GOLD_LEGGINGS));
                            items.add(new ItemStack(Material.LEATHER_CHESTPLATE));
                            items.add(new ItemStack(Material.LEATHER_LEGGINGS));
                            items.add(new ItemStack(Material.CHAINMAIL_BOOTS));
                            items.add(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                        }

                        for(int i = 1; i <= 31; i++) {
                            items.add(new ItemStack(Material.DIAMOND_PICKAXE));
                            items.add(new ItemStack(Material.IRON_PICKAXE));
                            items.add(new ItemStack(Material.IRON_AXE));
                            items.add(new ItemStack(Material.COMPASS));
                            items.add(new ItemStack(Material.IRON_HELMET));
                            items.add(new ItemStack(Material.IRON_BOOTS));
                            items.add(new ItemStack(Material.BOW));
                            items.add(new ItemStack(Material.ARROW, SurvivalGames.randomInteger(1, 37)));
                            items.add(new ItemStack(Material.EXP_BOTTLE, SurvivalGames.randomInteger(1, 3)));
                            items.add(new ItemStack(Material.RAW_FISH));
                            items.add(new ItemStack(Material.CAKE));
                            items.add(new ItemStack(Material.COOKED_FISH, SurvivalGames.randomInteger(1, 5)));
                            items.add(new ItemStack(Material.LEATHER_BOOTS));
                        }

                        for(int i = 1; i <= 21; i++) {
                            items.add(new ItemStack(Material.DIAMOND_AXE));
                            items.add(new ItemStack(Material.IRON_INGOT));
                            if(u.hasGamePerk(3)){
                                items.add(ItemUtil.namedItem(Material.IRON_SWORD,u.getRank().getColor() + p.getName(),null));
                            } else {
                                items.add(new ItemStack(Material.IRON_SWORD));
                            }
                            items.add(new ItemStack(Material.IRON_CHESTPLATE));
                            items.add(new ItemStack(Material.IRON_LEGGINGS));
                            items.add(new ItemStack(Material.GOLDEN_APPLE));
                            items.add(new ItemStack(Material.MELON, SurvivalGames.randomInteger(1, 3)));
                        }

                        for(int i = 1; i <= 6; i++) {
                            ItemStack regen = new ItemStack(Material.POTION);
                            regen.setDurability((short)8257);

                            ItemStack heal = new ItemStack(Material.POTION);
                            regen.setDurability((short)8229);

                            items.add(regen);
                            items.add(heal);
                        }

                        while(n != 0){
                            n--;
                            inv.setItem(SurvivalGames.randomInteger(1, 26), items.get(SurvivalGames.randomInteger(0, items.size())));
                        }

                        CHESTS.put(e.getClickedBlock().getLocation(),inv);
                        e.setCancelled(true);
                        e.setUseItemInHand(Event.Result.DENY);
                        e.setUseInteractedBlock(Event.Result.DENY);
                        p.openInventory(inv);
                        p.getWorld().playSound(p.getEyeLocation(), Sound.CHEST_OPEN,1f,1f);
                        return;
                    }
                }
            }

            if(e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_AIR){
                if(p.getItemInHand() != null && p.getItemInHand().getType() != null){
                    if(p.getItemInHand().getType() == Material.COMPASS){
                        Player n = SurvivalGames.getInstance().getNearestPlayer(p);

                        if(n != null){
                            if(u.hasPermission(Rank.VIP)){
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("Nearest target: %p.").replace("%p",ChestUser.getUser(n).getRank().getColor() + n.getName() + ChatColor.GOLD) + " " + ChatColor.YELLOW + "(" + u.getTranslatedMessage("%b blocks away.").replace("%b",String.valueOf(((Double)n.getLocation().distance(p.getLocation())).intValue())) + ")");
                            } else {
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("Nearest target: %p.").replace("%p",n.getDisplayName() + ChatColor.GOLD) + " " + ChatColor.YELLOW + "(" + u.getTranslatedMessage("%b blocks away.").replace("%b",String.valueOf(((Double)n.getLocation().distance(p.getLocation())).intValue())) + ")");
                            }
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("No target found."));
                        }
                    }

                    if(p.getItemInHand().getType() == Material.LEATHER_HELMET || p.getItemInHand().getType() == Material.CHAINMAIL_HELMET || p.getItemInHand().getType() == Material.IRON_HELMET || p.getItemInHand().getType() == Material.GOLD_HELMET || p.getItemInHand().getType() == Material.DIAMOND_HELMET){
                        if(p.getInventory().getHelmet() != null){
                            ItemStack oldItem = p.getInventory().getHelmet();
                            ItemStack newItem = p.getItemInHand();

                            p.getInventory().setHelmet(newItem);
                            p.getInventory().setItemInHand(oldItem);
                        }
                    }

                    if(p.getItemInHand().getType() == Material.LEATHER_CHESTPLATE || p.getItemInHand().getType() == Material.CHAINMAIL_CHESTPLATE || p.getItemInHand().getType() == Material.IRON_CHESTPLATE || p.getItemInHand().getType() == Material.GOLD_CHESTPLATE || p.getItemInHand().getType() == Material.DIAMOND_CHESTPLATE){
                        if(p.getInventory().getChestplate() != null){
                            ItemStack oldItem = p.getInventory().getChestplate();
                            ItemStack newItem = p.getItemInHand();

                            p.getInventory().setChestplate(newItem);
                            p.getInventory().setItemInHand(oldItem);
                        }
                    }

                    if(p.getItemInHand().getType() == Material.LEATHER_LEGGINGS || p.getItemInHand().getType() == Material.CHAINMAIL_LEGGINGS || p.getItemInHand().getType() == Material.IRON_LEGGINGS || p.getItemInHand().getType() == Material.GOLD_LEGGINGS || p.getItemInHand().getType() == Material.DIAMOND_LEGGINGS){
                        if(p.getInventory().getLeggings() != null){
                            ItemStack oldItem = p.getInventory().getLeggings();
                            ItemStack newItem = p.getItemInHand();

                            p.getInventory().setLeggings(newItem);
                            p.getInventory().setItemInHand(oldItem);
                        }
                    }

                    if(p.getItemInHand().getType() == Material.LEATHER_BOOTS || p.getItemInHand().getType() == Material.CHAINMAIL_BOOTS || p.getItemInHand().getType() == Material.IRON_BOOTS || p.getItemInHand().getType() == Material.GOLD_BOOTS || p.getItemInHand().getType() == Material.DIAMOND_BOOTS){
                        if(p.getInventory().getBoots() != null){
                            ItemStack oldItem = p.getInventory().getBoots();
                            ItemStack newItem = p.getItemInHand();

                            p.getInventory().setBoots(newItem);
                            p.getInventory().setItemInHand(oldItem);
                        }
                    }
                }
            }
        } else if(SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.VOTE_MAP){
            // moved to ChestAPI

            if(e.getAction() == Action.RIGHT_CLICK_BLOCK){
                if(e.getClickedBlock() != null && e.getClickedBlock().getType() != null){
                    if(SurvivalGames.getInstance().DISALLOWED_BLOCKS.contains(e.getClickedBlock().getType())){
                        e.setCancelled(true);
                        e.setUseItemInHand(Event.Result.DENY);
                        e.setUseInteractedBlock(Event.Result.DENY);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onWeather(WeatherChangeEvent e){
        e.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e){
        if(SurvivalGames.CURRENT_PHASE != SurvivalGamesPhase.INGAME && SurvivalGames.CURRENT_PHASE != SurvivalGamesPhase.SHOWDOWN){
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlateDamageBy(FameTitlePlateDamageByEntityEvent e){
        Player p = e.getPlayer();
        ChestUser u = e.getUser();
        ArmorStand a = e.getPlate();
        Entity entity = e.getDamager();

        if(entity instanceof Player){
            Player p2 = (Player)entity;

            if(PARTICIPANTS.contains(p) && PARTICIPANTS.contains(p2)){
                p.damage(e.getDamage(),entity);
            }
        } else if(entity instanceof Arrow){
            Arrow arrow = (Arrow)entity;

            if(arrow.getShooter() instanceof Player){
                Player p2 = (Player)arrow.getShooter();

                if(PARTICIPANTS.contains(p) && PARTICIPANTS.contains(p2)){
                    p.damage(e.getDamage(),entity);
                }
            }
        } else if(entity instanceof FishHook){
            FishHook h = (FishHook)entity;

            if(h.getShooter() instanceof Player){
                Player p2 = (Player)h.getShooter();

                if(PARTICIPANTS.contains(p) && PARTICIPANTS.contains(p2)){
                    p.damage(e.getDamage(),entity);
                }
            }
        }
    }

    @EventHandler
    public void onDamageBy(EntityDamageByEntityEvent e){
        if(SurvivalGames.CURRENT_PHASE != SurvivalGamesPhase.INGAME && SurvivalGames.CURRENT_PHASE != SurvivalGamesPhase.SHOWDOWN){
            e.setCancelled(true);
        } else {
            if(e.getEntity() instanceof Player && e.getDamager() instanceof Player){
                Player p = (Player)e.getEntity();
                Player p2 = (Player)e.getDamager();

                if(SurvivalGames.gracePeriod == false){
                    if(!PARTICIPANTS.contains(p) || !PARTICIPANTS.contains(p2)){
                        e.setCancelled(true);
                    } else {
                        SurvivalUser.get(p).lastDamager = p2;
                    }
                } else {
                    e.setCancelled(true);
                }
            } else if(e.getEntity() instanceof Player && e.getDamager() instanceof Arrow){
                Player p = (Player)e.getEntity();

                if(((Arrow)e.getDamager()).getShooter() instanceof Player){
                    Player p2 = (Player)((Arrow)e.getDamager()).getShooter();

                    if(SurvivalGames.gracePeriod == false){
                        if(!PARTICIPANTS.contains(p) || !PARTICIPANTS.contains(p2)){
                            e.setCancelled(true);
                        } else {
                            SurvivalUser.get(p).lastDamager = p2;
                        }
                    } else {
                        e.setCancelled(true);
                    }
                }
            } /*else if(e.getEntity() instanceof ArmorStand){
                Player damager = null;
                Player p = null;

                if(e.getDamager() instanceof Player){
                    damager = (Player)e.getDamager();
                } else if(e.getDamager() instanceof Arrow){
                    if(((Arrow)e.getDamager()) instanceof Player){
                        damager = (((Player)(Arrow)e.getDamager()));
                    }
                }

                if(damager != null){
                    for(Player all : Bukkit.getOnlinePlayers()){
                        if(ChestUser.getUser(all).fameTitlePlate.getEntityId() == e.getEntity().getEntityId()){
                            p = all;
                        }
                    }

                    if(p != null){
                        e.setCancelled(true);
                        if(PARTICIPANTS.contains(p) && PARTICIPANTS.contains(damager)){
                            p.damage(e.getDamage(),damager);
                        }
                    }
                }
            }*/
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e){
        Player p = e.getEntity();
        e.setDeathMessage(null);

        p.setHealth(p.getMaxHealth());
        SurvivalGames.getInstance().death(p);

        if(p.getKiller() != null){
            if(ChestUser.getUser(p.getKiller()).hasGamePerk(1)){
                if(e.getDrops().size() > 0){
                    Location loc = p.getLocation();
                    Inventory inv = Bukkit.createInventory(null,9*5,"Death Chest");

                    for(ItemStack i : e.getDrops()) inv.addItem(i);

                    loc.getBlock().setType(Material.REDSTONE_BLOCK);
                    loc = loc.getBlock().getLocation();

                    if(CHESTS.containsKey(loc)) CHESTS.remove(loc);
                    if(PERMANENT_CHESTS.containsKey(loc)) PERMANENT_CHESTS.remove(loc);

                    PERMANENT_CHESTS.put(loc,inv);
                    e.getDrops().clear();
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e){
        Player p = e.getPlayer();

        if(SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.GRACE_PERIOD || SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.INGAME || SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.SHOWDOWN){
            if(SurvivalGames.PARTICIPANTS.contains(p)){
                if(e.getBlock().getType() == Material.TNT){
                    e.getBlock().setType(Material.AIR);
                    TNTPrimed tnt = (TNTPrimed)e.getBlock().getLocation().getWorld().spawnEntity(e.getBlock().getLocation(), EntityType.PRIMED_TNT);
                    tnt.setFuseTicks(30);
                } else {
                    if(e.getBlock().getType() != Material.CAKE_BLOCK && e.getBlock().getType() != Material.CAKE){
                        e.setCancelled(true);
                    }
                }
            } else {
                e.setCancelled(true);
            }
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e){
        e.setYield(0);
        e.blockList().clear();
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent e){
        if(e.getEntity() instanceof Player){
            if(SurvivalGames.CURRENT_PHASE != SurvivalGamesPhase.INGAME && SurvivalGames.CURRENT_PHASE != SurvivalGamesPhase.SHOWDOWN && SurvivalGames.CURRENT_PHASE != SurvivalGamesPhase.GRACE_PERIOD){
                e.setCancelled(true);
                ((Player)e.getEntity()).setFoodLevel(20);
            }
        }
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e){
        if(e.getEntity().getType() != EntityType.ARMOR_STAND && e.getEntity().getType() != EntityType.FIREWORK){
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e){
        Player p = e.getPlayer();
        SurvivalUser s = SurvivalUser.get(p);
        ChestUser u = ChestUser.getUser(p);
        e.setQuitMessage(null);

        SurvivalUser.unregister(e.getPlayer());

        if(SurvivalGames.CURRENT_PHASE == SurvivalGamesPhase.VOTE_MAP){
            // moved to ChestAPI
        } else {
            if(SurvivalGames.CURRENT_PHASE != SurvivalGamesPhase.ENDING){
                if(SurvivalGames.PARTICIPANTS.contains(p)){
                    SurvivalGames.getInstance().death(p,false);
                }
            }
        }

        if(SurvivalGames.SPECTATORS.contains(p)) SurvivalGames.SPECTATORS.remove(p);
    }
}
