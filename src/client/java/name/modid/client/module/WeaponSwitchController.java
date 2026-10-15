package name.modid.client.module;

import name.modid.client.OvertakeClient;
import name.modid.client.config.OvertakeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class WeaponSwitchController {
    public enum State {
        IDLE, EVALUATING, SWITCH_REQUESTED, ITEM_VERIFIED, ACTION_READY, RESTORE_PENDING, CANCELLED
    }

    private State state = State.IDLE;
    private int requestedSlot = -1;
    private int previousSlot = -1;
    private int observedSlot = -1;
    private int remainingDelay;
    private int pendingTicks;
    private int retryCooldown;
    private boolean switchedByController;
    private boolean manualChanged;
    private boolean manualBlocked;
    private boolean verifyNextTick;
    private String actionId = "none";
    private int actionPriority;
    private LocalPlayer cachedPlayer;
    private int maceSlot = -1;
    private int swordSlot = -1;
    private int axeSlot = -1;
    private int shieldSlot = -1;
    private int pearlSlot = -1;
    private int totemSlot = -1;
    private int configuredMaceSlot;
    private int configuredSwordSlot;
    private int configuredAxeSlot;

    public void refreshHotbar(LocalPlayer player) {
        cachedPlayer = player;
        maceSlot = swordSlot = axeSlot = shieldSlot = pearlSlot = totemSlot = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (maceSlot < 0 && slotMatches(slot, configuredMaceSlot) && stack.is(Items.MACE)) maceSlot = slot;
            if (swordSlot < 0 && slotMatches(slot, configuredSwordSlot) && stack.is(ItemTags.SWORDS)) swordSlot = slot;
            if (axeSlot < 0 && slotMatches(slot, configuredAxeSlot) && stack.getItem() instanceof AxeItem) axeSlot = slot;
            if (shieldSlot < 0 && stack.is(Items.SHIELD)) shieldSlot = slot;
            if (pearlSlot < 0 && stack.is(Items.ENDER_PEARL)) pearlSlot = slot;
            if (totemSlot < 0 && stack.is(Items.TOTEM_OF_UNDYING)) totemSlot = slot;
        }
    }

    public void refreshHotbar(LocalPlayer player, OvertakeConfig.Settings settings) {
        configuredMaceSlot = settings.maceSlot;
        configuredSwordSlot = settings.swordSlot;
        configuredAxeSlot = settings.axeSlot;
        refreshHotbar(player);
    }

    private static boolean slotMatches(int slot, int configuredSlot) {
        return configuredSlot == 0 || configuredSlot == slot + 1;
    }

    public int findMaceSlot(LocalPlayer player) {
        if (cachedPlayer != player) refreshHotbar(player);
        return maceSlot;
    }

    public int findAxeSlot(LocalPlayer player) {
        if (cachedPlayer != player) refreshHotbar(player);
        return axeSlot;
    }

    public int findPreferredMeleeSlot(LocalPlayer player, List<String> priority) {
        if (cachedPlayer != player) refreshHotbar(player);
        int selectedSlot = player.getInventory().getSelectedSlot();
        if (isSpear(player.getInventory().getItem(selectedSlot))) return selectedSlot;
        for (String kind : priority) {
            if ("sword".equals(kind) && swordSlot >= 0) return swordSlot;
            if ("axe".equals(kind) && axeSlot >= 0) return axeSlot;
        }
        return -1;
    }

    private static boolean isSpear(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("spear");
    }

    public int findShieldSlot(LocalPlayer player) {
        if (cachedPlayer != player) refreshHotbar(player);
        return shieldSlot;
    }

    public int findPearlSlot(LocalPlayer player) {
        if (cachedPlayer != player) refreshHotbar(player);
        return pearlSlot;
    }

    public int findTotemSlot(LocalPlayer player) {
        if (cachedPlayer != player) refreshHotbar(player);
        return totemSlot;
    }

    public boolean hasOffhandTotem(LocalPlayer player) {
        return player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    public boolean hasOffhandShield(LocalPlayer player) {
        return player.getOffhandItem().is(Items.SHIELD);
    }

    public boolean hasOffhandPearl(LocalPlayer player) {
        return player.getOffhandItem().is(Items.ENDER_PEARL);
    }

    public void tick(Minecraft client, boolean actionValid, int desiredSlot, int delay,
                     boolean restoreSlot, boolean manualOverride) {
        LocalPlayer player = client.player;
        if (player == null) {
            cancel(client, false, "Player unavailable");
            return;
        }
        int selected = player.getInventory().getSelectedSlot();
        boolean playerChangedSlot = observedSlot >= 0 && selected != observedSlot;
        observedSlot = selected;
        if (!actionValid) {
            manualBlocked = false;
            retryCooldown = 0;
            cancel(client, restoreSlot, "Action prerequisites no longer valid");
            return;
        }
        if (playerChangedSlot) {
            if (manualOverride) {
                manualBlocked = true;
                if (state != State.IDLE) {
                    manualChanged = true;
                    cancel(client, false, "Manual hotbar change");
                }
                return;
            }
            if (state != State.IDLE) {
                manualChanged = true;
                if (selected != requestedSlot) {
                    remainingDelay = Math.max(0, delay);
                    pendingTicks = 0;
                    state = State.SWITCH_REQUESTED;
                    verifyNextTick = false;
                }
            }
        }
        if (!manualOverride) {
            manualBlocked = false;
        }
        if (manualBlocked) {
            return;
        }
        if (retryCooldown > 0) {
            retryCooldown--;
            return;
        }
        if (desiredSlot < 0 || desiredSlot > 8) {
            cancel(client, restoreSlot, "Requested item unavailable");
            return;
        }
        if (state != State.IDLE && requestedSlot != desiredSlot) {
            cancel(client, restoreSlot, "Preferred item changed");
            selected = player.getInventory().getSelectedSlot();
        }
        if (state == State.IDLE) {
            state = State.EVALUATING;
            OvertakeClient.cancellationReason = "None";
            requestedSlot = desiredSlot;
            actionId = player.getInventory().getItem(desiredSlot).is(Items.MACE) ? "mace-select" : "melee-select";
            actionPriority = "mace-select".equals(actionId) ? 100 : 50;
            previousSlot = selected;
            remainingDelay = Math.max(0, delay);
            pendingTicks = 0;
            manualChanged = false;
            switchedByController = false;
            verifyNextTick = false;
            if (selected == desiredSlot) {
                state = State.ITEM_VERIFIED;
            } else {
                state = State.SWITCH_REQUESTED;
            }
        }
        if (state == State.SWITCH_REQUESTED) {
            if (++pendingTicks > Math.max(3, delay + 2)) {
                cancel(client, restoreSlot, "Switch request expired");
                return;
            }
            if (remainingDelay > 0) {
                remainingDelay--;
                return;
            }
            if (selected != requestedSlot) {
                player.getInventory().setSelectedSlot(requestedSlot);
                observedSlot = requestedSlot;
                switchedByController = true;
                verifyNextTick = true;
                state = State.ACTION_READY;
                return;
            }
            state = State.ITEM_VERIFIED;
        }
        if (state == State.ITEM_VERIFIED || state == State.ACTION_READY) {
            if (verifyNextTick) {
                verifyNextTick = false;
                if (player.getInventory().getSelectedSlot() != requestedSlot) {
                    retryCooldown = 10;
                    cancel(client, false, "Selected-slot change was not confirmed");
                    return;
                }
            }
            state = State.ACTION_READY;
        }
    }

    public void cancel(Minecraft client, boolean restoreSlot) {
        cancel(client, restoreSlot, "Pending switch cancelled");
    }

    private void cancel(Minecraft client, boolean restoreSlot, String reason) {
        if (state == State.IDLE && !switchedByController) {
            return;
        }
        state = State.CANCELLED;
        OvertakeClient.cancellationReason = reason;
        if (restoreSlot && switchedByController && !manualChanged && client.player != null
                && client.player.getInventory().getSelectedSlot() == requestedSlot && previousSlot >= 0) {
            state = State.RESTORE_PENDING;
            client.player.getInventory().setSelectedSlot(previousSlot);
            observedSlot = previousSlot;
        }
        clear();
    }

    public boolean isSwitching() {
        return state == State.SWITCH_REQUESTED || state == State.RESTORE_PENDING;
    }

    public boolean isActionReady(int slot) {
        return state == State.ACTION_READY && requestedSlot == slot;
    }

    public String status() {
        return actionId.equals("none") ? state.name() : state.name() + " (" + actionId + ", priority " + actionPriority + ")";
    }

    private void clear() {
        state = State.IDLE;
        requestedSlot = -1;
        previousSlot = -1;
        remainingDelay = 0;
        pendingTicks = 0;
        actionId = "none";
        actionPriority = 0;
        switchedByController = false;
        manualChanged = false;
        verifyNextTick = false;
    }
}
