package main.java.PenguinT;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomCommands extends JavaPlugin implements Listener, CommandExecutor
{
    public static final String DbName = "CustomCommands.dat";
    public static final String GlobalTableName = "GLOBAL_COMMAND_ALIAS_TABLE";

    private String m_dbUrl = null;
    private Connection m_db = null;

    private final Logger m_log = getLogger();
    private final Server m_srv = getServer();
    private final PluginManager m_pm = m_srv.getPluginManager();

    private final Pattern m_aliasRegex = Pattern.compile("/?alias \"(.*)\" as \"(.*)\"");
    private final Pattern m_unaliasRegex = Pattern.compile("/?alias \"(.*)\"");

    @Override
    public void onEnable()
    {
        if (m_dbUrl == null)
        {
            Path dbDir = Paths.get(getDataFolder().getAbsolutePath());
            if (!Files.isDirectory(dbDir))
            {
                m_log.warning("CustomCommands plugin folder does not exist, creating");
                try
                {
                    Files.createDirectory(dbDir);
                }
                catch (Throwable ex)
                {
                    m_log.severe("Could not create CustomCommands plugin folder.");
                    m_log.severe(ex.toString());
                    return;
                }
            }

            Path dbPath = Paths.get(dbDir.toAbsolutePath().toString(), DbName);
            if (!Files.exists(dbPath))
            {
                m_log.warning("CustomCommands database does not exist, creating a new one: "+dbPath.toString());
                try
                {
                    Files.createFile(dbPath);
                }
                catch (Throwable ex)
                {
                    m_log.severe("Could not create CustomCommands database.");
                    m_log.severe(ex.toString());
                    return;
                }
            }
            m_dbUrl = "jdbc:sqlite:" + dbPath;
        }

        try
        {
            openConnection();
        }
        catch (Throwable ex)
        {
            m_log.severe("Could not open database connection.");
            m_log.severe(ex.toString());
            return;
        }

        m_pm.registerEvents(this, this);
        getCommand("alias").setExecutor(this);
        getCommand("unalias").setExecutor(this);
    }

    @Override
    public void onDisable()
    {
        try
        {
            closeConnection();
        }
        catch (Throwable ex)
        {
            m_log.severe("Could not close database connection.");
            m_log.severe(ex.toString());
        }

        HandlerList.unregisterAll((Listener)this);
    }

    private void createPlayerTableIfNeeded(String p_name) throws SQLException
    {
        PreparedStatement stmt = m_db.prepareStatement("CREATE TABLE IF NOT EXISTS \"" + p_name + "\" (Alias TEXT PRIMARY KEY, Command TEXT)");
        stmt.executeUpdate();
    }

    private void closeConnection() throws SQLException
    {
        if (m_db == null || m_db.isClosed())
        {
            return;
        }

        synchronized (this)
        {
            if (m_db != null && !m_db.isClosed())
            {
                m_db.close();
            }
        }
    }

    private void openConnection() throws SQLException, ClassNotFoundException
    {
        if (m_db != null && !m_db.isClosed())
        {
            return;
        }

        synchronized (this)
        {
            if (m_db != null && !m_db.isClosed())
            {
                return;
            }

            Class jdbc = Class.forName("com.mysql.jdbc.Driver");
            m_db = DriverManager.getConnection(m_dbUrl);
        }
    }


    private boolean aliasCommand(CommandSender p_sender, String p_alias, String p_cmd)
    {
        String playerOrAll = (p_sender instanceof Player) ? p_sender.getName() : GlobalTableName;

        try
        {
            createPlayerTableIfNeeded(playerOrAll);

            PreparedStatement stmt = m_db.prepareStatement("SELECT * FROM " + playerOrAll + " WHERE Alias = " + p_alias);
            ResultSet res = stmt.executeQuery();

            // Make sure they don't already have an alias for that command.
            if (res.next())
            {
                // Already aliased.
                p_sender.sendMessage("[CustomCommands] Error: That alias is already set to:");
                p_sender.sendMessage(res.getString("Command"));
                return false;
            }

            stmt = m_db.prepareStatement("INSERT INTO " + playerOrAll + " (Alias, Command) VALUES (" + p_alias + ", " + p_cmd+")");
            if (!stmt.execute())
            {
                p_sender.sendMessage("[CustomCommands] Error creating a new alias.");
                return false;
            }

            return true;
        }
        catch (Throwable ex)
        {
            p_sender.sendMessage("[CustomCommands] " + ex.getClass().getName() + " occurred creating that alias.");
            m_log.warning("Error when contacting database.");
            m_log.warning("Sender: "+p_sender.getName());
            m_log.warning("Alias: "+p_alias);
            m_log.warning("Command: "+p_cmd);
            m_log.warning(ex.toString());
        }

        return false;
    }

    private boolean unaliasCommand(CommandSender p_sender, String p_alias)
    {
        String playerOrAll = (p_sender instanceof Player) ? p_sender.getName() : GlobalTableName;

        try
        {
            createPlayerTableIfNeeded(playerOrAll);

            PreparedStatement stmt = m_db.prepareStatement("SELECT * FROM " + playerOrAll + " WHERE Alias = " + p_alias);
            ResultSet res = stmt.executeQuery();

            // Check if the alias exists.
            if (res.next())
            {
                stmt = m_db.prepareStatement("DELETE FROM " + playerOrAll + " WHERE Alias =  " + p_alias);
                if (!stmt.execute())
                {
                    p_sender.sendMessage("[CustomCommands] Error deleting an alias.");
                    return false;
                }
            }
            else
            {
                // No alias.
                p_sender.sendMessage("[CustomCommands] Error: That alias isn't set to anything.");
                return false;
            }

            return true;
        }
        catch (Throwable ex)
        {
            p_sender.sendMessage("[CustomCommands] " + ex.getClass().getName() + " occurred deleting that alias.");
            m_log.warning("Error when contacting database.");
            m_log.warning("Sender: "+p_sender.getName());
            m_log.warning("Alias: "+p_alias);
            m_log.warning(ex.toString());
        }

        return false;
    }

    @Override
    public boolean onCommand(CommandSender p_sender, Command p_command, String p_label, String[] p_args)
    {
        String fullCmd = p_label + " " + Utility.Join(p_args, " ");
        Matcher aliasMatcher = m_aliasRegex.matcher(fullCmd);
        Matcher unaliasMatcher = m_unaliasRegex.matcher(fullCmd);

        if (aliasMatcher.matches())
        {
            String alias = aliasMatcher.group(1);
            String command = aliasMatcher.group(2);
            return aliasCommand(p_sender, alias, command);
        }

        if (unaliasMatcher.matches())
        {
            String alias = unaliasMatcher.group(1);
            return unaliasCommand(p_sender, alias);
        }

        return false;
    }

    @EventHandler
    public void onPlayerCommandPreprocessEvent(PlayerCommandPreprocessEvent p_event)
    {
        Player sender = p_event.getPlayer();

        String[] tokens = p_event.getMessage().split(" ");
        if (tokens[0].charAt(0) == '/')
        {
            tokens[0] = tokens[0].substring(1);
        }

        String alias = tokens[0];

        try
        {
            createPlayerTableIfNeeded(sender.getName());

            // First search this player's aliases.
            PreparedStatement stmt = m_db.prepareStatement("SELECT * FROM " + sender.getName() + " WHERE Alias = " + alias);
            ResultSet res = stmt.executeQuery();
            if (res.next())
            {
                // A match was found.
                String commandFormat = res.getString("Command");
                String command = commandFormat.replaceAll("[^{]*\\{(\\d+)\\}[^}]*", tokens[Integer.parseInt("$1")]);
                command = command.replace("{{", "{");
                command = command.replace("}}", "}");

                sender.performCommand(command);
                p_event.setCancelled(true);
                return;
            }

            createPlayerTableIfNeeded(GlobalTableName);

            // Next try the global aliases.
            stmt = m_db.prepareStatement("SELECT * FROM " + GlobalTableName + " WHERE Alias = " + alias);
            res = stmt.executeQuery();
            if (res.next())
            {
                // A match was found.
                String commandFormat = res.getString("Command");
                String command = commandFormat.replaceAll("[^{]*\\{(\\d+)\\}[^}]*", tokens[Integer.parseInt("$1")]);
                command = command.replace("{{", "{");
                command = command.replace("}}", "}");

                sender.performCommand(command);
                p_event.setCancelled(true);
                return;
            }

            // Otherwise let subsequent handlers take care of the command.
            return;
        }
        catch (Throwable ex)
        {
            sender.sendMessage("[CustomCommands] " + ex.getClass().getName() + " occurred handling that command.");
            m_log.warning("Error when contacting database.");
            m_log.warning("Sender: "+sender.getName());
            m_log.warning("Command: "+p_event.getMessage());
            m_log.warning(ex.toString());
        }
    }

    @EventHandler
    public void onServerCommandEvent(ServerCommandEvent p_event)
    {
        CommandSender sender = p_event.getSender();

        String[] tokens = p_event.getCommand().split(" ");
        if (tokens[0].charAt(0) == '/')
        {
            tokens[0] = tokens[0].substring(1);
        }

        String alias = tokens[0];

        try
        {
            createPlayerTableIfNeeded(GlobalTableName);

            // Try the global aliases.
            PreparedStatement stmt = m_db.prepareStatement("SELECT * FROM " + GlobalTableName + " WHERE Alias = " + alias);
            ResultSet res = stmt.executeQuery();
            if (res.next())
            {
                // A match was found.
                String commandFormat = res.getString("Command");
                String command = commandFormat.replaceAll("[^{]*\\{(\\d+)\\}[^}]*", tokens[Integer.parseInt("$1")]);
                command = command.replace("{{", "{");
                command = command.replace("}}", "}");

                m_srv.dispatchCommand(sender, command);
                p_event.setCancelled(true);
                return;
            }

            // Otherwise let subsequent handlers take care of the command.
            return;
        }
        catch (Throwable ex)
        {
            p_event.getSender().sendMessage("[CustomCommands] " + ex.getClass().getName() + " occurred handling that command.");
            m_log.warning("Error when contacting database.");
            m_log.warning("Sender: "+sender.getName());
            m_log.warning("Command: "+p_event.getCommand());
            m_log.warning(ex.toString());
        }
    }
}
