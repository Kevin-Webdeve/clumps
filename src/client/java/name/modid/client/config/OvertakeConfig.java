package name.modid.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import name.modid.client.OvertakeClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OvertakeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("overtake.json");

    public boolean masterEnabled;
    public boolean debug;
    @SerializedName(value = "dashEnabled", alternate = {"spearLungeEnabled"})
    public boolean dashEnabled;
    public boolean enableOnMultiplayer = true;
    public String profile = "default";
    public String stunKeybind = "G";
    public String pearlKeybind = "P";
    public String crystalKeybind = "C";
    public String cartKeybind = "V";
    public String healingPotionKeybind = "H";
    public int pearlWindChargeDelayTicks = 0;
    public Settings defaultSettings = new Settings();
    public Settings pvpSettings = Settings.pvp();
    public Settings practiceSettings = Settings.practice();
    public Map<String, Settings> gameModeSettings = createGameModeSettings();

    public Settings settings() {
        return switch (profile) {
            case "pvp" -> pvpSettings;
            case "practice" -> practiceSettings;
            case "sword", "axe", "smp", "crystal", "pot", "uhc", "cart", "mace", "spear", "vanilla", "tnt" ->
                    gameModeSettings.get(profile);
            default -> defaultSettings;
        };
    }

    public static String normalizeProfile(String profile) {
        if (profile == null) return null;
        String normalized = profile.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "diamond", "netherite" -> "smp";
            case "nodebuff", "nethpot" -> "pot";
            case "trident" -> "spear";
            case "default", "pvp", "practice", "sword", "axe", "smp", "crystal",
                    "pot", "uhc", "cart", "mace", "spear", "vanilla", "tnt" -> normalized;
            default -> null;
        };
    }

    private static Map<String, Settings> createGameModeSettings() {
        Map<String, Settings> profiles = new LinkedHashMap<>();
        for (String name : List.of("sword", "axe", "smp", "crystal", "pot", "uhc",
                "cart", "mace", "spear", "vanilla", "tnt")) {
            profiles.put(name, Settings.forGameMode(name));
        }
        return profiles;
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
        String normalizedProfile = normalizeProfile(profile);
        profile = normalizedProfile == null ? "default" : normalizedProfile;
        if (!StunKeybind.isValid(stunKeybind)) {
            stunKeybind = "G";
        } else {
            stunKeybind = StunKeybind.normalize(stunKeybind);
        }
        if (!StunKeybind.isValid(pearlKeybind)) {
            pearlKeybind = "P";
        } else {
            pearlKeybind = StunKeybind.normalize(pearlKeybind);
        }
        if (!StunKeybind.isValid(crystalKeybind)) {
            crystalKeybind = "C";
        } else {
            crystalKeybind = StunKeybind.normalize(crystalKeybind);
        }
        if (!StunKeybind.isValid(cartKeybind)) {
            cartKeybind = "V";
        } else {
            cartKeybind = StunKeybind.normalize(cartKeybind);
        }
        if (!StunKeybind.isValid(healingPotionKeybind)) {
            healingPotionKeybind = "H";
        } else {
            healingPotionKeybind = StunKeybind.normalize(healingPotionKeybind);
        }
        pearlWindChargeDelayTicks = Math.max(0, Math.min(20, pearlWindChargeDelayTicks));
        if (defaultSettings == null) defaultSettings = new Settings();
        if (pvpSettings == null) pvpSettings = Settings.pvp();
        if (practiceSettings == null) practiceSettings = Settings.practice();
        if (gameModeSettings == null) gameModeSettings = new LinkedHashMap<>();
        Map<String, Settings> defaultGameModeSettings = createGameModeSettings();
        for (Map.Entry<String, Settings> entry : defaultGameModeSettings.entrySet()) {
            Settings settings = gameModeSettings.get(entry.getKey());
            if (settings == null) {
                settings = entry.getValue();
                gameModeSettings.put(entry.getKey(), settings);
            }
            settings.validate();
        }
        defaultSettings.validate();
        pvpSettings.validate();
        practiceSettings.validate();
    }

    public static final class Settings {
        public String activationMode = "manual";
        public int inputRetryTicks = 2;
        public int activationTimeoutTicks = 5;
        public int inputBufferTicks = 1;
        public int dashCooldownTicks = 2;
        public int spearLungeSwitchDelayMs = 30;
        public int spearLungeHotbarSwitchDelayTicks = 0;
        public int spearLungeClickTriggerLimitMs = 50;
        public int maceRestoreDelayMs = 70;
        public boolean requireLungeEnchantment = true;
        public boolean allowVerticalDash;
        public boolean allowAirDash = true;
        public boolean debugLogging;
        public boolean switchEnabled = true;
        public boolean maceEnabled = true;
        public boolean restoreSlot = true;
        public boolean manualOverride = true;
        public boolean hudEnabled = true;
        public boolean soundAlerts;
        public boolean stunMaceFallCheck = true;
        public int switchDelay;
        public int maceDelay = 0;
        public int hudX = 8;
        public int hudY = 8;
        public double maceThreshold = 2.0;
        public double maceRange = 4.5;
        public double meleeRange = 4.5;
        public double hudScale = 1.0;
        public double stunReach = 3.0;
        public double stunAimCone = 60.0;
        public int maceSlot;
        public int swordSlot;
        public int axeSlot;
        public String targetType = "living";
        public String maceMode = "manual";
        public String targetMode = "CROSSHAIR_OR_NEAREST";
        public String followUpMode = "AUTO";
        public boolean maceAutoAttack;
        public boolean swordTriggerbot;
        public boolean swordSprintReset;
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

        private static Settings forGameMode(String profile) {
            Settings settings = new Settings();
            settings.targetType = "players";
            settings.maceEnabled = "mace".equals(profile);
            settings.maceMode = settings.maceEnabled ? "assist" : "manual";
            if ("sword".equals(profile)) {
                settings.swordTriggerbot = true;
                settings.swordSprintReset = true;
            }
            if ("axe".equals(profile)) {
                settings.weaponPriority = new ArrayList<>(List.of("axe", "sword", "mace"));
            } else {
                settings.weaponPriority = new ArrayList<>(List.of("sword", "axe", "mace"));
            }
            return settings;
        }

        public void validate() {
            activationMode = "automatic".equals(activationMode) ? "automatic" : "manual";
            inputRetryTicks = clamp(inputRetryTicks, 1, 100);
            activationTimeoutTicks = clamp(activationTimeoutTicks, 1, 100);
            inputBufferTicks = clamp(inputBufferTicks, 0, 20);
            dashCooldownTicks = clamp(dashCooldownTicks, 0, 1200);
            spearLungeSwitchDelayMs = clamp(spearLungeSwitchDelayMs, 0, 5000);
            spearLungeHotbarSwitchDelayTicks = clamp(spearLungeHotbarSwitchDelayTicks, 0, 20);
            spearLungeClickTriggerLimitMs = clamp(spearLungeClickTriggerLimitMs, 0, 5000);
            maceRestoreDelayMs = clamp(maceRestoreDelayMs, 0, 5000);
            switchDelay = clamp(switchDelay, 0, 100);
            maceDelay = clamp(maceDelay, 0, 100);
            hudX = clamp(hudX, 0, 10000);
            hudY = clamp(hudY, 0, 10000);
            maceThreshold = clamp(maceThreshold, 2.0, 20.0);
            maceRange = clamp(maceRange, 1.0, 16.0);
            meleeRange = clamp(meleeRange, 1.0, 8.0);
            hudScale = clamp(hudScale, 0.5, 3.0);
            stunReach = clamp(stunReach, 1.0, 6.0);
            stunAimCone = clamp(stunAimCone, 1.0, 360.0);
            maceSlot = clamp(maceSlot, 0, 9);
            swordSlot = clamp(swordSlot, 0, 9);
            axeSlot = clamp(axeSlot, 0, 9);
            targetType = "players".equals(targetType) ? "players" : "living";
            maceMode = mode(maceMode);
            targetMode = switch (targetMode == null ? "" : targetMode) {
                case "CROSSHAIR_ONLY" -> "CROSSHAIR_ONLY";
                case "NEAREST_ONLY" -> "NEAREST_ONLY";
                default -> "CROSSHAIR_OR_NEAREST";
            };
            followUpMode = switch (followUpMode == null ? "" : followUpMode) {
                case "MACE_ONLY" -> "MACE_ONLY";
                case "SWORD_ONLY" -> "SWORD_ONLY";
                default -> "AUTO";
            };
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
