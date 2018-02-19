package eu.thechest.survivalgames.user;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;

/**
 * Created by zeryt on 27.02.2017.
 */
public class SurvivalUser {
    public static HashMap<Player,SurvivalUser> STORAGE = new HashMap<Player,SurvivalUser>();

    public static SurvivalUser get(Player p){
        if(STORAGE.containsKey(p)){
            return STORAGE.get(p);
        } else {
            new SurvivalUser(p);

            if(STORAGE.containsKey(p)){
                return STORAGE.get(p);
            } else {
                return null;
            }
        }
    }

    public static void unregister(Player p){
        if(STORAGE.containsKey(p)){
            STORAGE.get(p).saveData();
            STORAGE.remove(p);
        }
    }

    private Player p;

    private int startPoints;
    private int points;
    private int startKills;
    private int kills;
    private int startDeaths;
    private int deaths;
    private int startPlayedGames;
    private int playedGames;
    private int startVictories;
    private int victories;
    private int startShowdowns;
    private int showdowns;
    private Timestamp lastGame;

    public boolean allowSaveData = true;

    public Player lastDamager;

    public SurvivalUser(Player p){
        this.p = p;

        try {
            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `sg_stats` WHERE `uuid` = ?");
            ps.setString(1,p.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            if(rs.first()){
                startPoints = rs.getInt("points");
                points = 0;
                startKills = rs.getInt("kills");
                kills = 0;
                startDeaths = rs.getInt("deaths");
                deaths = 0;
                startPlayedGames = rs.getInt("playedGames");
                playedGames = 0;
                startVictories = rs.getInt("victories");
                victories = 0;
                startShowdowns = rs.getInt("showdowns");
                showdowns = 0;

                lastGame = new Timestamp(System.currentTimeMillis());

                STORAGE.put(p,this);
            } else {
                PreparedStatement insert = MySQLManager.getInstance().getConnection().prepareStatement("INSERT INTO `sg_stats` (`uuid`) VALUES(?)");
                insert.setString(1,p.getUniqueId().toString());
                insert.execute();
                insert.close();

                new SurvivalUser(p);
            }

            MySQLManager.getInstance().closeResources(rs,ps);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public Player getPlayer(){
        return this.p;
    }

    public ChestUser getUser(){
        return ChestUser.getUser(p);
    }

    public void addPoints(int points){
        for(int i = 0; i < points; i++){
            //if((startPoints+this.points+i)<=0) break;

            this.points++;
        }

        //p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + getUser().getTranslatedMessage("You now have %p points.").replace("%p",ChatColor.YELLOW.toString() + getPoints() + ChatColor.GREEN));
    }

    public void reducePoints(int points){
        for(int i = 0; i < points; i++){
            if((startPoints+this.points+(i/-1))<=0) break;

            this.points--;
        }

        //p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + getUser().getTranslatedMessage("You now have %p points.").replace("%p",ChatColor.YELLOW.toString() + getPoints() + ChatColor.RED));
    }

    public int getPoints(){
        return this.startPoints + this.points;
    }

    public int getDeaths(){
        return this.startDeaths+this.deaths;
    }

    public void addDeaths(int i){
        this.deaths += i;
    }

    public int getKills(){
        return this.startKills+this.kills;
    }

    public int getCurrentKills(){
        return this.kills;
    }

    public void addKills(int i){
        this.kills += i;
    }

    public int getPlayedGames(){
        return this.startPlayedGames+this.playedGames;
    }

    public void addPlayedGames(int i){
        this.playedGames += i;
    }

    public int getVictories(){
        return this.startVictories+this.victories;
    }

    public void addVictories(int i){
        this.victories += i;
    }

    public int getShowdowns(){
        return this.startShowdowns+this.showdowns;
    }

    public void addShowdowns(int i){
        this.showdowns += i;
    }

    public void saveData(){
        if(allowSaveData == false) return;

        ChestAPI.async(() -> {
            try {
                PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("UPDATE `sg_stats` SET `points`=`points`+?, `monthlyPoints`=`monthlyPoints`+?, `kills`=`kills`+?, `deaths`=`deaths`+?, `lastGame`=?, `playedGames`=`playedGames`+?, `victories`=`victories`+?, `showdowns`=`showdowns`+? WHERE `uuid`=?");
                ps.setInt(1,this.points);
                ps.setInt(2,this.points);
                ps.setInt(3,this.kills);
                ps.setInt(4,this.deaths);
                ps.setTimestamp(5,this.lastGame);
                ps.setInt(6,this.playedGames);
                ps.setInt(7,this.victories);
                ps.setInt(8,this.showdowns);
                ps.setString(9,p.getUniqueId().toString());
                ps.executeUpdate();
                ps.close();
            } catch (Exception e){
                e.printStackTrace();
            }
        });
    }
}
