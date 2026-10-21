package name.modid.client.module;

import name.modid.client.OvertakeClient;
import name.modid.client.config.OvertakeConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

public final class SpearLungeFeature {
    private enum State {
        IDLE, READY, INPUT_PENDING, DASH_ACTIVE, COOLDOWN, FAILED
    }

    private enum Sequence {
        NONE, SWITCH_DELAY, RESTORE_DELAY
    }

    private static final double MIN_PREDICTED_LUNGE_SPEED = 0.16;
    private static final int DASH_ACTIVE_MAX_TICKS = 20;
    private static final int BACK_DELAY_TICKS = 1;

    private static State state = State.IDLE;
    private static Sequence sequence = Sequence.NONE;
    private static LocalPlayer sequencePlayer;
    private static ClientLevel sequenceLevel;
    private static Vec3 initialPosition;
    private static Vec3 initialVelocity;
    private static Vec3 dashDirection;
    private static int attemptLungeLevel;
    private static long attemptStartTick;
    private static long nextEligibleTick;
    private static long lastAttemptNanos;
    private static long switchDelayUntilNanos;
    private static long attemptCount;
    private static int originalSlot = -1;
    private static int spearSlot = -1;
    private static int waitTicks;
    private static int bufferedInputTicks;
    private static int lowMotionTicks;
    private SpearLungeFeature() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(SpearLungeFeature::tick);
    }

    public static boolean isRunning() {
        return sequence != Sequence.NONE || state == State.INPUT_PENDING || state == State.DASH_ACTIVE;
    }

    public static boolean setEnabled(Minecraft client, boolean enabled) {
        if (OvertakeClient.CONFIG == null) return false;
        OvertakeClient.CONFIG.dashEnabled = enabled;
        if (!enabled) cancel(client, "Feature disabled");
        boolean saved = OvertakeClient.CONFIG.save();
        if (client.gui != null) {
            client.gui.setOverlayMessage(Component.literal("Spear Lunge: " + (enabled ? "ON" : "OFF")
                    + (saved ? "" : " (config save failed)")), true);
        }
        return saved;
    }

    private static void tick(Minecraft client) {
        OvertakeConfig config = OvertakeClient.CONFIG;
        if (config == null) return;
        boolean attackClicked = false;

        try {
            if (config.dashEnabled && client.player != null && client.level != null
                    && client.screen == null) {
                boolean lungeInput = sequence != Sequence.NONE
                        || isSupportedTriggerItem(client.player.getMainHandItem());
                if (lungeInput) attackClicked = client.options.keyAttack.consumeClick();
            }

            if (!config.dashEnabled || client.player == null || client.level == null
                    || client.screen != null || client.isPaused() || client.gameMode == null
                    || !client.player.isAlive() || client.player.isSpectator()
                    || client.player.isPassenger()) {
                if (attackClicked && client.player != null && client.player.isAlive()) {
                    client.player.swing(InteractionHand.MAIN_HAND);
                }
                if (sequence != Sequence.NONE || state == State.INPUT_PENDING || state == State.DASH_ACTIVE) {
                    cancel(client, "Player/world state became invalid");
                }
                bufferedInputTicks = 0;
                return;
            }

            if (sequence != Sequence.NONE) {
                if (!isSequenceSafe(client)) {
                    cancel(client, "Sequence interrupted");
                    return;
                }
                if (attackClicked) {
                    client.player.swing(InteractionHand.MAIN_HAND);
                }
                advanceSequence(client);
                if (state == State.DASH_ACTIVE) {
                    updateActiveDash(client);
                } else if (state == State.INPUT_PENDING) {
                    updateActivation(client);
                }
                return;
            }

            if (state == State.INPUT_PENDING) {
                client.options.keyAttack.consumeClick();
                if (client.player.getInventory().getSelectedSlot() != originalSlot) {
                    cancel(client, "Selected item changed while awaiting dash");
                    return;
                }
                updateActivation(client);
                return;
            }

            if (state == State.DASH_ACTIVE) {
                client.options.keyAttack.consumeClick();
                if (client.player.getInventory().getSelectedSlot() != originalSlot) {
                    resetState("Selected item changed during dash", client);
                    return;
                }
                updateActiveDash(client);
                return;
            }

            int heldSpearSlot = findSpearSlot(client);
            int selectedSlot = client.player.getInventory().getSelectedSlot();
            boolean holdingTriggerItem = isSupportedTriggerItem(
                    client.player.getInventory().getItem(selectedSlot));
            boolean spearAvailable = heldSpearSlot >= 0;

            if (!spearAvailable) {
                if (attackClicked) client.player.swing(InteractionHand.MAIN_HAND);
                bufferedInputTicks = 0;
                setState(State.IDLE, client, "Waiting for wind charge and spear");
                return;
            }
            if (!holdingTriggerItem) {
                bufferedInputTicks = 0;
                setState(State.IDLE, client, "Select a potion, wind charge, or empty hotbar slot");
                return;
            }
            int lungeLevel = getLungeLevel(client, client.player.getInventory().getItem(heldSpearSlot));
            OvertakeConfig.Settings settings = config.settings();

            if (settings.activationMode.equals("automatic")) {
                bufferedInputTicks = Math.max(bufferedInputTicks, 1);
            } else if (attackClicked) {
                bufferedInputTicks = settings.inputBufferTicks + 1;
            }

            boolean eligible = canAttempt(client, settings, heldSpearSlot, lungeLevel);
            if (!eligible) {
                if (attackClicked) client.player.swing(InteractionHand.MAIN_HAND);
                decrementInputBuffer();
                setState(State.IDLE, client, eligibilityReason(client, settings, heldSpearSlot, lungeLevel));
                return;
            }
            if (client.level.getGameTime() < nextEligibleTick) {
                if (attackClicked) client.player.swing(InteractionHand.MAIN_HAND);
                decrementInputBuffer();
                setState(State.COOLDOWN, client, "Attempt retry interval");
                return;
            }
            if (state == State.COOLDOWN) setState(State.IDLE, client, "Cooldown elapsed");
            if (bufferedInputTicks <= 0) {
                setState(State.READY, client, "Ready");
                return;
            }

            beginAttempt(client, heldSpearSlot, selectedSlot, settings);
        } catch (RuntimeException exception) {
            OvertakeClient.LOGGER.error("Spear Lunge failed; cancelling the active sequence.", exception);
            cancel(client, "Exception");
        }
    }

    private static void beginAttempt(Minecraft client, int foundSpearSlot, int triggerSlot,
                                     OvertakeConfig.Settings settings) {
        LocalPlayer player = client.player;
        sequencePlayer = player;
        sequenceLevel = client.level;
        originalSlot = player.getInventory().getSelectedSlot();
        spearSlot = foundSpearSlot;
        if (triggerSlot != originalSlot
                || !isSupportedTriggerItem(player.getInventory().getItem(triggerSlot))) {
            clearSequence();
            return;
        }
        initialPosition = player.position();
        initialVelocity = player.getDeltaMovement();
        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0, look.z);
        dashDirection = horizontalLook.lengthSqr() > 1.0E-6 ? horizontalLook.normalize() : Vec3.ZERO;
        attemptStartTick = client.level.getGameTime();
        nextEligibleTick = attemptStartTick + settings.dashCooldownTicks;
        attemptLungeLevel = getLungeLevel(client, player.getInventory().getItem(spearSlot));
        lastAttemptNanos = System.nanoTime();
        attemptCount++;
        bufferedInputTicks = 0;
        waitTicks = BACK_DELAY_TICKS;
        lowMotionTicks = 0;
        sequence = Sequence.SWITCH_DELAY;
        switchDelayUntilNanos = System.nanoTime()
                + settings.spearLungeSwitchDelayMs * 1_000_000L;
        setState(State.INPUT_PENDING, client, "Selecting spear; waiting configured switch delay");
        player.getInventory().setSelectedSlot(spearSlot);
        if (settings.spearLungeSwitchDelayMs == 0) performSpearJab(client, settings);
        logAttempt(client, settings);
    }

    private static void advanceSequence(Minecraft client) {
        if (sequence == Sequence.SWITCH_DELAY) {
            if (sequencePlayer.getInventory().getSelectedSlot() != spearSlot
                    || !isSequenceSafe(client)) {
                cancel(client, "Spear slot changed during switch delay");
                return;
            }
            if (System.nanoTime() < switchDelayUntilNanos) return;
            performSpearJab(client, OvertakeClient.CONFIG.settings());
            return;
        }

        if (sequence == Sequence.RESTORE_DELAY) {
            if (sequencePlayer.getInventory().getSelectedSlot() != spearSlot
                    || !isSequenceSafe(client)) {
                cancel(client, "Spear slot changed before restoration");
                return;
            }
            if (waitTicks > 0) {
                waitTicks--;
                if (waitTicks > 0) return;
            }
            restoreSlot(client);
        }
    }

    private static void performSpearJab(Minecraft client, OvertakeConfig.Settings settings) {
        ItemStack spear = sequencePlayer.getInventory().getItem(spearSlot);
        PiercingWeapon piercingWeapon = spear.get(DataComponents.PIERCING_WEAPON);
        if (!isSpear(spear) || piercingWeapon == null
                || settings.requireLungeEnchantment && attemptLungeLevel <= 0) {
            cancel(client, "Spear jab became unavailable after slot selection");
            return;
        }
        client.gameMode.piercingAttack(piercingWeapon);
        sequencePlayer.swing(InteractionHand.MAIN_HAND);
        sequence = Sequence.RESTORE_DELAY;
        waitTicks = BACK_DELAY_TICKS;
        logState(client, "Vanilla spear STAB sent after configured switch delay");
    }

    private static void updateActivation(Minecraft client) {
        if (hasPredictedLunge(client)) {
            lowMotionTicks = 0;
            setState(State.DASH_ACTIVE, client, "Client-predicted Lunge movement");
            return;
        }
        long elapsedTicks = client.level.getGameTime() - attemptStartTick;
        if (elapsedTicks >= OvertakeClient.CONFIG.settings().activationTimeoutTicks) {
            nextEligibleTick = Math.max(nextEligibleTick, client.level.getGameTime()
                    + OvertakeClient.CONFIG.settings().inputRetryTicks);
            setState(State.FAILED, client, "No Lunge movement detected before timeout");
            logState(client, "Attempt timed out; server acceptance is not observable client-side");
            setState(State.IDLE, client, "Retry available after configured interval");
        }
    }

    private static void updateActiveDash(Minecraft client) {
        Vec3 velocity = client.player.getDeltaMovement();
        double projectedSpeed = velocity.dot(dashDirection);
        double baselineSpeed = initialVelocity.dot(dashDirection);
        if (projectedSpeed - baselineSpeed < MIN_PREDICTED_LUNGE_SPEED) {
            lowMotionTicks++;
        } else {
            lowMotionTicks = 0;
        }

        long activeTicks = client.level.getGameTime() - attemptStartTick;
        if (lowMotionTicks >= 2 || activeTicks >= DASH_ACTIVE_MAX_TICKS) {
            setState(State.COOLDOWN, client, "Predicted dash ended");
        }
    }

    private static boolean hasPredictedLunge(Minecraft client) {
        if (dashDirection == null || dashDirection.lengthSqr() < 1.0E-6) return false;
        if (attemptLungeLevel <= 0) return false;
        Vec3 velocityChange = client.player.getDeltaMovement().subtract(initialVelocity);
        double forwardImpulse = velocityChange.dot(dashDirection);
        double minimumImpulse = Math.max(MIN_PREDICTED_LUNGE_SPEED, 0.458 * attemptLungeLevel * 0.30);
        return Double.isFinite(forwardImpulse) && forwardImpulse >= minimumImpulse;
    }

    private static boolean canAttempt(Minecraft client, OvertakeConfig.Settings settings,
                                      int foundSpearSlot, int lungeLevel) {
        LocalPlayer player = client.player;
        ItemStack spear = player.getInventory().getItem(foundSpearSlot);
        return isSpear(spear) && canAttackNow(spear, player, settings, lungeLevel)
                && (settings.allowAirDash || player.onGround())
                && (settings.allowVerticalDash || Math.abs(player.getXRot()) <= 75.0F);
    }

    private static boolean canAttackNow(ItemStack spear, LocalPlayer player,
                                        OvertakeConfig.Settings settings, int lungeLevel) {
        if (!isSpear(spear) || player.cannotAttackWithItem(spear, 5)) return false;
        if (settings.requireLungeEnchantment && lungeLevel <= 0) return false;
        return !player.isPassenger() && !player.isInWater() && !player.isFallFlying();
    }

    private static String eligibilityReason(Minecraft client, OvertakeConfig.Settings settings,
                                            int foundSpearSlot, int lungeLevel) {
        LocalPlayer player = client.player;
        if (!isSpear(player.getInventory().getItem(foundSpearSlot))) return "No spear";
        if (settings.requireLungeEnchantment && lungeLevel <= 0) return "Spear has no Lunge enchantment";
        if (player.cannotAttackWithItem(player.getInventory().getItem(foundSpearSlot), 5)) {
            return "Spear attack cooldown";
        }
        if (player.isInWater()) return "Lunge is disabled in water";
        if (player.isFallFlying()) return "Lunge is disabled while gliding";
        if (!settings.allowAirDash && !player.onGround()) return "Air attempts disabled";
        if (!settings.allowVerticalDash && Math.abs(player.getXRot()) > 75.0F) {
            return "Near-vertical aim is disabled";
        }
        return "Not eligible";
    }

    private static boolean isSequenceSafe(Minecraft client) {
        return client.player == sequencePlayer && client.level == sequenceLevel
                && sequencePlayer != null && client.screen == null && sequencePlayer.isAlive()
                && !sequencePlayer.isSpectator() && !sequencePlayer.isPassenger()
                && !sequencePlayer.isInWater() && !sequencePlayer.isFallFlying()
                && (sequence != Sequence.RESTORE_DELAY
                || sequencePlayer.getInventory().getSelectedSlot() == spearSlot)
                && spearSlot >= 0 && originalSlot >= 0
                && isSpear(sequencePlayer.getInventory().getItem(spearSlot))
                && isSupportedTriggerItem(sequencePlayer.getInventory().getItem(originalSlot))
                && OvertakeClient.CONFIG.dashEnabled;
    }

    private static int getLungeLevel(Minecraft client, ItemStack stack) {
        if (client.level == null) return 0;
        var enchantment = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.LUNGE);
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
    }

    private static void decrementInputBuffer() {
        if (bufferedInputTicks > 0) bufferedInputTicks--;
    }

    private static int findSpearSlot(Minecraft client) {
        for (int slot = 0; slot < 9; slot++) {
            if (isSpear(client.player.getInventory().getItem(slot))) return slot;
        }
        return -1;
    }

    private static boolean isSupportedTriggerItem(ItemStack stack) {
        return stack.isEmpty() || stack.is(Items.WIND_CHARGE) || stack.getItem() instanceof PotionItem;
    }

    private static boolean isSpear(ItemStack stack) {
        return !stack.isEmpty()
                && stack.get(DataComponents.PIERCING_WEAPON) != null
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                .getPath().contains("spear");
    }

    private static void restoreSlot(Minecraft client) {
        try {
            if (client != null && client.player == sequencePlayer
                    && originalSlot >= 0 && originalSlot < 9
                    && sequencePlayer.getInventory().getSelectedSlot() == spearSlot) {
                sequencePlayer.getInventory().setSelectedSlot(originalSlot);
            }
        } catch (RuntimeException exception) {
            OvertakeClient.LOGGER.error("Could not restore the selected slot after Spear Lunge.", exception);
        } finally {
            clearSequence();
        }
    }

    private static void cancel(Minecraft client, String reason) {
        if (sequence != Sequence.NONE) restoreSlot(client);
        clearSequence();
        bufferedInputTicks = 0;
        setState(State.IDLE, client, reason);
    }

    private static void resetState(String reason, Minecraft client) {
        clearSequence();
        bufferedInputTicks = 0;
        setState(State.IDLE, client, reason);
    }

    private static void clearSequence() {
        sequence = Sequence.NONE;
        sequencePlayer = null;
        sequenceLevel = null;
        originalSlot = -1;
        spearSlot = -1;
        waitTicks = 0;
        lowMotionTicks = 0;
    }

    private static void setState(State next, Minecraft client, String reason) {
        if (state == next) return;
        state = next;
        OvertakeConfig config = OvertakeClient.CONFIG;
        if (config != null && (config.settings().debugLogging || config.debug)) {
            logState(client, reason);
        }
    }

    private static void logAttempt(Minecraft client, OvertakeConfig.Settings settings) {
        if (!settings.debugLogging && !OvertakeClient.CONFIG.debug) return;
        ItemStack spear = client.player.getInventory().getItem(spearSlot);
        OvertakeClient.LOGGER.info(
                "Spear dash attempt #{} tick={} elapsedMs=0 spear={} lungeLevel={} ready={} yaw={} pitch={} "
                        + "velocity={} direction={} movement=predicted/server-confirmation=unavailable",
                attemptCount, attemptStartTick, spear.getHoverName().getString(),
                getLungeLevel(client, spear), !client.player.cannotAttackWithItem(spear, 5),
                client.player.getYRot(), client.player.getXRot(), initialVelocity, dashDirection);
    }

    private static void logState(Minecraft client, String reason) {
        if (client == null || client.level == null || OvertakeClient.CONFIG == null) return;
        long elapsedMillis = lastAttemptNanos == 0 ? 0
                : (System.nanoTime() - lastAttemptNanos) / 1_000_000L;
        OvertakeClient.LOGGER.info(
                "Spear dash state={} tick={} elapsedMs={} reason={} velocity={} position={} "
                        + "startPosition={} cooldownTicks={} movement=client-predicted/server-confirmation=unavailable",
                state, client.level.getGameTime(), elapsedMillis, reason,
                client.player == null ? Vec3.ZERO : client.player.getDeltaMovement(),
                client.player == null ? Vec3.ZERO : client.player.position(),
                initialPosition == null ? Vec3.ZERO : initialPosition,
                Math.max(0, nextEligibleTick - client.level.getGameTime()));
    }
}
