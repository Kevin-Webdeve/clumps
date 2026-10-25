package name.modid.client;

import com.mojang.blaze3d.platform.InputConstants;
import name.modid.client.command.OvertakeCommands;
import name.modid.client.config.OvertakeConfig;
import name.modid.client.gui.OvertakeSettingsScreen;
import name.modid.client.module.PearlFeature;
import name.modid.client.module.CombatProfileFeature;
import name.modid.client.module.SpearLungeFeature;
import name.modid.client.module.WeaponSwitchController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OvertakeClient implements ClientModInitializer {
    public static final String MOD_ID = "overtake";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static OvertakeConfig CONFIG;
    public static final WeaponSwitchController SWITCHER = new WeaponSwitchController();
    public static boolean maceReady;
    public static String actionState = "Idle";
    public static String cancellationReason = "None";
    public static double targetDistance = -1;
    public static long lastTickNanos;
    private static KeyMapping toggleKey;
    private static KeyMapping settingsKey;
    private static ClientLevel previousWorld;
    private static String previousActionState = "";
    private static boolean autoAttackAttempted;

    @Override
    public void onInitializeClient() {
        CONFIG = OvertakeConfig.load();
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.overtake.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, KeyMapping.Category.MISC));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.overtake.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KeyMapping.Category.MISC));
        OvertakeCommands.register();
        SpearLungeFeature.register();
        PearlFeature.register();
        ClientTickEvents.START_CLIENT_TICK.register(OvertakeClient::tick);
        CombatProfileFeature.register();
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                (graphics, tickCounter) -> render(graphics));
    }

    private static void tick(Minecraft client) {
        long started = System.nanoTime();
        while (toggleKey.consumeClick()) {
            toggleMaster(client);
        }
        while (settingsKey.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new OvertakeSettingsScreen(null));
            }
        }
        OvertakeConfig.Settings settings = CONFIG.settings();
        if (client.level != previousWorld) {
            SWITCHER.cancel(client, false);
            SpearLungeFeature.resetSession();
            previousWorld = client.level;
            targetDistance = -1;
            maceReady = false;
            autoAttackAttempted = false;
            cancellationReason = "World changed";
        }
        if (client.player == null || client.level == null || !client.player.isAlive()) {
            SWITCHER.cancel(client, false);
            maceReady = false;
            autoAttackAttempted = false;
            targetDistance = -1;
            actionState = "Idle";
            lastTickNanos = System.nanoTime() - started;
            return;
        }
        if (client.isPaused()) {
            lastTickNanos = System.nanoTime() - started;
            return;
        }
        if (client.screen != null) {
            SWITCHER.cancel(client, false);
            maceReady = false;
            autoAttackAttempted = false;
            targetDistance = -1;
            actionState = "Inventory or screen open";
            lastTickNanos = System.nanoTime() - started;
            return;
        }
        if (SpearLungeFeature.isRunning()) {
            lastTickNanos = System.nanoTime() - started;
            return;
        }
        if (PearlFeature.isActionActiveThisTick()) {
            lastTickNanos = System.nanoTime() - started;
            return;
        }
        if (!CONFIG.masterEnabled) {
            SWITCHER.cancel(client, false);
            maceReady = false;
            autoAttackAttempted = false;
            targetDistance = -1;
            actionState = "Master disabled";
            lastTickNanos = System.nanoTime() - started;
            return;
        }

        SWITCHER.refreshHotbar(client.player, settings);
        var target = OvertakeRuntime.findTarget(client, settings);
        targetDistance = target == null ? -1 : client.player.distanceTo(target);
        boolean wasMaceReady = maceReady;
        maceReady = OvertakeRuntime.isMaceReady(client, target, settings);
        if (!maceReady) {
            autoAttackAttempted = false;
        }
        if (maceReady && !wasMaceReady && settings.soundAlerts) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.15F));
        }
        boolean maceRequest = settings.switchEnabled && settings.maceEnabled
                && maceReady && (settings.maceAutoAttack
                || ("assist".equals(settings.maceMode) && client.options.keyAttack.isDown()));
        double maceDetectionRange = Math.max(4.5,
                OvertakeRuntime.targetRange(client, settings.maceRange));
        boolean swordTriggerbot = "sword".equals(CONFIG.profile) && settings.swordTriggerbot;
        int preferredMeleeSlot = target == null ? -1
                : swordTriggerbot ? SWITCHER.findSwordSlot(client.player)
                : "axe".equals(CONFIG.profile) ? SWITCHER.findAxeSlot(client.player)
                : SWITCHER.findPreferredMeleeSlot(client.player, settings.weaponPriority);
        boolean meleeRequest = CONFIG.masterEnabled && settings.switchEnabled && target != null
                && targetDistance <= OvertakeRuntime.targetRange(client, settings.meleeRange)
                && (client.options.keyAttack.isDown() || swordTriggerbot);
        int requestedSlot = maceRequest
                ? SWITCHER.findMaceSlot(client.player)
                : meleeRequest ? preferredMeleeSlot : -1;
        boolean validRequest = (maceRequest || meleeRequest) && requestedSlot >= 0;

        SWITCHER.tick(client, CONFIG.masterEnabled && settings.switchEnabled && validRequest,
                requestedSlot, maceRequest ? settings.maceDelay : settings.switchDelay,
                settings.restoreSlot, settings.manualOverride);

        if (settings.maceAutoAttack && maceRequest && !autoAttackAttempted
                && SWITCHER.isActionReady(requestedSlot) && client.gameMode != null) {
            autoAttackAttempted = attackTarget(client, target, maceDetectionRange);
        }

        if (!CONFIG.masterEnabled) {
            actionState = "Master disabled";
        } else if (maceReady) {
            actionState = settings.maceAutoAttack
                    ? autoAttackAttempted ? "Mace attack sent" : "Mace window ready"
                    : "Mace window ready (manual confirmation)";
        } else if (SWITCHER.isSwitching()) {
            actionState = "Switching weapon";
        } else if (target != null) {
            actionState = "Target in view";
        } else {
            actionState = "No valid target";
        }
        if (CONFIG.debug && !actionState.equals(previousActionState)) {
            LOGGER.debug("Combat state changed: {}", actionState);
            previousActionState = actionState;
        }
        lastTickNanos = System.nanoTime() - started;
    }

    private static void render(GuiGraphics graphics) {
        if (CONFIG == null || !CONFIG.settings().hudEnabled) {
            return;
        }
        OvertakeRuntime.renderHud(graphics, CONFIG, SWITCHER, maceReady, actionState, cancellationReason,
                targetDistance, lastTickNanos);
    }

    private static boolean attackTarget(Minecraft client, LivingEntity target, double maximumRange) {
        if (client.player == null || client.gameMode == null || target == null
                || !client.player.isAlive() || client.player.isSpectator()
                || !target.isAlive() || !target.isAttackable()
                || client.player.distanceTo(target) > maximumRange) {
            return false;
        }
        client.gameMode.attack(client.player, target);
        client.player.swing(InteractionHand.MAIN_HAND);
        client.player.resetAttackStrengthTicker();
        return true;
    }

    private static void toggleMaster(Minecraft client) {
        CONFIG.masterEnabled = !CONFIG.masterEnabled;
        if (!CONFIG.masterEnabled) {
            SWITCHER.cancel(client, false);
        }
        CONFIG.save();
    }
}
