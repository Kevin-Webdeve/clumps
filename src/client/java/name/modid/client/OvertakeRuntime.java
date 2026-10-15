package name.modid.client;

import name.modid.client.config.OvertakeConfig;
import name.modid.client.module.WeaponSwitchController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.List;

public final class OvertakeRuntime {
    private OvertakeRuntime() {
    }

    public static LivingEntity findTarget(Minecraft client, OvertakeConfig.Settings settings) {
        if (!(client.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity target)
                || target == client.player || !target.isAlive()) {
            return null;
        }
        if ("players".equals(settings.targetType) && !(target instanceof Player)) {
            return null;
        }
        double distance = client.player.distanceTo(target);
        double maximumRange = targetRange(client, settings.meleeRange);
        if (settings.maceEnabled && isFallingAtLeast(client, settings.maceThreshold)
                && OvertakeClient.SWITCHER.findMaceSlot(client.player) >= 0) {
            maximumRange = Math.max(maximumRange, Math.min(settings.maceRange, 4.5));
        }
        if (settings.stunEnabled) {
            maximumRange = Math.max(maximumRange, targetRange(client, settings.stunRange));
        }
        return distance <= maximumRange ? target : null;
    }

    public static double targetRange(Minecraft client, double configuredRange) {
        if (client.player == null) return 0.0;
        String itemId = BuiltInRegistries.ITEM.getKey(client.player.getMainHandItem().getItem()).getPath();
        double weaponLimit = itemId.contains("spear") ? 4.5 : 3.0;
        return Math.min(configuredRange, weaponLimit);
    }

    public static boolean isMaceReady(Minecraft client, LivingEntity target, OvertakeConfig.Settings settings) {
        if (target == null || !settings.maceEnabled || !isFallingAtLeast(client, settings.maceThreshold)) {
            return false;
        }
        double distance = client.player.distanceTo(target);
        return distance <= Math.min(settings.maceRange, 4.5)
                && OvertakeClient.SWITCHER.findMaceSlot(client.player) >= 0;
    }

    public static boolean isFallingAtLeast(Minecraft client, double fallDistance) {
        return client.player != null && client.player.isAlive() && !client.player.onGround()
                && client.player.getDeltaMovement().y < 0.0
                && client.player.fallDistance >= fallDistance;
    }

    public static void renderHud(GuiGraphics graphics, OvertakeConfig config, WeaponSwitchController switcher,
                                 boolean maceReady, String actionState, String cancellationReason, double targetDistance, long tickNanos) {
        OvertakeConfig.Settings settings = config.settings();
        List<String> lines = new ArrayList<>(8);
        lines.add("OverTake: " + (config.masterEnabled ? "ON" : "OFF") + " [" + config.profile + "]");
        lines.add("Switch " + onOff(settings.switchEnabled) + " | Mace " + onOff(settings.maceEnabled)
                + " | Stun " + onOff(settings.stunEnabled));
        var player = Minecraft.getInstance().player;
        lines.add("Selected: " + (player == null ? "unavailable" : player.getMainHandItem().getHoverName().getString()));
        int preferredSlot = player == null ? -1 : maceReady
                ? switcher.findMaceSlot(player)
                : switcher.findPreferredMeleeSlot(player, settings.weaponPriority);
        String preferredName = player == null || preferredSlot < 0 ? "none"
                : player.getInventory().getItem(preferredSlot).getHoverName().getString();
        lines.add("Preferred: " + preferredName);
        lines.add("Target: " + (targetDistance < 0 ? "none" : String.format("%.1f blocks", targetDistance)));
        lines.add("Mace: " + (maceReady ? "READY" : "not ready") + " | Switch: " + switcher.status());
        if (player != null) {
            lines.add("Shield slot " + slot(switcher.findShieldSlot(player)) + "/offhand "
                    + onOff(switcher.hasOffhandShield(player)) + " | Pearl slot "
                    + slot(switcher.findPearlSlot(player)) + "/offhand " + onOff(switcher.hasOffhandPearl(player))
                    + " | Totem slot "
                    + slot(switcher.findTotemSlot(player)) + " | Offhand totem "
                    + onOff(switcher.hasOffhandTotem(player)));
        }
        lines.add("Action: " + actionState);
        if (!"None".equals(cancellationReason)) lines.add("Cancelled: " + cancellationReason);
        if ("practice".equals(config.profile) && player != null) {
            lines.add(String.format("Practice: vertical speed %.3f, fall distance %.2f",
                    player.getDeltaMovement().y, player.fallDistance));
        }
        if (config.debug) lines.add(String.format("Tick: %.2f ms", tickNanos / 1_000_000.0));

        int x = settings.hudX;
        int y = settings.hudY;
        int width = 0;
        for (String line : lines) width = Math.max(width, Minecraft.getInstance().font.width(line));
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale((float) settings.hudScale, (float) settings.hudScale);
        graphics.fill(-4, -4, width + 4, lines.size() * 10 + 2, 0x99000000);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(Minecraft.getInstance().font, lines.get(i), 0, i * 10, 0xFFFFFFFF, true);
        }
        graphics.pose().popMatrix();
    }

    private static String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private static String slot(int slot) {
        return slot < 0 ? "missing" : Integer.toString(slot + 1);
    }
}
