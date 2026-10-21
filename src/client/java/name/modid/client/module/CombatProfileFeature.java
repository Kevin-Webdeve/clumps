package name.modid.client.module;

import name.modid.client.OvertakeClient;
import name.modid.client.config.OvertakeConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class CombatProfileFeature {
    private static boolean crystalKeyWasDown;
    private static boolean cartKeyWasDown;
    private static boolean potionKeyWasDown;
    private static int sprintResetPhase;
    private static LocalPlayer sprintResetPlayer;
    private static ClientLevel sprintResetLevel;
    private static boolean originalForwardDown;
    private static boolean originalBackDown;

    private CombatProfileFeature() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(CombatProfileFeature::tick);
    }

    private static void tick(Minecraft client) {
        OvertakeConfig config = OvertakeClient.CONFIG;
        if (config == null) return;

        boolean crystalDown = StunSlam.isKeyDown(client, config.crystalKeybind);
        boolean cartDown = StunSlam.isKeyDown(client, config.cartKeybind);
        boolean potionDown = StunSlam.isKeyDown(client, config.healingPotionKeybind);
        boolean crystalPressed = crystalDown && !crystalKeyWasDown;
        boolean cartPressed = cartDown && !cartKeyWasDown;
        boolean potionPressed = potionDown && !potionKeyWasDown;
        crystalKeyWasDown = crystalDown;
        cartKeyWasDown = cartDown;
        potionKeyWasDown = potionDown;
        tickSprintReset(client);

        if (!config.masterEnabled || client.player == null || client.level == null
                || client.screen != null || client.isPaused() || client.gameMode == null
                || !client.player.isAlive() || client.player.isSpectator()
                || SpearLungeFeature.isRunning() || PearlFeature.isActionActiveThisTick()) {
            return;
        }

        OvertakeConfig.Settings settings = config.settings();
        if ("sword".equals(config.profile) && settings.swordTriggerbot) {
            tickSwordTriggerbot(client, settings);
        } else {
            cancelSprintReset(client);
        }

        switch (config.profile) {
            case "crystal" -> {
                if (crystalPressed) useCrystalAction(client);
            }
            case "cart" -> {
                if (cartPressed) useCartAction(client);
            }
            case "pot" -> {
                if (potionPressed) useHealingPotion(client);
            }
            default -> {
            }
        }
    }

    private static void tickSwordTriggerbot(Minecraft client, OvertakeConfig.Settings settings) {
        LocalPlayer player = client.player;
        if (sprintResetPhase != 0 || client.options.keyAttack.isDown()
                || !player.getMainHandItem().is(ItemTags.SWORDS)
                || !(client.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity target)
                || target == player || !target.isAlive() || !target.isAttackable()
                || ("players".equals(settings.targetType)
                && !(target instanceof net.minecraft.world.entity.player.Player))
                || !player.isWithinAttackRange(target.getBoundingBox(), target.getPickRadius())
                || player.getAttackStrengthScale(0.5F) < 1.0F) {
            return;
        }
        client.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
        player.resetAttackStrengthTicker();
        if (settings.swordSprintReset && player.isSprinting() && client.options.keyUp.isDown()) {
            beginSprintReset(client, player);
        }
    }

    private static void beginSprintReset(Minecraft client, LocalPlayer player) {
        sprintResetPlayer = player;
        sprintResetLevel = client.level;
        originalForwardDown = client.options.keyUp.isDown();
        originalBackDown = client.options.keyDown.isDown();
        sprintResetPhase = 1;
        applySprintTap(client, true);
    }

    private static void tickSprintReset(Minecraft client) {
        if (sprintResetPhase == 0) return;
        if (!OvertakeClient.CONFIG.masterEnabled || client.player != sprintResetPlayer
                || client.level != sprintResetLevel
                || !sprintResetPlayer.isAlive() || client.screen != null) {
            cancelSprintReset(client);
            return;
        }
        if (sprintResetPhase == 1) {
            applySprintTap(client, false);
            sprintResetPhase = 2;
        } else {
            cancelSprintReset(client);
        }
    }

    private static void applySprintTap(Minecraft client, boolean back) {
        client.options.keyUp.setDown(!back);
        client.options.keyDown.setDown(back);
    }

    private static void cancelSprintReset(Minecraft client) {
        if (sprintResetPhase == 0) return;
        client.options.keyUp.setDown(originalForwardDown);
        client.options.keyDown.setDown(originalBackDown);
        sprintResetPhase = 0;
        sprintResetPlayer = null;
        sprintResetLevel = null;
    }

    private static void useCrystalAction(Minecraft client) {
        if (!(client.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            message(client, "Aim at obsidian, bedrock, or a respawn anchor.");
            return;
        }
        var state = client.level.getBlockState(hit.getBlockPos());
        if (state.getBlock() instanceof RespawnAnchorBlock) {
            int charge = state.getValue(RespawnAnchorBlock.CHARGE);
            if (charge < 4) {
                useItemOnBlock(client, hit, Items.GLOWSTONE, "No Glowstone in the hotbar or offhand.");
            } else if (!client.level.environmentAttributes()
                    .getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, hit.getBlockPos())) {
                useEmptyHandOnBlock(client, hit);
            } else {
                message(client, "The charged Respawn Anchor cannot explode in this dimension.");
            }
            return;
        }
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK)) {
            useItemOnBlock(client, hit, Items.END_CRYSTAL, "No End Crystal in the hotbar or offhand.");
            return;
        }
        message(client, "Aim at obsidian, bedrock, or a respawn anchor.");
    }

    private static void useCartAction(Minecraft client) {
        LocalPlayer player = client.player;
        if (!(player.getMainHandItem().getItem() instanceof BowItem)
                && !(player.getMainHandItem().getItem() instanceof CrossbowItem)) {
            message(client, "Hold a Bow or Crossbow and aim at a rail.");
            return;
        }
        if (!(client.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !client.level.getBlockState(hit.getBlockPos()).is(BlockTags.RAILS)) {
            message(client, "Aim at a rail to place a TNT Minecart.");
            return;
        }
        useItemOnBlock(client, hit, Items.TNT_MINECART, "No TNT Minecart in the hotbar or offhand.");
    }

    private static void useHealingPotion(Minecraft client) {
        LocalPlayer player = client.player;
        int slot = -1;
        for (int index = 0; index < 9; index++) {
            if (isHealingPotion(player.getInventory().getItem(index))) {
                slot = index;
                break;
            }
        }
        if (slot < 0) {
            message(client, "No splash Healing Potion in the hotbar.");
            return;
        }
        ItemStack potion = player.getInventory().getItem(slot);
        if (player.getCooldowns().isOnCooldown(potion)) {
            message(client, "Healing Potion is on cooldown.");
            return;
        }
        useMainHandItem(client, slot);
    }

    private static boolean isHealingPotion(ItemStack stack) {
        if (!stack.is(Items.SPLASH_POTION)) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.potion()
                .filter(potion -> potion.is(Potions.HEALING) || potion.is(Potions.STRONG_HEALING))
                .isPresent();
    }

    private static void useItemOnBlock(Minecraft client, BlockHitResult hit, Item item, String missingMessage) {
        LocalPlayer player = client.player;
        if (player.getOffhandItem().is(item)) {
            useOffhandOnBlock(client, hit);
            return;
        }
        int slot = findSlot(player, item);
        if (slot < 0) {
            message(client, missingMessage);
            return;
        }
        int previousSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(slot);
        try {
            InteractionResult result = client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            if (result.consumesAction()) player.swing(InteractionHand.MAIN_HAND);
            else message(client, "The item use was not accepted.");
        } finally {
            if (client.player == player && player.isAlive()
                    && player.getInventory().getSelectedSlot() == slot) {
                player.getInventory().setSelectedSlot(previousSlot);
            }
        }
    }

    private static void useOffhandOnBlock(Minecraft client, BlockHitResult hit) {
        InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.OFF_HAND, hit);
        if (result.consumesAction()) client.player.swing(InteractionHand.OFF_HAND);
        else message(client, "The item use was not accepted.");
    }

    private static void useEmptyHandOnBlock(Minecraft client, BlockHitResult hit) {
        LocalPlayer player = client.player;
        if (player.getOffhandItem().isEmpty()) {
            InteractionResult result = client.gameMode.useItemOn(player, InteractionHand.OFF_HAND, hit);
            if (result.consumesAction()) player.swing(InteractionHand.OFF_HAND);
            else message(client, "The anchor interaction was not accepted.");
            return;
        }
        int emptySlot = findEmptySlot(player);
        if (emptySlot < 0) {
            message(client, "An empty hand is needed to activate a charged Respawn Anchor.");
            return;
        }
        int previousSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(emptySlot);
        try {
            InteractionResult result = client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            if (result.consumesAction()) player.swing(InteractionHand.MAIN_HAND);
            else message(client, "The anchor interaction was not accepted.");
        } finally {
            if (client.player == player && player.isAlive()
                    && player.getInventory().getSelectedSlot() == emptySlot) {
                player.getInventory().setSelectedSlot(previousSlot);
            }
        }
    }

    private static void useMainHandItem(Minecraft client, int slot) {
        LocalPlayer player = client.player;
        int previousSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(slot);
        try {
            InteractionResult result = client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            if (result.consumesAction()) player.swing(InteractionHand.MAIN_HAND);
            else message(client, "The Healing Potion use was not accepted.");
        } finally {
            if (client.player == player && player.isAlive()
                    && player.getInventory().getSelectedSlot() == slot) {
                player.getInventory().setSelectedSlot(previousSlot);
            }
        }
    }

    private static int findSlot(LocalPlayer player, Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(item)) return slot;
        }
        return -1;
    }

    private static int findEmptySlot(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private static void message(Minecraft client, String text) {
        client.gui.setOverlayMessage(Component.literal("[OT] " + text), false);
    }
}
