package eu.thechest.survivalgames.cmd;

import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.user.Rank;
import eu.thechest.chestapi.util.PlayerUtilities;
import eu.thechest.chestapi.util.StringUtils;
import eu.thechest.survivalgames.SurvivalGames;
import eu.thechest.survivalgames.SurvivalGamesPhase;
import eu.thechest.survivalgames.user.SurvivalUser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Created by zeryt on 27.02.2017.
 */
public class MainExecutor implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        return false;
    }
}
