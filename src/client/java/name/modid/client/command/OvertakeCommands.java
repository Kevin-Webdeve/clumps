package name.modid.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import name.modid.client.OvertakeClient;
import name.modid.client.config.OvertakeConfig;
import name.modid.client.config.StunKeybind;
import name.modid.client.module.SpearLungeFeature;

import java.util.List;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class OvertakeCommands {
    private OvertakeCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerRoot(dispatcher));
    }

    private static void registerRoot(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ot")
                .then(literal("on").executes(context -> {
                    OvertakeClient.CONFIG.masterEnabled = true;
                    save(context.getSource(), "OverTake enabled.");
                    return 1;
                }))
                .then(literal("off").executes(context -> {
                    OvertakeClient.SWITCHER.cancel(context.getSource().getClient(), false);
                    OvertakeClient.CONFIG.masterEnabled = false;
                    OvertakeClient.cancellationReason = "Master system disabled";
                    OvertakeClient.actionState = "Master disabled";
                    save(context.getSource(), "OverTake disabled; pending assistance cancelled.");
                    return 1;
                }))
                .then(literal("toggle").executes(context -> {
                    boolean enabling = !OvertakeClient.CONFIG.masterEnabled;
                    if (!enabling) OvertakeClient.SWITCHER.cancel(context.getSource().getClient(), false);
                    OvertakeClient.CONFIG.masterEnabled = enabling;
                    save(context.getSource(), "OverTake " + (enabling ? "enabled." : "disabled; pending actions cancelled."));
                    return 1;
                }))
                .then(literal("status").executes(context -> status(context.getSource())))
                .then(literal("help").executes(context -> feedback(context.getSource(),
                        "Commands: /ot on|off|toggle|status|help|config|reload|debug <on|off>|profile <default|pvp|practice|sword|axe|smp|crystal|pot|uhc|cart|mace|spear|vanilla|tnt>; /ot sword, crystal, cart, pot, sp, pearl, switch, mace, stun, hud. Status aliases show the full /ot status.")))
                .then(literal("config").executes(context -> feedback(context.getSource(), configSummary())))
                .then(literal("reload").executes(context -> {
                    OvertakeClient.SWITCHER.cancel(context.getSource().getClient(), false);
                    OvertakeClient.CONFIG = OvertakeConfig.load();
                    feedback(context.getSource(), "Configuration reloaded from config/overtake.json.");
                    return 1;
                }))
                .then(literal("debug")
                        .then(literal("on").executes(context -> setDebug(context.getSource(), true)))
                        .then(literal("off").executes(context -> setDebug(context.getSource(), false))))
                .then(literal("profile").then(argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("default").suggest("pvp").suggest("practice")
                                    .suggest("sword").suggest("axe").suggest("smp")
                                    .suggest("crystal").suggest("pot").suggest("uhc")
                                    .suggest("cart").suggest("mace").suggest("spear")
                                    .suggest("vanilla").suggest("tnt");
                            return builder.buildFuture();
                        }).executes(context -> {
                            String requestedProfile = StringArgumentType.getString(context, "name");
                            String profile = OvertakeConfig.normalizeProfile(requestedProfile);
                            if (profile == null) {
                                return error(context.getSource(),
                                        "Unknown profile. Choose default, pvp, practice, sword, axe, smp, crystal, pot, uhc, cart, mace, spear, vanilla, or tnt.");
                            }
                            OvertakeClient.SWITCHER.cancel(context.getSource().getClient(), false);
                            OvertakeClient.CONFIG.profile = profile;
                            OvertakeClient.CONFIG.settings().validate();
                            return save(context.getSource(), "Active profile: " + profile
                                    + " (saved settings are kept separately for each profile).");
                        })))
                .then(switchCommands())
                .then(maceCommands())
                .then(swordCommands())
                .then(crystalCommands())
                .then(cartCommands())
                .then(healingPotionCommands())
                .then(stunCommands())
                .then(spearLungeCommands())
                .then(pearlCommands())
                .then(hudCommands()));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource>
    swordCommands() {
        return literal("sword")
                .then(literal("triggerbot")
                        .then(literal("on").executes(context -> setProfileBoolean(context.getSource(), "sword",
                                settings -> settings.swordTriggerbot = true,
                                "Sword triggerbot enabled.")))
                        .then(literal("off").executes(context -> setProfileBoolean(context.getSource(), "sword",
                                settings -> settings.swordTriggerbot = false,
                                "Sword triggerbot disabled."))))
                .then(literal("sprint_reset")
                        .then(literal("on").executes(context -> setProfileBoolean(context.getSource(), "sword",
                                settings -> settings.swordSprintReset = true,
                                "Sword S/W sprint reset enabled.")))
                        .then(literal("off").executes(context -> setProfileBoolean(context.getSource(), "sword",
                                settings -> settings.swordSprintReset = false,
                                "Sword S/W sprint reset disabled."))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource>
    crystalCommands() {
        return keybindCommands("crystal", "Crystal action", config -> config.crystalKeybind,
                (config, key) -> config.crystalKeybind = key);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource>
    cartCommands() {
        return keybindCommands("cart", "TNT minecart action", config -> config.cartKeybind,
                (config, key) -> config.cartKeybind = key);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource>
    healingPotionCommands() {
        return keybindCommands("pot", "Healing potion action", config -> config.healingPotionKeybind,
                (config, key) -> config.healingPotionKeybind = key);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource>
    keybindCommands(String command, String label,
                    java.util.function.Function<OvertakeConfig, String> getter,
                    java.util.function.BiConsumer<OvertakeConfig, String> setter) {
        return literal(command)
                .then(literal("status").executes(context -> feedback(context.getSource(),
                        label + " keybind: " + getter.apply(OvertakeClient.CONFIG) + ".")))
                .then(literal("keybind").then(argument("key", StringArgumentType.string()).executes(context -> {
                    String key = StringArgumentType.getString(context, "key");
                    if (!StunKeybind.isValid(key)) {
                        return error(context.getSource(),
                                "Invalid key. Use a letter, digit, F1-F12, or mouse button (mouse2-mouse5).");
                    }
                    key = StunKeybind.normalize(key);
                    setter.accept(OvertakeClient.CONFIG, key);
                    return save(context.getSource(), label + " keybind set to " + key + ".");
                })));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource>
    spearLungeCommands() {
        return literal("sp")
                .executes(context -> {
                    boolean enabled = !OvertakeClient.CONFIG.dashEnabled;
                    return setSpearLungeEnabled(context.getSource(), enabled);
                })
                .then(literal("on").executes(context -> setSpearLungeEnabled(context.getSource(), true)))
                .then(literal("off").executes(context -> setSpearLungeEnabled(context.getSource(), false)))
                .then(literal("status").executes(context -> status(context.getSource())))
                .then(literal("delay").then(argument("milliseconds", IntegerArgumentType.integer(0, 5000))
                        .executes(context -> {
                            int delay = IntegerArgumentType.getInteger(context, "milliseconds");
                            OvertakeClient.CONFIG.settings().spearLungeSwitchDelayMs = delay;
                            return save(context.getSource(), "Spear Lunge switch delay: " + delay + " ms.");
                        })))
                .then(literal("clicklimit").then(argument("milliseconds", IntegerArgumentType.integer(0, 5000))
                        .executes(context -> {
                            int limit = IntegerArgumentType.getInteger(context, "milliseconds");
                            OvertakeClient.CONFIG.settings().spearLungeClickTriggerLimitMs = limit;
                            return save(context.getSource(), "Spear Lunge click trigger limit: " + limit + " ms.");
                        })))
                .then(literal("switchhotbardelay").then(argument("ticks", IntegerArgumentType.integer(0, 20))
                        .executes(context -> {
                            int ticks = IntegerArgumentType.getInteger(context, "ticks");
                            OvertakeClient.CONFIG.settings().spearLungeHotbarSwitchDelayTicks = ticks;
                            return save(context.getSource(),
                                    "Spear Lunge hotbar switch delay: " + ticks + " tick" + (ticks == 1 ? "" : "s") + ".");
                        })));
    }

    private static int setSpearLungeEnabled(FabricClientCommandSource source, boolean enabled) {
        if (!SpearLungeFeature.setEnabled(source.getClient(), enabled)) {
            return error(source, "Spear Lunge " + onOff(enabled)
                    + " in memory, but could not save config/overtake.json; check the game log.");
        }
        return feedback(source, "Spear Lunge " + onOff(enabled) + ".");
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> pearlCommands() {
        return literal("pearl")
                .executes(context -> status(context.getSource()))
                .then(argument("key", StringArgumentType.string()).executes(context -> {
                    String key = StringArgumentType.getString(context, "key");
                    if (!StunKeybind.isValid(key)) {
                        return error(context.getSource(),
                                "Invalid key. Use a letter (R), digit (1), function key (F1-F12), or mouse button (mouse2-mouse5).");
                    }
                    OvertakeClient.CONFIG.pearlKeybind = StunKeybind.normalize(key);
                    return save(context.getSource(),
                            "Ender Pearl activation key set to " + OvertakeClient.CONFIG.pearlKeybind + ".");
                }))
                .then(literal("delay")
                        .executes(context -> status(context.getSource()))
                        .then(argument("ticks", IntegerArgumentType.integer(0, 20)).executes(context -> {
                            OvertakeClient.CONFIG.pearlWindChargeDelayTicks =
                                    IntegerArgumentType.getInteger(context, "ticks");
                            return save(context.getSource(), "Wind Charge throw delay set to "
                                    + OvertakeClient.CONFIG.pearlWindChargeDelayTicks + " ticks.");
                        })));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> switchCommands() {
        return literal("switch")
                .then(literal("on").executes(context -> setSwitching(context.getSource(), true)))
                .then(literal("off").executes(context -> setSwitching(context.getSource(), false)))
                .then(literal("restore").then(literal("on").executes(context -> setBoolean(context.getSource(),
                                "Slot restore", value -> value.restoreSlot = true, "restoreSlot = true"))
                        .then(literal("off").executes(context -> setBoolean(context.getSource(),
                                "Slot restore", value -> value.restoreSlot = false, "restoreSlot = false")))))
                .then(literal("manual_override").then(literal("on").executes(context -> setBoolean(context.getSource(),
                                "Manual override", value -> value.manualOverride = true, "manualOverride = true"))
                        .then(literal("off").executes(context -> setBoolean(context.getSource(),
                                "Manual override", value -> value.manualOverride = false, "manualOverride = false")))))
                .then(literal("delay").then(argument("ticks", IntegerArgumentType.integer(0, 100))
                        .executes(context -> {
                            OvertakeClient.CONFIG.settings().switchDelay = IntegerArgumentType.getInteger(context, "ticks");
                            save(context.getSource(), "Switch delay: " + OvertakeClient.CONFIG.settings().switchDelay + " ticks.");
                            return 1;
                        })))
                .then(literal("priority").then(argument("first", StringArgumentType.word())
                        .then(argument("second", StringArgumentType.word())
                                .then(argument("third", StringArgumentType.word()).executes(context -> {
                                    List<String> priorities = List.of(
                                            StringArgumentType.getString(context, "first").toLowerCase(Locale.ROOT),
                                            StringArgumentType.getString(context, "second").toLowerCase(Locale.ROOT),
                                            StringArgumentType.getString(context, "third").toLowerCase(Locale.ROOT));
                                    if (priorities.stream().distinct().count() != 3
                                            || !priorities.containsAll(List.of("mace", "sword", "axe"))) {
                                        return error(context.getSource(), "Priority requires each of mace, sword, and axe exactly once.");
                                    }
                                    OvertakeClient.CONFIG.settings().weaponPriority = new java.util.ArrayList<>(priorities);
                                    save(context.getSource(), "Weapon priority: " + String.join(" > ", priorities) + ".");
                                    return 1;
                                })))))
                .then(literal("status").executes(context -> status(context.getSource())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> maceCommands() {
        return literal("mace")
                .then(literal("on").executes(context -> setBoolean(context.getSource(),
                        "Mace assistance", value -> value.maceEnabled = true, "Mace assistance enabled.")))
                .then(literal("off").executes(context -> setBoolean(context.getSource(),
                        "Mace assistance", value -> value.maceEnabled = false, "Mace assistance disabled.")))
                .then(maceTriggerbotCommand("auto"))
                .then(maceTriggerbotCommand("triggerbot"))
                .then(literal("mode").then(literal("manual").executes(context -> setMode(context.getSource(), "manual")))
                        .then(literal("assist").executes(context -> setMode(context.getSource(), "assist"))))
                .then(literal("threshold").then(argument("blocks", DoubleArgumentType.doubleArg(2.0, 20.0))
                        .executes(context -> {
                            double blocks = DoubleArgumentType.getDouble(context, "blocks");
                            OvertakeClient.CONFIG.settings().maceThreshold = blocks;
                            save(context.getSource(), "Mace fall-distance threshold: " + blocks + " blocks.");
                            return 1;
                        })))
                .then(literal("range").then(argument("blocks", DoubleArgumentType.doubleArg(1.0, 16.0))
                        .executes(context -> setRange(context.getSource(), "Mace range",
                                DoubleArgumentType.getDouble(context, "blocks"),
                                settings -> settings.maceRange = DoubleArgumentType.getDouble(context, "blocks")))))
                .then(literal("delay").then(argument("ticks", IntegerArgumentType.integer(0, 100))
                        .executes(context -> {
                            OvertakeClient.CONFIG.settings().maceDelay = IntegerArgumentType.getInteger(context, "ticks");
                            save(context.getSource(), "Mace switch delay: " + OvertakeClient.CONFIG.settings().maceDelay + " ticks.");
                            return 1;
                        })))
                .then(literal("status").executes(context -> status(context.getSource())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource>
    maceTriggerbotCommand(String command) {
        return literal(command)
                .then(literal("on").executes(context -> setBoolean(context.getSource(),
                        "Mace triggerbot", value -> value.maceAutoAttack = true,
                        "Mace triggerbot enabled; it attacks a valid crosshair target during a fall window without waiting for full weapon cooldown.")))
                .then(literal("off").executes(context -> setBoolean(context.getSource(),
                        "Mace triggerbot", value -> value.maceAutoAttack = false,
                        "Mace triggerbot disabled.")));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> hudCommands() {
        return literal("hud")
                .then(literal("on").executes(context -> setBoolean(context.getSource(),
                        "HUD", value -> value.hudEnabled = true, "HUD enabled.")))
                .then(literal("off").executes(context -> setBoolean(context.getSource(),
                        "HUD", value -> value.hudEnabled = false, "HUD disabled.")))
                .then(literal("sound").then(literal("on").executes(context -> setBoolean(context.getSource(),
                                "Sound alerts", value -> value.soundAlerts = true, "Sound alerts enabled."))
                        .then(literal("off").executes(context -> setBoolean(context.getSource(),
                                "Sound alerts", value -> value.soundAlerts = false, "Sound alerts disabled.")))))
                .then(literal("position").then(argument("x", IntegerArgumentType.integer(0, 10000))
                        .then(argument("y", IntegerArgumentType.integer(0, 10000)).executes(context -> {
                            var settings = OvertakeClient.CONFIG.settings();
                            settings.hudX = IntegerArgumentType.getInteger(context, "x");
                            settings.hudY = IntegerArgumentType.getInteger(context, "y");
                            save(context.getSource(), "HUD position set to " + settings.hudX + ", " + settings.hudY + ".");
                            return 1;
                        }))))
                .then(literal("scale").then(argument("value", DoubleArgumentType.doubleArg(0.5, 3.0))
                        .executes(context -> {
                            OvertakeClient.CONFIG.settings().hudScale = DoubleArgumentType.getDouble(context, "value");
                            save(context.getSource(), "HUD scale set to " + OvertakeClient.CONFIG.settings().hudScale + ".");
                            return 1;
                        })));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> stunCommands() {
        return literal("stun")
                .then(literal("keybind").then(argument("key", StringArgumentType.string()).executes(context -> {
                    String key = StringArgumentType.getString(context, "key");
                    if (!StunKeybind.isValid(key)) {
                        return error(context.getSource(),
                                "Invalid key. Use a letter (R), digit (1), function key (F1-F12), or mouse button (mouse2-mouse5).");
                    }
                    OvertakeClient.CONFIG.stunKeybind = StunKeybind.normalize(key);
                    return save(context.getSource(), "Stun slam keybind set to " + OvertakeClient.CONFIG.stunKeybind + ".");
                })))
                .then(literal("target")
                        .then(literal("crosshair").executes(context ->
                                setStunTarget(context.getSource(), "CROSSHAIR_ONLY", "crosshair")))
                        .then(literal("nearest").executes(context ->
                                setStunTarget(context.getSource(), "NEAREST_ONLY", "nearest")))
                        .then(literal("both").executes(context ->
                                setStunTarget(context.getSource(), "CROSSHAIR_OR_NEAREST", "both"))))
                .then(literal("mode")
                        .then(literal("auto").executes(context ->
                                setStunMode(context.getSource(), "AUTO", "auto")))
                        .then(literal("mace").executes(context ->
                                setStunMode(context.getSource(), "MACE_ONLY", "mace")))
                        .then(literal("sword").executes(context ->
                                setStunMode(context.getSource(), "SWORD_ONLY", "sword"))))
                .then(literal("reach").then(argument("blocks", DoubleArgumentType.doubleArg(1.0, 6.0))
                        .executes(context -> {
                            double reach = DoubleArgumentType.getDouble(context, "blocks");
                            OvertakeClient.CONFIG.settings().stunReach = reach;
                            return save(context.getSource(), "Stun target reach set to " + reach + " blocks.");
                        })))
                .then(literal("cone").then(argument("degrees", DoubleArgumentType.doubleArg(1.0, 180.0))
                        .executes(context -> {
                            double cone = DoubleArgumentType.getDouble(context, "degrees");
                            OvertakeClient.CONFIG.settings().stunAimCone = cone;
                            return save(context.getSource(), "Stun aim cone set to " + cone + " degrees.");
                        })))
                .then(literal("degree").then(argument("degrees", DoubleArgumentType.doubleArg(1.0, 360.0))
                        .executes(context -> {
                            double degrees = DoubleArgumentType.getDouble(context, "degrees");
                            OvertakeClient.CONFIG.settings().stunAimCone = degrees;
                            return save(context.getSource(),
                                    "Stun nearest-target angle set to " + degrees + " degrees.");
                        })))
                .then(literal("fall_check").then(literal("on").executes(context -> setBoolean(context.getSource(),
                                "Mace fall check", settings -> settings.stunMaceFallCheck = true,
                                "Mace fall check enabled."))
                        .then(literal("off").executes(context -> setBoolean(context.getSource(),
                                "Mace fall check", settings -> settings.stunMaceFallCheck = false,
                                "Mace fall check disabled.")))))
                .then(literal("status").executes(context -> status(context.getSource())));
    }

    private static int setStunTarget(FabricClientCommandSource source, String mode, String label) {
        OvertakeClient.CONFIG.settings().targetMode = mode;
        return save(source, "Stun target mode: " + label + ".");
    }

    private static int setStunMode(FabricClientCommandSource source, String mode, String label) {
        OvertakeClient.CONFIG.settings().followUpMode = mode;
        return save(source, "Stun follow-up mode: " + label + ".");
    }

    private static int setSwitching(FabricClientCommandSource source, boolean enabled) {
        var settings = OvertakeClient.CONFIG.settings();
        settings.switchEnabled = enabled;
        if (!enabled) OvertakeClient.SWITCHER.cancel(source.getClient(), settings.restoreSlot);
        return save(source, "Weapon switching " + onOff(enabled) + ".");
    }

    private static int setMode(FabricClientCommandSource source, String mode) {
        OvertakeClient.CONFIG.settings().maceMode = mode;
        return save(source, "Mace mode: " + mode + ".");
    }

    private static int setRange(FabricClientCommandSource source, String name, double number,
                                java.util.function.Consumer<OvertakeConfig.Settings> setter) {
        setter.accept(OvertakeClient.CONFIG.settings());
        return save(source, name + ": " + number + " blocks.");
    }

    private static int setBoolean(FabricClientCommandSource source, String name,
                                  java.util.function.Consumer<OvertakeConfig.Settings> change, String message) {
        change.accept(OvertakeClient.CONFIG.settings());
        return save(source, message);
    }

    private static int setProfileBoolean(FabricClientCommandSource source, String profile,
                                         java.util.function.Consumer<OvertakeConfig.Settings> change,
                                         String message) {
        change.accept(OvertakeClient.CONFIG.gameModeSettings.get(profile));
        return save(source, message);
    }

    private static int setDebug(FabricClientCommandSource source, boolean enabled) {
        OvertakeClient.CONFIG.debug = enabled;
        return save(source, "Diagnostic logging " + onOff(enabled) + ".");
    }

    private static int save(FabricClientCommandSource source, String message) {
        if (!OvertakeClient.CONFIG.save()) {
            return error(source, "Setting changed in memory but could not save config/overtake.json; check the game log.");
        }
        feedback(source, message);
        return 1;
    }

    private static int feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal("[OT] " + message));
        return 1;
    }

    private static int error(FabricClientCommandSource source, String message) {
        source.sendError(Component.literal("[OT] " + message));
        return 0;
    }

    private static int status(FabricClientCommandSource source) {
        var settings = OvertakeClient.CONFIG.settings();
        var player = source.getPlayer();
        List<String> lines = new java.util.ArrayList<>(24);
        lines.add("Status");
        lines.add("Master: " + onOff(OvertakeClient.CONFIG.masterEnabled));
        lines.add("Profile: " + OvertakeClient.CONFIG.profile);
        lines.add("Weapon switching: " + onOff(settings.switchEnabled));
        lines.add("Switch priority: " + String.join(" > ", settings.weaponPriority));
        lines.add("Melee target type/range: " + settings.targetType + "/" + settings.meleeRange + " blocks");
        lines.add("Configured mace/sword/axe slots: " + slotSetting(settings.maceSlot) + "/"
                + slotSetting(settings.swordSlot) + "/" + slotSetting(settings.axeSlot));
        lines.add("Switch delay: " + settings.switchDelay + " ticks");
        lines.add("Restore slot: " + onOff(settings.restoreSlot));
        lines.add("Manual override: " + onOff(settings.manualOverride));
        lines.add("Switch state: " + OvertakeClient.SWITCHER.status());
        lines.add("Mace assistance: " + onOff(settings.maceEnabled));
        lines.add("Mace mode: " + settings.maceMode);
        lines.add("Mace triggerbot: " + onOff(settings.maceAutoAttack));
        lines.add("Mace threshold/range: " + settings.maceThreshold + "/" + settings.maceRange + " blocks");
        lines.add("Mace switch delay: " + settings.maceDelay + " ticks");
        lines.add("Sword triggerbot/sprint reset: " + onOff(settings.swordTriggerbot)
                + "/" + onOff(settings.swordSprintReset));
        lines.add("Spear Lunge: " + onOff(OvertakeClient.CONFIG.dashEnabled));
        lines.add("Spear switch delay: " + settings.spearLungeSwitchDelayMs + " ms");
        lines.add("Pearl key: " + OvertakeClient.CONFIG.pearlKeybind);
        lines.add("Wind Charge delay: " + OvertakeClient.CONFIG.pearlWindChargeDelayTicks + " ticks");
        lines.add("Crystal/cart/healing potion keys: " + OvertakeClient.CONFIG.crystalKeybind + "/"
                + OvertakeClient.CONFIG.cartKeybind + "/" + OvertakeClient.CONFIG.healingPotionKeybind);
        lines.add("Stun key: " + OvertakeClient.CONFIG.stunKeybind);
        lines.add("Stun target/follow-up: " + settings.targetMode + "/" + settings.followUpMode);
        lines.add("Stun reach/cone: " + settings.stunReach + " blocks/" + settings.stunAimCone + " degrees");
        lines.add("Stun mace fall check: " + onOff(settings.stunMaceFallCheck));
        lines.add("HUD: " + onOff(settings.hudEnabled));
        lines.add("HUD position/scale: " + settings.hudX + "," + settings.hudY + "/" + settings.hudScale);
        lines.add("HUD sound alerts: " + onOff(settings.soundAlerts));
        lines.add("Debug: " + onOff(OvertakeClient.CONFIG.debug));
        if (player != null) {
            lines.add("Shield hotbar/offhand: " + slot(OvertakeClient.SWITCHER.findShieldSlot(player))
                    + "/" + onOff(OvertakeClient.SWITCHER.hasOffhandShield(player)));
            lines.add("Pearl hotbar/offhand: " + slot(OvertakeClient.SWITCHER.findPearlSlot(player))
                    + "/" + onOff(OvertakeClient.SWITCHER.hasOffhandPearl(player)));
            lines.add("Totem hotbar/offhand: " + slot(OvertakeClient.SWITCHER.findTotemSlot(player))
                    + "/" + onOff(OvertakeClient.SWITCHER.hasOffhandTotem(player)));
        }
        lines.forEach(line -> feedback(source, line));
        return 1;
    }

    private static String configSummary() {
        var settings = OvertakeClient.CONFIG.settings();
        return "Profile " + OvertakeClient.CONFIG.profile + "; switch delay " + settings.switchDelay
                + " ticks; target " + settings.targetType + "; slots mace/sword/axe "
                + slotSetting(settings.maceSlot) + "/" + slotSetting(settings.swordSlot) + "/"
                + slotSetting(settings.axeSlot)
                + "; priority " + String.join(" > ", settings.weaponPriority) + "; mace threshold "
                + settings.maceThreshold + " blocks; mace triggerbot " + onOff(settings.maceAutoAttack)
                + "; sword triggerbot/sprint reset " + onOff(settings.swordTriggerbot) + "/"
                + onOff(settings.swordSprintReset)
                + "; pearl key " + OvertakeClient.CONFIG.pearlKeybind + " / Wind Charge delay "
                + OvertakeClient.CONFIG.pearlWindChargeDelayTicks + " ticks"
                + "; crystal/cart/potion keys " + OvertakeClient.CONFIG.crystalKeybind + "/"
                + OvertakeClient.CONFIG.cartKeybind + "/" + OvertakeClient.CONFIG.healingPotionKeybind
                + "; stun key " + OvertakeClient.CONFIG.stunKeybind + " target/mode "
                + settings.targetMode + "/" + settings.followUpMode
                + " reach/cone " + settings.stunReach + "/" + settings.stunAimCone
                + "; ranges melee/mace " + settings.meleeRange + "/"
                + settings.maceRange + "; restore " + onOff(settings.restoreSlot)
                + "; manual override " + onOff(settings.manualOverride) + "; HUD "
                + (settings.hudEnabled ? settings.hudX + "," + settings.hudY + " scale " + settings.hudScale : "off")
                + "; sound alerts " + onOff(settings.soundAlerts) + "; debug "
                + onOff(OvertakeClient.CONFIG.debug) + ".";
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String slotSetting(int slot) {
        return slot == 0 ? "auto" : Integer.toString(slot);
    }

    private static String slot(int slot) {
        return slot < 0 ? "missing" : Integer.toString(slot + 1);
    }
}
