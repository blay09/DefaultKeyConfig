package net.blay09.mods.defaultkeys;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod(modid = DefaultKeys.MODID)
public class DefaultKeys {

    public static final String MODID = "defaultkeys";

    public static final Logger logger = LogManager.getLogger();

    @Mod.Instance
    public static DefaultKeys instance;

    private static boolean initialized;
    private static Map<String, Integer> defaultKeys = new HashMap<>();
    private static List<String> knownKeys = new ArrayList<>();

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new CommandDefaultKeys());
        ClientCommandHandler.instance.registerCommand(new CommandDefaultOptions());
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SuppressWarnings("unused")
    public static void preStartGame() {
        File optionsFile = new File(Minecraft.getMinecraft().mcDataDir, "options.txt");
        if (!optionsFile.exists()) {
            applyDefaultOptions();
        }
        if (FMLClientHandler.instance().hasOptifine()) {
            File optionsFileOF = new File(Minecraft.getMinecraft().mcDataDir, "optionsof.txt");
            if (!optionsFileOF.exists()) {
                applyDefaultOptionsOptiFine();
            }
        }
        File localConfigDefs = new File(Minecraft.getMinecraft().mcDataDir, "config/localconfig.txt");
        if(!localConfigDefs.exists()) {
            try(PrintWriter writer = new PrintWriter(localConfigDefs)) {
                writer.println("# In this file, modpack creators can define config options that should NOT get overriden by modpack updates.");
                writer.println("# The values for these options will be restored to what they were before the pack update.");
                writer.println("# The format is the following: FILE/CATEGORY.TYPE:NAME");
                writer.println("# If the config file is inside a sub-directory, encase the path inside square brackets, ex. [eirairc/shared.cfg]");
                writer.println("# Categories and sub-categories are split by periods, ex. general.subcategory");
                writer.println("# The type is a single-character just like Forge's configuration type prefix: B, I, S, D; for lists, append <> to the type character");
                writer.println("# Full Example #1: trashslot.cfg/general.I:trashSlotX");
                writer.println("# Full Example #2: [eirairc/client.cfg]/notifications.D:notificationSoundVolume");
                writer.println();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        File modpackUpdate = new File(Minecraft.getMinecraft().mcDataDir, "config/modpack-update");
        if (modpackUpdate.exists()) {
            if (restoreLocalConfig()) {
                if (!modpackUpdate.delete()) {
                    logger.error("Could not delete modpack-update file. Delete manually or configs will keep restoring to this point.");
                }
            }
        } else {
            backupLocalConfig();
        }
    }

    public static boolean backupLocalConfig() {
        logger.info("Backing up local config values...");
        File mcDataDir = Minecraft.getMinecraft().mcDataDir;
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(mcDataDir, "config/localconfig.txt")));
             PrintWriter writer = new PrintWriter(new File(mcDataDir, "localconfig.cfg"))) {
            writer.println("# This file is automatically generated on each startup.");
            writer.println("# You don't want to change anything in here as it'll get overriden anyways.");
            writer.println("# Just leave this file be and go edit the respective config files instead.");
            writer.println();
            List<LocalConfigEntry> entries = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                LocalConfigEntry entry = LocalConfigEntry.fromString(line, false);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            String currentConfigName = null;
            Configuration currentConfig = null;
            for (LocalConfigEntry entry : entries) {
                if (!entry.name.equals(currentConfigName)) {
                    File configFile = new File(mcDataDir, "config/" + entry.file);
                    if (!configFile.exists()) {
                        logger.error("Skipping entry for {}: file at {} not found", entry.getIdentifier(), configFile);
                        continue;
                    }
                    currentConfigName = entry.name;
                    currentConfig = new Configuration(configFile);
                }
                if (currentConfig.hasCategory(entry.path)) {
                    ConfigCategory category = currentConfig.getCategory(entry.path);
                    Property property = category.get(entry.name);
                    if (property != null) {
                        if (entry.type.charAt(0) == property.getType().getID() && property.isList() == entry.type.endsWith("<>")) {
                            if (property.isList()) {
                                writer.println(entry.getIdentifier() + "=" + StringUtils.join(property.getStringList(), ", "));
                            } else {
                                writer.println(entry.getIdentifier() + "=" + property.getString());
                            }
                        } else {
                            logger.error("Skipping entry for {}: type mismatch (found {})", entry.getIdentifier(), property.getType().getID());
                        }
                    } else {
                        logger.error("Skipping entry for {}: property not found", entry.getIdentifier());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean restoreLocalConfig() {
        logger.info("Restoring local config values...");
        File mcDataDir = Minecraft.getMinecraft().mcDataDir;
        try (BufferedReader defReader = new BufferedReader(new FileReader(new File(mcDataDir, "config/localconfig.txt")));
             BufferedReader valReader = new BufferedReader(new FileReader(new File(mcDataDir, "localconfig.cfg")))) {
            Map<String, LocalConfigEntry> localConfig = new HashMap<>();
            String line;
            while ((line = defReader.readLine()) != null) {
                LocalConfigEntry entry = LocalConfigEntry.fromString(line, false);
                if (entry != null) {
                    localConfig.put(entry.getIdentifier(), null);
                }
            }
            while ((line = valReader.readLine()) != null) {
                LocalConfigEntry entry = LocalConfigEntry.fromString(line, true);
                if (entry != null && localConfig.containsKey(entry.getIdentifier())) {
                    localConfig.put(entry.getIdentifier(), entry);
                }
            }
            ArrayListMultimap<String, LocalConfigEntry> fileEntries = ArrayListMultimap.create();
            for (Map.Entry<String, LocalConfigEntry> entry : localConfig.entrySet()) {
                LocalConfigEntry configEntry = entry.getValue();
                fileEntries.put(configEntry.file, configEntry);
            }
            for (String key : fileEntries.keySet()) {
                List<LocalConfigEntry> list = fileEntries.get(key);
                LocalConfigEntry first = list.get(0);
                File configFile = new File(mcDataDir, "config/" + key);
                if (!configFile.exists()) {
                    logger.error("Skipping entry for {}: file at {} not found", first.getIdentifier(), configFile);
                    continue;
                }
                ForgeConfigHandler.restore(list, configFile);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public static boolean applyDefaultOptions() {
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(Minecraft.getMinecraft().mcDataDir, "config/defaultoptions.txt")));
             PrintWriter writer = new PrintWriter(new FileWriter(new File(Minecraft.getMinecraft().mcDataDir, "options.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("key_")) {
                    continue;
                }
                writer.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean applyDefaultOptionsOptiFine() {
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(Minecraft.getMinecraft().mcDataDir, "config/defaultoptionsof.txt")));
             PrintWriter writer = new PrintWriter(new FileWriter(new File(Minecraft.getMinecraft().mcDataDir, "optionsof.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void finishMinecraftLoading(GuiOpenEvent event) {
        if (!initialized && event.gui instanceof GuiMainMenu) {
            reloadDefaultMappings();
            initialized = true;
        }
    }

    public boolean saveDefaultOptionsOptiFine() {
        if (!FMLClientHandler.instance().hasOptifine()) {
            return true;
        }
        Minecraft.getMinecraft().gameSettings.saveOptions();
        try (PrintWriter writer = new PrintWriter(new FileWriter(new File(Minecraft.getMinecraft().mcDataDir, "config/defaultoptionsof.txt")));
             BufferedReader reader = new BufferedReader(new FileReader(new File(Minecraft.getMinecraft().mcDataDir, "optionsof.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean saveDefaultOptions() {
        Minecraft.getMinecraft().gameSettings.saveOptions();
        try (PrintWriter writer = new PrintWriter(new FileWriter(new File(Minecraft.getMinecraft().mcDataDir, "config/defaultoptions.txt")));
             BufferedReader reader = new BufferedReader(new FileReader(new File(Minecraft.getMinecraft().mcDataDir, "options.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("key_")) {
                    continue;
                }
                writer.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean saveDefaultMappings() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(new File(Minecraft.getMinecraft().mcDataDir, "config/defaultkeys.txt")))) {
            for (KeyBinding keyBinding : Minecraft.getMinecraft().gameSettings.keyBindings) {
                writer.println("key_" + keyBinding.getKeyDescription() + ":" + keyBinding.getKeyCode());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void reloadDefaultMappings() {
        // Clear old values
        defaultKeys.clear();
        knownKeys.clear();

        // Load the default keys from the config
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(Minecraft.getMinecraft().mcDataDir, "config/defaultkeys.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] s = line.split(":");
                if (s.length != 2 || !s[0].startsWith("key_")) {
                    continue;
                }
                try {
                    defaultKeys.put(s[0].substring(4), Integer.parseInt(s[1]));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

        // Load the known keys from the Minecraft directory
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(Minecraft.getMinecraft().mcDataDir, "knownkeys.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    knownKeys.add(line);
                }
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

        // Override the default mappings and set the initial key codes, if the key is not known yet
        for (KeyBinding keyBinding : Minecraft.getMinecraft().gameSettings.keyBindings) {
            if (defaultKeys.containsKey(keyBinding.getKeyDescription())) {
                keyBinding.keyCodeDefault = defaultKeys.get(keyBinding.getKeyDescription());
                if (!knownKeys.contains(keyBinding.getKeyDescription())) {
                    keyBinding.setKeyCode(keyBinding.getKeyCodeDefault());
                    knownKeys.add(keyBinding.getKeyDescription());
                }
            }
        }

        // Save the updated known keys to the knownkeys.txt file in the Minecraft directory
        try (PrintWriter writer = new PrintWriter(new FileWriter(new File(Minecraft.getMinecraft().mcDataDir, "knownkeys.txt")))) {
            for (String s : knownKeys) {
                writer.println(s);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
