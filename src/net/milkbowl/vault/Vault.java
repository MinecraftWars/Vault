/* This file is part of Vault.

    Vault is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Vault is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with Vault.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.milkbowl.vault;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collection;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.chat.plugins.Chat_DroxPerms;
import net.milkbowl.vault.chat.plugins.Chat_GroupManager;
import net.milkbowl.vault.chat.plugins.Chat_Permissions3;
import net.milkbowl.vault.chat.plugins.Chat_PermissionsEx;
import net.milkbowl.vault.chat.plugins.Chat_bPermissions;
import net.milkbowl.vault.chat.plugins.Chat_bPermissions2;
import net.milkbowl.vault.chat.plugins.Chat_iChat;
import net.milkbowl.vault.chat.plugins.Chat_mChat;
import net.milkbowl.vault.chat.plugins.Chat_mChatSuite;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.plugins.Economy_3co;
import net.milkbowl.vault.economy.plugins.Economy_AEco;
import net.milkbowl.vault.economy.plugins.Economy_BOSE6;
import net.milkbowl.vault.economy.plugins.Economy_BOSE7;
import net.milkbowl.vault.economy.plugins.Economy_Craftconomy;
import net.milkbowl.vault.economy.plugins.Economy_CurrencyCore;
import net.milkbowl.vault.economy.plugins.Economy_EconXP;
import net.milkbowl.vault.economy.plugins.Economy_Essentials;
import net.milkbowl.vault.economy.plugins.Economy_McMoney;
import net.milkbowl.vault.economy.plugins.Economy_MineConomy;
import net.milkbowl.vault.economy.plugins.Economy_MultiCurrency;
import net.milkbowl.vault.economy.plugins.Economy_eWallet;
import net.milkbowl.vault.economy.plugins.Economy_iConomy4;
import net.milkbowl.vault.economy.plugins.Economy_iConomy5;
import net.milkbowl.vault.economy.plugins.Economy_iConomy6;
import net.milkbowl.vault.permission.Permission;
import net.milkbowl.vault.permission.plugins.Permission_DroxPerms;
import net.milkbowl.vault.permission.plugins.Permission_GroupManager;
import net.milkbowl.vault.permission.plugins.Permission_Permissions3;
import net.milkbowl.vault.permission.plugins.Permission_PermissionsBukkit;
import net.milkbowl.vault.permission.plugins.Permission_PermissionsEx;
import net.milkbowl.vault.permission.plugins.Permission_Privileges;
import net.milkbowl.vault.permission.plugins.Permission_SimplyPerms;
import net.milkbowl.vault.permission.plugins.Permission_Starburst;
import net.milkbowl.vault.permission.plugins.Permission_SuperPerms;
import net.milkbowl.vault.permission.plugins.Permission_bPermissions;
import net.milkbowl.vault.permission.plugins.Permission_bPermissions2;
import net.milkbowl.vault.permission.plugins.Permission_zPermissions;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.nijikokun.register.payment.Methods;

public class Vault extends JavaPlugin {

    private static final Logger log = Logger.getLogger("Minecraft");
    private Permission perms;
    private double newVersion;
    private double currentVersion;
    private ServicesManager sm;
    private Metrics metrics;

    @Override
    public void onDisable() {
        // Remove all Service Registrations
        getServer().getServicesManager().unregisterAll(this);

        log.info(String.format("[%s] Disabled Version %s", getDescription().getName(), getDescription().getVersion()));
    }

    @Override
    public void onEnable() {
        currentVersion = Double.valueOf(getDescription().getVersion().split("-")[0].replaceFirst("\\.", ""));
        sm = getServer().getServicesManager();
        // Load Vault Addons
        loadEconomy();
        loadPermission();
        loadChat();

        getCommand("vault-info").setExecutor(this);
        getCommand("vault-convert").setExecutor(this);
        getServer().getPluginManager().registerEvents(new VaultListener(), this);

        // Schedule to check the version every 30 minutes for an update. This is to update the most recent 
        // version so if an admin reconnects they will be warned about newer versions.
        this.getServer().getScheduler().scheduleAsyncRepeatingTask(this, new Runnable() {

            @Override
            public void run() {
                try {
                    newVersion = updateCheck(currentVersion);
                    if (newVersion > currentVersion) {
                        log.warning("Vault " + newVersion + " is out! You are running: Vault " + currentVersion);
                        log.warning("Update Vault at: http://dev.bukkit.org/server-mods/vault");
                    }
                } catch (Exception e) {
                    // ignore exceptions
                }
            }

        }, 0, 432000);

        // Load up the Plugin metrics
        try {
            String authors = "";
            for (String author : this.getDescription().getAuthors()) {
                authors += author + ", ";
            }
            if (!authors.isEmpty()) {
                authors = authors.substring(0, authors.length() - 2);
            }
            metrics = new Metrics(getDescription().getVersion(), authors);
            metrics.findCustomData(this);
            metrics.beginMeasuringPlugin(this);
        } catch (IOException e) {
            // ignore exception
        }
        log.info(String.format("[%s] Enabled Version %s", getDescription().getName(), getDescription().getVersion()));
    }

    /**
     * Attempts to load Chat Addons
     */
    private void loadChat() {
    	// Try to load PermissionsEx
    	hookChat("PermissionsEx", new String[]{"ru.tehkode.permissions.bukkit.PermissionsEx"}, Chat_PermissionsEx.class, ServicePriority.Highest);

    	// Try to load mChatSuite
    	hookChat("mChatSuite", new String[]{"in.mDev.MiracleM4n.mChatSuite.mChatSuite"}, Chat_mChatSuite.class, ServicePriority.Highest);

    	// Try to load mChat
    	hookChat("mChat", new String[]{"net.D3GN.MiracleM4n.mChat"}, Chat_mChat.class, ServicePriority.Highest);

    	// Try to load DroxPerms Chat
    	hookChat("DroxPerms", new String[]{"de.hydrox.bukkit.DroxPerms.DroxPerms"}, Chat_DroxPerms.class, ServicePriority.Lowest);

    	// Try to load bPermssions 2
    	hookChat("bPermssions2", new String[]{"de.bananaco.bpermissions.api.ApiLayer"}, Chat_bPermissions2.class, ServicePriority.Highest);

    	// Try to load bPermissions 1
    	hookChat("bPermissions", new String[]{"de.bananaco.permissions.info.InfoReader"}, Chat_bPermissions.class, ServicePriority.Normal);

    	// Try to load GroupManager
    	hookChat("GroupManager", new String[]{"org.anjocaido.groupmanager.GroupManager"}, Chat_GroupManager.class, ServicePriority.Normal);

    	// Try to load Permissions 3 (Yeti)
    	hookChat("Permissions3", new String[]{"com.nijiko.permissions.ModularControl"}, Chat_Permissions3.class, ServicePriority.Normal);

    	// Try to load iChat
    	hookChat("iChat", new String[]{"net.TheDgtl.iChat.iChat"}, Chat_iChat.class, ServicePriority.Low);
    }

    /**
     * Attempts to load Economy Addons
     */
    private void loadEconomy() {
    	// Try to load MultiCurrency
    	hookEconomy("MultiCurrency", new String[]{"me.ashtheking.currency.Currency", "me.ashtheking.currency.CurrencyList"}, 
    			Economy_MultiCurrency.class, ServicePriority.Normal);

    	// Try to load MineConomy
    	hookEconomy("MineConomy", new String[]{"me.mjolnir.mineconomy.MineConomy"}, Economy_MineConomy.class, ServicePriority.Normal);

    	// Try to load AEco
    	hookEconomy("AEco", new String[]{"org.neocraft.AEco.AEco"}, Economy_AEco.class, ServicePriority.Normal);

    	// Try to load McMoney
    	hookEconomy("McMoney", new String[]{"boardinggamer.mcmoney.McMoneyAPI"}, Economy_McMoney.class, ServicePriority.Normal);

    	// Try to load Craftconomy
    	hookEconomy("CraftConomy", new String[]{"me.greatman.Craftconomy.Craftconomy"}, Economy_Craftconomy.class, ServicePriority.Normal);

    	// Try to load eWallet
    	hookEconomy("eWallet", new String[]{"me.ethan.eWallet.ECO"}, Economy_eWallet.class, ServicePriority.Normal);

    	// Try to load 3co
    	hookEconomy("3co", new String[]{"me.ic3d.eco.ECO"}, Economy_3co.class, ServicePriority.Normal);

    	// Try to load BOSEconomy 6
    	hookEconomy("BOSEconomy6", new String[]{"cosine.boseconomy.BOSEconomy", "cosine.boseconomy.CommandManager"}, 
    			Economy_BOSE6.class, ServicePriority.Normal);

    	// Try to load BOSEconomy 7
    	hookEconomy("BOSEconomy7", new String[]{"cosine.boseconomy.BOSEconomy", "cosine.boseconomy.CommandHandler"}, 
    			Economy_BOSE7.class, ServicePriority.Normal);

    	// Try to load CurrencyCore
    	hookEconomy("CurrencyCore", new String[]{"is.currency.Currency"}, Economy_CurrencyCore.class, ServicePriority.Normal);

    	// Try to load Essentials Economy
    	hookEconomy("Essentials Economy", new String[]{"com.earth2me.essentials.api.Economy", "com.earth2me.essentials.api.NoLoanPermittedException", 
    			"com.earth2me.essentials.api.UserDoesNotExistException"}, Economy_Essentials.class, ServicePriority.Low);

    	// Try to load iConomy 4
    	hookEconomy("iConomy 4", new String[]{"com.nijiko.coelho.iConomy.iConomy", "com.nijiko.coelho.iConomy.system.Account"}, 
    			Economy_iConomy4.class, ServicePriority.High);

    	// Try to load iConomy 5
    	hookEconomy("iConomy 5", new String[]{"com.iConomy.iConomy", "com.iConomy.system.Account", "com.iConomy.system.Holdings"}, 
    			Economy_iConomy5.class, ServicePriority.High);

    	// Try to load iConomy 6
    	hookEconomy("iConomy 6", new String[]{"com.iCo6.iConomy"}, Economy_iConomy6.class, ServicePriority.High);

    	// Try to load EconXP
    	hookEconomy("EconXP", new String[]{"ca.agnate.EconXP.EconXP"}, Economy_EconXP.class, ServicePriority.Normal);
    }

    /**
     * Attempts to load Permission Addons
     */
    private void loadPermission() {
    	// Try to load Starburst
    	hookPermission("Starburst", new String[]{"com.dthielke.starburst.StarburstPlugin"}, Permission_Starburst.class, ServicePriority.Highest);

    	// Try to load PermissionsEx
    	hookPermission("PermissionsEx", new String[]{"ru.tehkode.permissions.bukkit.PermissionsEx"}, Permission_PermissionsEx.class, ServicePriority.Highest);

    	// Try to load PermissionsBukkit
    	hookPermission("PermissionsBukkit", new String[]{"com.platymuus.bukkit.permissions.PermissionsPlugin"}, Permission_PermissionsBukkit.class, 
    			ServicePriority.Highest);

    	// Try to load DroxPerms
    	hookPermission("DroxPerms", new String[]{"de.hydrox.bukkit.DroxPerms.DroxPerms"}, Permission_DroxPerms.class, ServicePriority.High);

    	// Try to load SimplyPerms
    	hookPermission("SimplyPerms", new String[]{"net.crystalyx.bukkit.simplyperms.SimplyPlugin"}, Permission_SimplyPerms.class, ServicePriority.Highest);

    	// Try to load bPermissions2
    	hookPermission("bPermissions 2", new String[]{"de.bananaco.bpermissions.api.WorldManager"}, Permission_bPermissions2.class, ServicePriority.Highest);

    	// Try to load zPermission
    	hookPermission("zPermissions", new String[]{"org.tyrannyofheaven.bukkit.zPermissions.ZPermissionsPlugin"}, Permission_zPermissions.class, 
    			ServicePriority.Highest);

    	// Try to load Privileges
    	hookPermission("Privileges", new String[]{"net.krinsoft.privileges.Privileges"}, Permission_Privileges.class, ServicePriority.Highest);

    	// Try to load bPermissions
    	hookPermission("bPermissions", new String[]{"de.bananaco.permissions.SuperPermissionHandler"}, Permission_bPermissions.class, ServicePriority.High);

    	// Try to load GroupManager
    	hookPermission("GroupManager", new String[]{"org.anjocaido.groupmanager.GroupManager"}, Permission_GroupManager.class, ServicePriority.High);

    	// Try to load Permissions 3 (Yeti)
    	hookPermission("Permissions 3 (Yeti)", new String[]{"com.nijiko.permissions.ModularControl"}, Permission_Permissions3.class, ServicePriority.High);

        Permission perms = new Permission_SuperPerms(this);
        sm.register(Permission.class, perms, this, ServicePriority.Lowest);
        log.info(String.format("[%s][Permission] SuperPermissions loaded as backup permission system.", getDescription().getName()));

        this.perms = sm.getRegistration(Permission.class).getProvider();
    }
    
    private void hookChat (String name, String[] packageCheck, Class<? extends Chat> hookClass, ServicePriority priority) {
        try {
            if (packageExists(packageCheck)) {
                Chat chat = hookClass.getConstructor(Plugin.class, Permission.class).newInstance(this, perms);
                sm.register(Chat.class, chat, this, priority);
                log.info(String.format("[%s][Chat] %s found: %s", getDescription().getName(), name, chat.isEnabled() ? "Loaded" : "Waiting"));
            }
        } catch (Exception e) {
            log.severe(String.format("[%s][Chat] There was an error hooking %s - check to make sure you're using a compatible version!", 
            		getDescription().getName(), name));
        }
    }
    
    private void hookEconomy (String name, String[] packageCheck, Class<? extends Economy> hookClass, ServicePriority priority) {
        try {
            if (packageExists(packageCheck)) {
                Economy econ = hookClass.getConstructor(Plugin.class).newInstance(this);
                sm.register(Economy.class, econ, this, priority);
                log.info(String.format("[%s][Economy] %s found: %s", getDescription().getName(), name, econ.isEnabled() ? "Loaded" : "Waiting"));
            }
        } catch (Exception e) {
            log.severe(String.format("[%s][Economy] There was an error hooking %s - check to make sure you're using a compatible version!", 
            		getDescription().getName(), name));
        }
    }
    
    private void hookPermission (String name, String[] packageCheck, Class<? extends Permission> hookClass, ServicePriority priority) {
        try {
            if (packageExists(packageCheck)) {
                Permission perms = hookClass.getConstructor(Plugin.class).newInstance(this);
                sm.register(Permission.class, perms, this, priority);
                log.info(String.format("[%s][Permission] %s found: %s", getDescription().getName(), name, perms.isEnabled() ? "Loaded" : "Waiting"));
            }
        } catch (Exception e) {
            log.severe(String.format("[%s][Permission] There was an error hooking %s - check to make sure you're using a compatible version!", 
            		getDescription().getName(), name));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        if (sender instanceof Player) {
            // Check if Player
            // If so, ignore command if player is not Op
            Player p = (Player) sender;
            if (!p.isOp()) {
                return true;
            }
        }

        if (command.getName().equalsIgnoreCase("vault-info")) {
            infoCommand(sender);
            return true;
        } else if (command.getName().equalsIgnoreCase("vault-convert")) {
            convertCommand(sender, args);
            return true;
        } else {
            // Show help
            sender.sendMessage("Vault Commands:");
            sender.sendMessage("  /vault-info - Displays information about Vault");
            sender.sendMessage("  /vault-convert [economy1] [economy2] - Converts from one Economy to another");
            return true;
        }
    }

    private void convertCommand(CommandSender sender, String[] args) {
        Collection<RegisteredServiceProvider<Economy>> econs = this.getServer().getServicesManager().getRegistrations(Economy.class);
        if (econs == null || econs.size() < 2) {
            sender.sendMessage("You must have at least 2 economies loaded to convert.");
            return;
        } else if (args.length != 2) {
            sender.sendMessage("You must specify only the economy to convert from and the economy to convert to. (without spaces)");
            return;
        }
        Economy econ1 = null;
        Economy econ2 = null;
        for (RegisteredServiceProvider<Economy> econ : econs) {
            String econName = econ.getProvider().getName().replace(" ", "");
            if (econName.equalsIgnoreCase(args[0])) {
                econ1 = econ.getProvider();
            } else if (econName.equalsIgnoreCase(args[1])) {
                econ2 = econ.getProvider();
            }
        }

        if (econ1 == null) {
            sender.sendMessage("Could not find " + args[0] + " loaded on the server, check your spelling");
            return;
        } else if (econ2 == null) {
            sender.sendMessage("Could not find " + args[1] + " loaded on the server, check your spelling");
            return;
        }

        sender.sendMessage("This may take some time to convert, expect server lag.");
        for (OfflinePlayer op : Bukkit.getServer().getOfflinePlayers()) {
            String pName = op.getName();
            if (econ1.hasAccount(pName)) {
                if (econ2.hasAccount(pName)) {
                    continue;
                }
                econ2.createPlayerAccount(pName);
                econ2.depositPlayer(pName, econ1.getBalance(pName));
            }
        }
    }

    private void infoCommand(CommandSender sender) {
        // Get String of Registered Economy Services
        String registeredEcons = null;
        Collection<RegisteredServiceProvider<Economy>> econs = this.getServer().getServicesManager().getRegistrations(Economy.class);
        for (RegisteredServiceProvider<Economy> econ : econs) {
            Economy e = econ.getProvider();
            if (registeredEcons == null) {
                registeredEcons = e.getName();
            } else {
                registeredEcons += ", " + e.getName();
            }
        }

        // Get String of Registered Permission Services
        String registeredPerms = null;
        Collection<RegisteredServiceProvider<Permission>> perms = this.getServer().getServicesManager().getRegistrations(Permission.class);
        for (RegisteredServiceProvider<Permission> perm : perms) {
            Permission p = perm.getProvider();
            if (registeredPerms == null) {
                registeredPerms = p.getName();
            } else {
                registeredPerms += ", " + p.getName();
            }
        }

        String registeredChats = null;
        Collection<RegisteredServiceProvider<Chat>> chats = this.getServer().getServicesManager().getRegistrations(Chat.class);
        for (RegisteredServiceProvider<Chat> chat : chats) {
            Chat c = chat.getProvider();
            if (registeredChats == null) {
                registeredChats = c.getName();
            } else {
                registeredChats += ", " + c.getName();
            }
        }

        // Get Economy & Permission primary Services
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        Economy econ = null;
        if (rsp != null) {
            econ = rsp.getProvider();
        }
        Permission perm = null;
        RegisteredServiceProvider<Permission> rspp = getServer().getServicesManager().getRegistration(Permission.class);
        if (rspp != null) {
            perm = rspp.getProvider();
        }
        Chat chat = null;
        RegisteredServiceProvider<Chat> rspc = getServer().getServicesManager().getRegistration(Chat.class);
        if (rspc != null) {
            chat = rspc.getProvider();
        }
        // Send user some info!
        sender.sendMessage(String.format("[%s] Vault v%s Information", getDescription().getName(), getDescription().getVersion()));
        sender.sendMessage(String.format("[%s] Economy: %s [%s]", getDescription().getName(), econ == null ? "None" : econ.getName(), registeredEcons));
        sender.sendMessage(String.format("[%s] Permission: %s [%s]", getDescription().getName(), perm == null ? "None" : perm.getName(), registeredPerms));
        sender.sendMessage(String.format("[%s] Chat: %s [%s]", getDescription().getName(), chat == null ? "None" : chat.getName(), registeredChats));
    }

    /**
     * Determines if all packages in a String array are within the Classpath
     * This is the best way to determine if a specific plugin exists and will be
     * loaded. If the plugin package isn't loaded, we shouldn't bother waiting
     * for it!
     * @param packages String Array of package names to check
     * @return Success or Failure
     */
    private static boolean packageExists(String...packages) {
        try {
            for (String pkg : packages) {
                Class.forName(pkg);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public double updateCheck(double currentVersion) throws Exception {
        String pluginUrlString = "http://dev.bukkit.org/server-mods/vault/files.rss";
        try {
            URL url = new URL(pluginUrlString);
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(url.openConnection().getInputStream());
            doc.getDocumentElement().normalize();
            NodeList nodes = doc.getElementsByTagName("item");
            Node firstNode = nodes.item(0);
            if (firstNode.getNodeType() == 1) {
                Element firstElement = (Element)firstNode;
                NodeList firstElementTagName = firstElement.getElementsByTagName("title");
                Element firstNameElement = (Element) firstElementTagName.item(0);
                NodeList firstNodes = firstNameElement.getChildNodes();
                return Double.valueOf(firstNodes.item(0).getNodeValue().replace("Vault", "").replaceFirst(".", "").trim());
            }
        }
        catch (Exception localException) {
        }
        return currentVersion;
    }

    public class VaultListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            if (perms.has(player, "vault.admin")) {
                try {
                    if (newVersion > currentVersion) {
                        player.sendMessage(newVersion + " is out! You are running " + currentVersion);
                        player.sendMessage("Update Vault at: http://dev.bukkit.org/server-mods/vault");
                    }
                } catch (Exception e) {
                    // Ignore exceptions
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPluginEnable(PluginEnableEvent event) {
            if (event.getPlugin().getDescription().getName().equals("Register") && packageExists("com.nijikokun.register.payment.Methods")) {
                if (!Methods.hasMethod()) {
                    try {
                        Method m = Methods.class.getMethod("addMethod", Methods.class);
                        m.setAccessible(true);
                        m.invoke(null, "Vault", new net.milkbowl.vault.VaultEco());
                        if (!Methods.setPreferred("Vault")) {
                            log.info("Unable to hook register");
                        } else {
                            log.info("[Vault] - Successfully injected Vault methods into Register.");
                        }
                    } catch (SecurityException e) {
                        log.info("Unable to hook register");
                    } catch (NoSuchMethodException e) {
                        log.info("Unable to hook register");
                    } catch (IllegalArgumentException e) {
                        log.info("Unable to hook register");
                    } catch (IllegalAccessException e) {
                        log.info("Unable to hook register");
                    } catch (InvocationTargetException e) {
                        log.info("Unable to hook register");
                    }
                }
            }
        }
    }
}