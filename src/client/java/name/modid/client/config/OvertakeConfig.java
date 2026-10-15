package name.modid.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import name.modid.client.OvertakeClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OvertakeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("overtake.json");

    public boolean masterEnabled;
    public boolean debug;
    public String profile = "default";
    public Settings defaultSettings = new Settings();
    public Settings pvpSettings = Settings.pvp();
    public Settings practiceSettings = Settings.practice();

    public Settings settings() {
        return switch (profile) {
            case "pvp" -> pvpSettings;
            case "practice" -> practiceSettings;
            default -> defaultSettings;
        };
    }

    public static OvertakeConfig load() {
        if (!Files.exists(FILE)) {
            OvertakeConfig config = new OvertakeConfig();
            config.save();
            return config;
        }
        try (var reader = Files.newBufferedReader(FILE)) {
            OvertakeConfig config = GSON.fromJson(reader, OvertakeConfig.class);
            if (config == null) {
                throw new JsonParseException("Configuration is empty");
            }
            config.validate();
            return config;
        } catch (IOException | JsonParseException exception) {
            OvertakeClient.LOGGER.error("Could not read {}. Replacing it with safe defaults.", FILE, exception);
            OvertakeConfig config = new OvertakeConfig();
            config.save();
            return config;
        }
    }

    public boolean save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
            return true;
        } catch (IOException exception) {
            OvertakeClient.LOGGER.error("Could not save configuration to {}", FILE, exception);
            return false;
        }
    }

    public void validate() {
        if (profile == null || !List.of("default", "pvp", "practice").contains(profile)) {
            profile = "default";
        }
        if (defaultSettings == null) defaultSettings = new Settings();
        if (pvpSettings == null) pvpSettings = Settings.pvp();
        if (practiceSettings == null) practiceSettings = Settings.practice();
        defaultSettings.validate();
        pvpSettings.validate();
        practiceSettings.validate();
    }

    public static final class Settings {
        public boolean switchEnabled = true;
        public boolean maceEnabled = true;
        public boolean stunEnabled;
        public boolean restoreSlot = true;
        public boolean manualOverride = true;
        public boolean hudEnabled = true;
        public boolean soundAlerts;
        public int switchDelay = 1;
        public int maceDelay = 0;
        public int stunCooldown = 20;
        public int hudX = 8;
        public int hudY = 8;
        public double maceThreshold = 2.0;
        public double maceRange = 4.5;
        public double meleeRange = 4.5;
        public double stunRange = 4.0;
        public double hudScale = 1.0;
        public int maceSlot;
        public int swordSlot;
        public int axeSlot;
        public String targetType = "living";
        public String maceMode = "manual";
        public boolean maceAutoAttack;
        public String stunMode = "manual";
        public List<String> weaponPriority = new ArrayList<>(List.of("mace", "sword", "axe"));

        private static Settings pvp() {
            Settings settings = new Settings();
            settings.maceMode = "assist";
            settings.weaponPriority = new ArrayList<>(List.of("mace", "axe", "sword"));
            return settings;
        }

        private static Settings practice() {
            Settings settings = new Settings();
            settings.maceMode = "assist";
            settings.soundAlerts = true;
            return settings;
        }

        public void validate() {
            switchDelay = clamp(switchDelay, 0, 100);
            maceDelay = clamp(maceDelay, 0, 100);
            stunCooldown = clamp(stunCooldown, 0, 12000);
            hudX = clamp(hudX, 0, 10000);
            hudY = clamp(hudY, 0, 10000);
            maceThreshold = clamp(maceThreshold, 2.0, 20.0);
            maceRange = clamp(maceRange, 1.0, 16.0);
            meleeRange = clamp(meleeRange, 1.0, 8.0);
            stunRange = clamp(stunRange, 1.0, 16.0);
            hudScale = clamp(hudScale, 0.5, 3.0);
            maceSlot = clamp(maceSlot, 0, 9);
            swordSlot = clamp(swordSlot, 0, 9);
            axeSlot = clamp(axeSlot, 0, 9);
            targetType = "players".equals(targetType) ? "players" : "living";
            maceMode = mode(maceMode);
            stunMode = mode(stunMode);
            if (weaponPriority == null) {
                weaponPriority = new ArrayList<>(List.of("mace", "sword", "axe"));
            }
            List<String> safePriority = new ArrayList<>(3);
            for (String weapon : weaponPriority) {
                if (weapon != null) {
                    String normalized = weapon.toLowerCase(Locale.ROOT);
                    if (List.of("mace", "sword", "axe").contains(normalized) && !safePriority.contains(normalized)) {
                        safePriority.add(normalized);
                    }
                }
            }
            for (String weapon : List.of("mace", "sword", "axe")) {
                if (!safePriority.contains(weapon)) safePriority.add(weapon);
            }
            weaponPriority = safePriority;
        }

        private static String mode(String value) {
            return "assist".equals(value) ? "assist" : "manual";
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static double clamp(double value, double min, double max) {
            if (!Double.isFinite(value)) return min;
            return Math.max(min, Math.min(max, value));
        }
    }
}
