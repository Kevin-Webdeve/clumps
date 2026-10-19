package name.modid.client.module;

import name.modid.client.OvertakeClient;
import name.modid.client.OvertakeRuntime;
import name.modid.client.config.OvertakeConfig;
import name.modid.client.config.StunKeybind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;

public final class StunSlam {
    private static boolean keyWasDown;
    private static LocalPlayer comboPlayer;
    private static LivingEntity comboTarget;
    private static int followUpSlot = -1;
    private static boolean maceFollowUp;

    private StunSlam() {
    }

    public static void tick(Minecraft client) {
        if (OvertakeClient.CONFIG == null) return;
        boolean down = isKeyDown(client, OvertakeClient.CONFIG.stunKeybind);
        boolean pressed = down && !keyWasDown;
        keyWasDown = down;
        if (client.player == null || client.level == null || client.screen != null
                || client.isPaused() || client.gameMode == null) {
            clearCombo();
            return;
        }

        if (comboPlayer != null) {
            completeFollowUp(client);
            return;
        }
        if (!pressed) return;

        var player = client.player;
        var settings = OvertakeClient.CONFIG.settings();
        int axeSlot = findAxeSlot(player);
        int selectedFollowUpSlot = findFollowUpSlot(player, settings);
        if (axeSlot < 0 || selectedFollowUpSlot < 0) return;
        ItemStack followUp = player.getInventory().getItem(selectedFollowUpSlot);
        boolean selectedMaceFollowUp = followUp.is(Items.MACE);
        if (selectedMaceFollowUp && settings.stunMaceFallCheck
                && !OvertakeRuntime.isFallingAtLeast(client, 1.5)) return;

        LivingEntity target = findTarget(client, settings);
        selectSlot(client, axeSlot);
        attackAndSwing(client, target);
        comboPlayer = player;
        comboTarget = target;
        followUpSlot = selectedFollowUpSlot;
        maceFollowUp = selectedMaceFollowUp;
    }

    private static void completeFollowUp(Minecraft client) {
        var player = client.player;
        var settings = OvertakeClient.CONFIG.settings();
        if (player != comboPlayer
                || !isFollowUpItem(player.getInventory().getItem(followUpSlot), maceFollowUp)
                || (maceFollowUp && settings.stunMaceFallCheck
                && !OvertakeRuntime.isFallingAtLeast(client, 1.5))
                || (comboTarget != null && !validTarget(player, comboTarget, settings.stunReach))) {
            clearCombo();
            return;
        }

        selectSlot(client, followUpSlot);
        attackAndSwing(client, comboTarget);
        clearCombo();
    }

    private static boolean isFollowUpItem(ItemStack stack, boolean mace) {
        return mace ? stack.is(Items.MACE) : stack.is(ItemTags.SWORDS);
    }

    private static void clearCombo() {
        comboPlayer = null;
        comboTarget = null;
        followUpSlot = -1;
        maceFollowUp = false;
    }

    private static boolean isKeyDown(Minecraft client, String keyName) {
        StunKeybind.Key key = StunKeybind.parse(keyName);
        if (key == null || client.getWindow() == null
                || (key.mouse() && key.code() == GLFW.GLFW_MOUSE_BUTTON_1)) return false;
        long window = client.getWindow().handle();
        return key.mouse()
                ? GLFW.glfwGetMouseButton(window, key.code()) == GLFW.GLFW_PRESS
                : GLFW.glfwGetKey(window, key.code()) == GLFW.GLFW_PRESS;
    }

    private static int findAxeSlot(Player player) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).getItem() instanceof AxeItem) return slot;
        }
        return -1;
    }

    private static int findFollowUpSlot(Player player, OvertakeConfig.Settings settings) {
        int maceSlot = -1;
        int bestSwordSlot = -1;
        double bestSwordDamage = Double.NEGATIVE_INFINITY;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.MACE) && maceSlot < 0) maceSlot = slot;
            if (stack.is(ItemTags.SWORDS)) {
                var attributes = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
                double damage = attributes == null ? 0.0
                        : attributes.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND);
                if (damage > bestSwordDamage) {
                    bestSwordDamage = damage;
                    bestSwordSlot = slot;
                }
            }
        }
        return switch (settings.followUpMode) {
            case "MACE_ONLY" -> maceSlot;
            case "SWORD_ONLY" -> bestSwordSlot;
            default -> maceSlot >= 0 ? maceSlot : bestSwordSlot;
        };
    }

    private static LivingEntity findTarget(Minecraft client, OvertakeConfig.Settings settings) {
        var player = client.player;
        boolean crosshairAllowed = !"NEAREST_ONLY".equals(settings.targetMode);
        if (crosshairAllowed && client.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity target
                && validTarget(player, target, settings.stunReach)) {
            return target;
        }
        if ("CROSSHAIR_ONLY".equals(settings.targetMode)) return null;

        double coneLimit = Math.cos(Math.toRadians(Math.min(settings.stunAimCone, 180.0)));
        Vec3 look = player.getLookAngle().normalize();
        return player.level().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(settings.stunReach))
                .stream()
                .filter(target -> validTarget(player, target, settings.stunReach))
                .filter(target -> {
                    Vec3 direction = target.getEyePosition().subtract(player.getEyePosition()).normalize();
                    return look.dot(direction) >= coneLimit;
                })
                .min(Comparator.comparingInt((LivingEntity target) -> target instanceof Player ? 0 : 1)
                        .thenComparingDouble(player::distanceTo))
                .orElse(null);
    }

    private static boolean validTarget(Player player, LivingEntity target, double reach) {
        return target != player && target.isAlive() && target.isAttackable() && !target.isInvulnerable()
                && player.distanceTo(target) <= reach;
    }

    private static void selectSlot(Minecraft client, int slot) {
        client.player.getInventory().setSelectedSlot(slot);
    }

    private static void attackAndSwing(Minecraft client, LivingEntity target) {
        if (target != null) client.gameMode.attack(client.player, target);
        client.player.swing(InteractionHand.MAIN_HAND);
        client.player.resetAttackStrengthTicker();
    }
}
