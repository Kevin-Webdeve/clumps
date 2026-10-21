package name.modid.client.module;

import name.modid.client.OvertakeClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class PearlFeature {
    private static final int ATTEMPT_TIMEOUT_TICKS = 60;

    private static boolean keyWasDown;
    private static boolean actionActiveThisTick;
    private static LocalPlayer attemptPlayer;
    private static ClientLevel attemptLevel;
    private static Vec3 pearlPosition;
    private static Vec3 pearlVelocity;
    private static long pearlLaunchTick;
    private static long attemptStartTick;
    private static int originalSlot = -1;
    private static int expectedSlot = -1;
    private static int firstAimTick = -1;
    private static int windChargeReadyTick = -1;
    private static int fallbackReadyTick = -1;
    private static int noPredictionTicks;
    private static float aimYaw;
    private static float aimPitch;

    private PearlFeature() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(PearlFeature::tick);
    }

    public static boolean isActionActiveThisTick() {
        return actionActiveThisTick || attemptPlayer != null;
    }

    private static void tick(Minecraft client) {
        actionActiveThisTick = false;
        if (attemptPlayer != null) {
            tickCatch(client);
            return;
        }
        if (OvertakeClient.CONFIG == null) return;
        boolean down = StunSlam.isKeyDown(client, OvertakeClient.CONFIG.pearlKeybind);
        boolean pressed = down && !keyWasDown;
        keyWasDown = down;
        if (!pressed || client.player == null || client.level == null || client.screen != null
                || client.isPaused() || client.gameMode == null || !client.player.isAlive()
                || client.player.isSpectator() || client.player.isUsingItem()
                || SpearLungeFeature.isRunning()) {
            return;
        }

        LocalPlayer player = client.player;
        int pearlSlot = findSlot(player, Items.ENDER_PEARL);
        int windChargeSlot = findSlot(player, Items.WIND_CHARGE);
        boolean offhandPearl = player.getOffhandItem().is(Items.ENDER_PEARL);
        boolean offhandWindCharge = player.getOffhandItem().is(Items.WIND_CHARGE);
        if (pearlSlot < 0 && !offhandPearl) {
            showMessage(client, "No Ender Pearl in your hotbar or offhand.");
            return;
        }
        if (windChargeSlot < 0 && !offhandWindCharge) {
            showMessage(client, "A Wind Charge is required in your hotbar or offhand.");
            return;
        }

        InteractionHand pearlHand = pearlSlot >= 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack pearl = pearlHand == InteractionHand.MAIN_HAND
                ? player.getInventory().getItem(pearlSlot) : player.getOffhandItem();
        if (player.getCooldowns().isOnCooldown(pearl)) {
            showMessage(client, "Ender Pearl is on cooldown.");
            return;
        }

        int previousSlot = player.getInventory().getSelectedSlot();
        if (pearlHand == InteractionHand.MAIN_HAND && pearlSlot != previousSlot) {
            player.getInventory().setSelectedSlot(pearlSlot);
        }

        actionActiveThisTick = true;
        if (!useItem(client, player, pearlHand,
                -1, pearlSlot)) {
            return;
        }

        Vec3 launchPosition = player.position();
        Vec3 movement = player.getKnownMovement();
        Vec3 launchVelocity = player.getLookAngle().scale(1.5)
                .add(movement.x, player.onGround() ? 0.0 : movement.y, movement.z);
        attemptPlayer = player;
        attemptLevel = client.level;
        pearlPosition = launchPosition;
        pearlVelocity = launchVelocity;
        pearlLaunchTick = player.tickCount;
        attemptStartTick = pearlLaunchTick;
        originalSlot = previousSlot;
        if (windChargeSlot >= 0) {
            player.getInventory().setSelectedSlot(windChargeSlot);
            expectedSlot = windChargeSlot;
        } else {
            if (player.getInventory().getSelectedSlot() != previousSlot) {
                player.getInventory().setSelectedSlot(previousSlot);
            }
            expectedSlot = previousSlot;
        }
        firstAimTick = -1;
        windChargeReadyTick = -1;
        fallbackReadyTick = -1;
        noPredictionTicks = 0;
        aimYaw = player.getYRot();
        aimPitch = player.getXRot();
        Aim initialAim = predictCatch(client, player);
        if (initialAim != null) {
            updateAim(client, initialAim);
            firstAimTick = player.tickCount;
        }
    }

    private static void tickCatch(Minecraft client) {
        LocalPlayer player = attemptPlayer;
        ClientLevel level = attemptLevel;
        if (client.player != player || client.level != level || !player.isAlive()
                || player.isSpectator()) {
            clearAttempt();
            return;
        }
        if (client.screen != null) {
            cancelAttempt(client, "Catch cancelled while a screen is open.");
            return;
        }
        if (client.isPaused()) return;
        if (client.gameMode == null || SpearLungeFeature.isRunning()) {
            cancelAttempt(client, "Catch cancelled by another active action.");
            return;
        }
        if (player.getInventory().getSelectedSlot() != expectedSlot) {
            cancelAttempt(client, "Catch cancelled because the hotbar selection changed.");
            return;
        }
        if (player.tickCount - attemptStartTick > ATTEMPT_TIMEOUT_TICKS) {
            cancelAttempt(client, "Pearl catch prediction timed out.");
            return;
        }

        Aim target = predictCatch(client, player);
        if (target == null) {
            if (++noPredictionTicks > 20) {
                if (fallbackReadyTick < 0) fallbackReadyTick = player.tickCount;
                if (player.tickCount - fallbackReadyTick >= OvertakeClient.CONFIG.pearlWindChargeDelayTicks) {
                    throwWindCharge(client, player, firstAimTick < 0 ? null : new Aim(aimYaw, aimPitch));
                }
            }
            return;
        }
        noPredictionTicks = 0;
        fallbackReadyTick = -1;
        updateAim(client, target);
        if (firstAimTick < 0) firstAimTick = player.tickCount;

        if (player.tickCount <= firstAimTick
                || Math.abs(Mth.wrapDegrees(target.yaw - aimYaw)) > 4.0F
                || Math.abs(target.pitch - aimPitch) > 4.0F) {
            windChargeReadyTick = -1;
            return;
        }

        if (windChargeReadyTick < 0) windChargeReadyTick = player.tickCount;
        if (player.tickCount - windChargeReadyTick >= OvertakeClient.CONFIG.pearlWindChargeDelayTicks) {
            throwWindCharge(client, player, new Aim(aimYaw, aimPitch));
        }
    }

    private static void throwWindCharge(Minecraft client, LocalPlayer player, Aim rotation) {
        int windChargeSlot = findSlot(player, Items.WIND_CHARGE);
        InteractionHand windChargeHand = windChargeSlot >= 0
                ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().is(Items.WIND_CHARGE) ? InteractionHand.OFF_HAND : null;
        if (windChargeHand == null) {
            cancelAttempt(client, "No Wind Charge remains in your hotbar or offhand.");
            return;
        }
        int selectedSlot = player.getInventory().getSelectedSlot();
        if (windChargeHand == InteractionHand.MAIN_HAND && windChargeSlot != selectedSlot) {
            player.getInventory().setSelectedSlot(windChargeSlot);
        }
        int restoreSlot = originalSlot;
        clearAttempt();
        actionActiveThisTick = true;
        useItem(client, player, windChargeHand,
                windChargeHand == InteractionHand.MAIN_HAND && windChargeSlot != restoreSlot
                        ? restoreSlot : -1,
                windChargeHand == InteractionHand.MAIN_HAND ? windChargeSlot : selectedSlot, rotation);
    }

    private static Aim predictCatch(Minecraft client, LocalPlayer player) {
        int latency = 50;
        if (client.getConnection() != null) {
            var info = client.getConnection().getPlayerInfo(player.getUUID());
            if (info != null && info.getLatency() > 0) latency = Math.min(info.getLatency(), 400);
        }
        double pingTicks = latency / 50.0;

        Vec3 playerVelocity = player.getKnownMovement();
        double verticalVelocity = playerVelocity.y;
        int fullPingTicks = (int) pingTicks;
        for (int tick = 0; tick < fullPingTicks && !player.isPassenger(); tick++) {
            verticalVelocity = (verticalVelocity - 0.08) * 0.98;
        }
        double fraction = pingTicks - fullPingTicks;
        if (fraction > 0.0 && !player.isPassenger()) {
            verticalVelocity += ((verticalVelocity - 0.08) * 0.98 - verticalVelocity) * fraction;
        }
        if (player.onGround()) verticalVelocity = 0.0;

        Vec3 targetVelocity = new Vec3(playerVelocity.x, verticalVelocity, playerVelocity.z);
        Vec3 targetEye = player.getEyePosition();
        Vec3 targetPosition = player.position().add(targetVelocity.scale(pingTicks));
        double elapsed = Math.max(0.0, player.tickCount - pearlLaunchTick + pingTicks);
        PearlPhysics.ProjectileState pearl = PearlPhysics.advanceProjectile(
                toVector(pearlPosition), toVector(pearlVelocity), elapsed);
        double impactTime = PearlPhysics.findCatchTime(
                toVector(targetEye), toVector(targetVelocity), pearl.position(), pearl.velocity());
        if (impactTime <= 0.0) return null;

        Vec3 pearlAtImpact = toVec(PearlPhysics.advanceProjectile(
                pearl.position(), pearl.velocity(), impactTime).position()).add(0.0, -0.2, 0.0);
        Vec3 playerAtImpact = targetPosition.add(targetVelocity.scale(impactTime));
        Vec3 direction = pearlAtImpact.subtract(playerAtImpact);
        if (direction.lengthSqr() < 1.0E-8) return null;
        return rotationFromDirection(direction);
    }

    private static Aim rotationFromDirection(Vec3 direction) {
        float yaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
        return new Aim(yaw, Mth.clamp(pitch, -90.0F, 90.0F));
    }

    private static PearlPhysics.Vector toVector(Vec3 vector) {
        return new PearlPhysics.Vector(vector.x, vector.y, vector.z);
    }

    private static Vec3 toVec(PearlPhysics.Vector vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static void updateAim(Minecraft client, Aim target) {
        double sensitivity = client.options.sensitivity().get();
        double sensitivityScale = sensitivity * 0.6 + 0.2;
        double sensitivityStep = sensitivityScale * sensitivityScale * sensitivityScale * 8.0;
        float gcd = (float) sensitivityStep * 0.15F;
        if (gcd <= 0.0) return;
        float yawDifference = Mth.wrapDegrees(target.yaw - aimYaw);
        int maxSteps = Math.max(1, (int) (60.0F / gcd));
        int yawSteps = Mth.clamp(Math.round(yawDifference / gcd), -maxSteps, maxSteps);
        int pitchSteps = Mth.clamp(Math.round((target.pitch - aimPitch) / gcd), -maxSteps, maxSteps);
        int maxPitchUp = (int) Math.floor((90.0F - aimPitch) / gcd);
        int maxPitchDown = (int) Math.ceil((-90.0F - aimPitch) / gcd);
        pitchSteps = Mth.clamp(pitchSteps, maxPitchDown, maxPitchUp);
        aimYaw += yawSteps * gcd;
        aimPitch = Mth.clamp(aimPitch + pitchSteps * gcd, -90.0F, 90.0F);
    }

    private static int findSlot(LocalPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(item)) return slot;
        }
        return -1;
    }

    private static boolean useItem(Minecraft client, LocalPlayer player, InteractionHand hand,
                                   int restoreSlot, int usedSlot) {
        return useItem(client, player, hand, restoreSlot, usedSlot, null);
    }

    private static boolean useItem(Minecraft client, LocalPlayer player, InteractionHand hand,
                                   int restoreSlot, int usedSlot, Aim rotation) {
        float originalYaw = player.getYRot();
        float originalPitch = player.getXRot();
        try {
            if (rotation != null) {
                player.setYRot(rotation.yaw);
                player.setXRot(rotation.pitch);
            }
            InteractionResult result = client.gameMode.useItem(player, hand);
            if (result.consumesAction()) {
                player.swing(hand);
                return true;
            }
            showMessage(client, "Item use was not accepted.");
            return false;
        } finally {
            if (rotation != null) {
                player.setYRot(originalYaw);
                player.setXRot(originalPitch);
            }
            if (restoreSlot >= 0 && client.player == player && player.isAlive()
                    && player.getInventory().getSelectedSlot() == usedSlot) {
                player.getInventory().setSelectedSlot(restoreSlot);
            }
        }
    }

    private static void cancelAttempt(Minecraft client, String message) {
        clearAttempt();
        showMessage(client, message);
    }

    private static void clearAttempt() {
        attemptPlayer = null;
        attemptLevel = null;
        pearlPosition = null;
        pearlVelocity = null;
        pearlLaunchTick = 0;
        attemptStartTick = 0;
        originalSlot = -1;
        expectedSlot = -1;
        firstAimTick = -1;
        windChargeReadyTick = -1;
        fallbackReadyTick = -1;
        noPredictionTicks = 0;
    }

    private static void showMessage(Minecraft client, String message) {
        client.gui.setOverlayMessage(Component.literal("[OT] " + message), false);
    }

    private record Aim(float yaw, float pitch) {
    }

}
