package name.modid.client;

import com.mojang.blaze3d.platform.InputConstants;
import name.modid.client.command.OvertakeCommands;
import name.modid.client.config.OvertakeConfig;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
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
    private static ClientLevel previousWorld;
    private static String previousActionState = "";
    private static boolean autoAttackAttempted;
    private enum StunSlamStage { IDLE, AXE, MACE }
    private static StunSlamStage stunSlamStage = StunSlamStage.IDLE;
    private static LivingEntity stunSlamTarget;
    private static int stunSlamTicks;

    @Override
    public void onInitializeClient() {
        CONFIG = OvertakeConfig.load();
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.overtake.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, KeyMapping.Category.MISC));
        OvertakeCommands.register();
        ClientTickEvents.START_CLIENT_TICK.register(OvertakeClient::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                (graphics, tickCounter) -> render(graphics));
    }

    private static void tick(Minecraft client) {
        long started = System.nanoTime();
        while (toggleKey.consumeClick()) {
            toggleMaster(client);
        }
        OvertakeConfig.Settings settings = CONFIG.settings();
        if (client.level != previousWorld) {
            SWITCHER.cancel(client, false);
            previousWorld = client.level;
            targetDistance = -1;
            maceReady = false;
            autoAttackAttempted = false;
            clearStunSlam();
            cancellationReason = "World changed";
        }
        if (client.player == null || client.level == null || !client.player.isAlive()) {
            SWITCHER.cancel(client, false);
            maceReady = false;
            autoAttackAttempted = false;
            clearStunSlam();
            targetDistance = -1;
            actionState = "Idle";
            lastTickNanos = System.nanoTime() - started;
            return;
        }
        if (client.isPaused()) {
            lastTickNanos = System.nanoTime() - started;
            return;
        }
        if (!CONFIG.masterEnabled) {
            SWITCHER.cancel(client, false);
            maceReady = false;
            autoAttackAttempted = false;
            clearStunSlam();
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
        updateStunSlam(client, settings, target);
        boolean stunSlamActive = stunSlamStage != StunSlamStage.IDLE;
        boolean stunAxeRequest = stunSlamStage == StunSlamStage.AXE;
        boolean stunMaceRequest = stunSlamStage == StunSlamStage.MACE;
        if (stunSlamActive) maceRequest = false;
        int preferredMeleeSlot = target == null ? -1
                : SWITCHER.findPreferredMeleeSlot(client.player, settings.weaponPriority);
        boolean meleeRequest = CONFIG.masterEnabled && settings.switchEnabled && target != null
                && targetDistance <= OvertakeRuntime.targetRange(client, settings.meleeRange)
                && (preferredMeleeSlot != SWITCHER.findAxeSlot(client.player)
                || OvertakeRuntime.isFallingAtLeast(client, 2.0))
                && client.options.keyAttack.isDown() && !stunSlamActive;
        int requestedSlot = stunAxeRequest
                ? SWITCHER.findAxeSlot(client.player)
                : stunMaceRequest || maceRequest
                ? SWITCHER.findMaceSlot(client.player)
                : meleeRequest ? preferredMeleeSlot : -1;
        boolean validRequest = (stunSlamActive || maceRequest || meleeRequest) && requestedSlot >= 0;

        SWITCHER.tick(client, CONFIG.masterEnabled && settings.switchEnabled && validRequest,
                requestedSlot, stunMaceRequest || maceRequest ? settings.maceDelay : settings.switchDelay,
                settings.restoreSlot, settings.manualOverride);

        if (stunAxeRequest && SWITCHER.isActionReady(requestedSlot) && client.gameMode != null) {
            if (attackTarget(client, stunSlamTarget, OvertakeRuntime.targetRange(client, settings.stunRange))) {
                SWITCHER.cancel(client, false);
                stunSlamStage = StunSlamStage.MACE;
                stunSlamTicks = 0;
            } else {
                clearStunSlam();
                SWITCHER.cancel(client, false);
            }
        } else if (stunMaceRequest && SWITCHER.isActionReady(requestedSlot) && client.gameMode != null) {
            if (attackTarget(client, stunSlamTarget, OvertakeRuntime.targetRange(client, settings.stunRange))) {
                SWITCHER.cancel(client, false);
                clearStunSlam();
            } else {
                clearStunSlam();
                SWITCHER.cancel(client, false);
            }
        } else if (settings.maceAutoAttack && maceRequest && !autoAttackAttempted
                && SWITCHER.isActionReady(requestedSlot) && client.gameMode != null
                && client.player.getAttackStrengthScale(0.5F) >= 1.0F) {
            autoAttackAttempted = attackTarget(client, target,
                    OvertakeRuntime.targetRange(client, settings.maceRange));
        }

        if (!CONFIG.masterEnabled) {
            actionState = "Master disabled";
        } else if (stunSlamActive) {
            actionState = stunSlamStage == StunSlamStage.AXE ? "Stun slam: axe hit" : "Stun slam: mace follow-up";
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

    private static void updateStunSlam(Minecraft client, OvertakeConfig.Settings settings, LivingEntity target) {
        if (stunSlamStage != StunSlamStage.IDLE) {
            stunSlamTicks++;
            if (!CONFIG.masterEnabled || !settings.switchEnabled || !settings.stunEnabled
                    || !"assist".equals(settings.stunMode)
                    || target != stunSlamTarget || !stunSlamTarget.isAlive()
                    || !OvertakeRuntime.isFallingAtLeast(client, settings.maceThreshold)
                    || client.player.distanceTo(stunSlamTarget) > OvertakeRuntime.targetRange(client, settings.stunRange)
                    || stunSlamTicks > Math.max(4, settings.switchDelay + settings.maceDelay + 4)) {
                clearStunSlam();
            }
            return;
        }
        if (!CONFIG.masterEnabled || !settings.switchEnabled || !settings.stunEnabled
                || !"assist".equals(settings.stunMode) || target == null || !target.isBlocking()
                || !OvertakeRuntime.isFallingAtLeast(client, settings.maceThreshold)
                || client.player.distanceTo(target) > Math.min(settings.stunRange, 3.0)
                || SWITCHER.findAxeSlot(client.player) < 0 || SWITCHER.findMaceSlot(client.player) < 0
                || client.player.getAttackStrengthScale(0.5F) < 1.0F
                || !client.options.keyAttack.consumeClick()) {
            return;
        }
        stunSlamTarget = target;
        stunSlamStage = StunSlamStage.AXE;
        stunSlamTicks = 0;
    }

    private static void clearStunSlam() {
        stunSlamTarget = null;
        stunSlamStage = StunSlamStage.IDLE;
        stunSlamTicks = 0;
    }

    private static boolean attackTarget(Minecraft client, LivingEntity target, double maximumRange) {
        if (client.player == null || client.gameMode == null || target == null
                || !client.player.isAlive() || client.player.isSpectator()
                || !target.isAlive() || !target.isAttackable()
                || !(client.hitResult instanceof EntityHitResult hit) || hit.getEntity() != target
                || !client.player.isWithinAttackRange(target.getBoundingBox(), target.getPickRadius())
                || client.player.distanceTo(target) > maximumRange
                || client.player.getAttackStrengthScale(0.5F) < 1.0F) {
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
            clearStunSlam();
        }
        boolean saved = CONFIG.save();
        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("OverTake " + (CONFIG.masterEnabled ? "enabled." : "disabled.")
                            + (saved ? "" : " (config save failed; check the game log)")),
                    true);
        }
    }
}
