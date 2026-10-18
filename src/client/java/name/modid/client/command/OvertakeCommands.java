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
                .then(literal("status").executes(context -> feedback(context.getSource(), status())))
                .then(literal("help").executes(context -> feedback(context.getSource(),
                        "Commands: /ot on|off|toggle|status|help|config|reload|debug <on|off>|profile <default|pvp|practice>; /ot switch, mace, stun, hud")))
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
                            builder.suggest("default").suggest("pvp").suggest("practice");
                            return builder.buildFuture();
                        }).executes(context -> {
                            String profile = StringArgumentType.getString(context, "name").toLowerCase(Locale.ROOT);
                            if (!List.of("default", "pvp", "practice").contains(profile)) {
                                return error(context.getSource(), "Profile must be default, pvp, or practice.");
                            }
                            OvertakeClient.CONFIG.profile = profile;
                            OvertakeClient.CONFIG.settings().validate();
                            save(context.getSource(), "Active profile: " + profile + ".");
                            return 1;
                        })))
                .then(switchCommands())
                .then(maceCommands())
                .then(stunCommands())
                .then(hudCommands()));
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
                .then(literal("status").executes(context -> {
                    var settings = OvertakeClient.CONFIG.settings();
                    return feedback(context.getSource(), "Switch " + onOff(settings.switchEnabled)
                            + ", priority " + String.join(" > ", settings.weaponPriority)
                            + ", delay " + settings.switchDelay + " ticks, restore " + onOff(settings.restoreSlot)
                            + ", manual override " + onOff(settings.manualOverride)
                            + ", state " + OvertakeClient.SWITCHER.status()
                            + ", shield slot " + slot(OvertakeClient.SWITCHER.findShieldSlot(context.getSource().getPlayer()))
                            + "/offhand " + onOff(OvertakeClient.SWITCHER.hasOffhandShield(context.getSource().getPlayer()))
                            + ", pearl slot " + slot(OvertakeClient.SWITCHER.findPearlSlot(context.getSource().getPlayer()))
                            + "/offhand " + onOff(OvertakeClient.SWITCHER.hasOffhandPearl(context.getSource().getPlayer()))
                            + ", offhand totem " + onOff(OvertakeClient.SWITCHER.hasOffhandTotem(context.getSource().getPlayer()))
                            + ", hotbar totem slot " + slot(OvertakeClient.SWITCHER.findTotemSlot(context.getSource().getPlayer())) + ".");
                }));
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
                .then(literal("status").executes(context -> {
                    var settings = OvertakeClient.CONFIG.settings();
                    return feedback(context.getSource(), "Mace " + onOff(settings.maceEnabled) + ", mode "
                            + settings.maceMode + ", triggerbot " + onOff(settings.maceAutoAttack)
                            + ", threshold " + settings.maceThreshold + " blocks, range "
                            + settings.maceRange + " blocks, switch delay " + settings.maceDelay + " ticks. "
                            + "Timing depends on server latency, server tick rate, and game mechanics.");
                }));
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
                .then(literal("status").executes(context -> {
                    var settings = OvertakeClient.CONFIG.settings();
                    return feedback(context.getSource(), "Stun key " + OvertakeClient.CONFIG.stunKeybind
                            + ", target " + settings.targetMode + ", follow-up " + settings.followUpMode
                            + ", reach " + settings.stunReach + ", aim cone " + settings.stunAimCone
                            + " degrees, mace fall check " + onOff(settings.stunMaceFallCheck) + ".");
                }));
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

    private static String status() {
        var settings = OvertakeClient.CONFIG.settings();
        return "Master " + onOff(OvertakeClient.CONFIG.masterEnabled) + " | profile "
                + OvertakeClient.CONFIG.profile + " | switch " + onOff(settings.switchEnabled)
                + " | mace " + onOff(settings.maceEnabled) + " (" + settings.maceMode
                + ", triggerbot " + onOff(settings.maceAutoAttack) + ") | HUD "
                + onOff(settings.hudEnabled) + ".";
    }

    private static String configSummary() {
        var settings = OvertakeClient.CONFIG.settings();
        return "Profile " + OvertakeClient.CONFIG.profile + "; switch delay " + settings.switchDelay
                + " ticks; target " + settings.targetType + "; slots mace/sword/axe "
                + slotSetting(settings.maceSlot) + "/" + slotSetting(settings.swordSlot) + "/"
                + slotSetting(settings.axeSlot)
                + "; priority " + String.join(" > ", settings.weaponPriority) + "; mace threshold "
                + settings.maceThreshold + " blocks; mace triggerbot " + onOff(settings.maceAutoAttack)
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
