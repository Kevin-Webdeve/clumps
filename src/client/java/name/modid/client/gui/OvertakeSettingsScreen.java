package name.modid.client.gui;

import name.modid.client.OvertakeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class OvertakeSettingsScreen extends Screen {
    private static final List<String> PROFILES = List.of(
            "default", "pvp", "practice", "sword", "axe", "smp", "crystal",
            "pot", "uhc", "cart", "mace", "spear", "vanilla", "tnt");

    private final Screen parent;
    private final List<SettingControl> controls = new ArrayList<>();

    public OvertakeSettingsScreen(Screen parent) {
        super(Component.literal("OverTake Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        controls.clear();
        int center = width / 2;
        int left = center - 154;
        int right = center + 2;
        int firstY = 46;
        int row = 20;

        addControl(center - 154, firstY, 308, () -> "Profile: " + OvertakeClient.CONFIG.profile,
                () -> {
                    int current = PROFILES.indexOf(OvertakeClient.CONFIG.profile);
                    OvertakeClient.SWITCHER.cancel(Minecraft.getInstance(), false);
                    OvertakeClient.CONFIG.profile = PROFILES.get((current + 1) % PROFILES.size());
                });
        addToggle(left, firstY + row, "Master", () -> OvertakeClient.CONFIG.masterEnabled,
                () -> {
                    OvertakeClient.CONFIG.masterEnabled = !OvertakeClient.CONFIG.masterEnabled;
                    if (!OvertakeClient.CONFIG.masterEnabled) {
                        OvertakeClient.SWITCHER.cancel(Minecraft.getInstance(), false);
                    }
                });
        addToggle(right, firstY + row, "Weapon switching", () -> OvertakeClient.CONFIG.settings().switchEnabled,
                () -> OvertakeClient.CONFIG.settings().switchEnabled = !OvertakeClient.CONFIG.settings().switchEnabled);
        addToggle(left, firstY + row * 2, "Mace assistance",
                () -> OvertakeClient.CONFIG.settings().maceEnabled,
                () -> OvertakeClient.CONFIG.settings().maceEnabled = !OvertakeClient.CONFIG.settings().maceEnabled);
        addToggle(right, firstY + row * 2, "Mace auto-attack",
                () -> OvertakeClient.CONFIG.settings().maceAutoAttack,
                () -> OvertakeClient.CONFIG.settings().maceAutoAttack = !OvertakeClient.CONFIG.settings().maceAutoAttack);
        addToggle(left, firstY + row * 3, "HUD",
                () -> OvertakeClient.CONFIG.settings().hudEnabled,
                () -> OvertakeClient.CONFIG.settings().hudEnabled = !OvertakeClient.CONFIG.settings().hudEnabled);
        addToggle(right, firstY + row * 3, "Readiness sound",
                () -> OvertakeClient.CONFIG.settings().soundAlerts,
                () -> OvertakeClient.CONFIG.settings().soundAlerts = !OvertakeClient.CONFIG.settings().soundAlerts);
        addControl(left, firstY + row * 4, 152,
                () -> "Targets: " + OvertakeClient.CONFIG.settings().targetType,
                () -> OvertakeClient.CONFIG.settings().targetType =
                        "players".equals(OvertakeClient.CONFIG.settings().targetType) ? "living" : "players");
        addControl(right, firstY + row * 4, 152,
                () -> "Mace mode: " + OvertakeClient.CONFIG.settings().maceMode,
                () -> OvertakeClient.CONFIG.settings().maceMode =
                        "assist".equals(OvertakeClient.CONFIG.settings().maceMode) ? "manual" : "assist");
        addControl(left, firstY + row * 5, 152,
                () -> "Switch delay: - (" + OvertakeClient.CONFIG.settings().switchDelay + ")",
                () -> OvertakeClient.CONFIG.settings().switchDelay =
                        Math.max(0, OvertakeClient.CONFIG.settings().switchDelay - 1));
        addControl(right, firstY + row * 5, 152,
                () -> "Switch delay: + (" + OvertakeClient.CONFIG.settings().switchDelay + ")",
                () -> OvertakeClient.CONFIG.settings().switchDelay =
                        Math.min(100, OvertakeClient.CONFIG.settings().switchDelay + 1));
        addControl(left, firstY + row * 6, 152,
                () -> String.format("Mace threshold: - (%.1f)", OvertakeClient.CONFIG.settings().maceThreshold),
                () -> OvertakeClient.CONFIG.settings().maceThreshold =
                        Math.max(2.0, OvertakeClient.CONFIG.settings().maceThreshold - 0.5));
        addControl(right, firstY + row * 6, 152,
                () -> String.format("Mace threshold: + (%.1f)", OvertakeClient.CONFIG.settings().maceThreshold),
                () -> OvertakeClient.CONFIG.settings().maceThreshold =
                        Math.min(20.0, OvertakeClient.CONFIG.settings().maceThreshold + 0.5));
        addControl(left, firstY + row * 7, 152,
                () -> "Wind Charge delay: -1 (" + OvertakeClient.CONFIG.pearlWindChargeDelayTicks + ")",
                () -> OvertakeClient.CONFIG.pearlWindChargeDelayTicks =
                        Math.max(0, OvertakeClient.CONFIG.pearlWindChargeDelayTicks - 1));
        addControl(right, firstY + row * 7, 152,
                () -> "Wind Charge delay: +1 (" + OvertakeClient.CONFIG.pearlWindChargeDelayTicks + ")",
                () -> OvertakeClient.CONFIG.pearlWindChargeDelayTicks =
                        Math.min(20, OvertakeClient.CONFIG.pearlWindChargeDelayTicks + 1));

        int footerY = firstY + row * 8 + 4;
        addRenderableWidget(Button.builder(Component.literal("Utility keybinds"),
                        button -> Minecraft.getInstance().setScreen(new OvertakeKeybindScreen(this)))
                .bounds(left, footerY, 152, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(right, footerY, 152, 20).build());
        refreshLabels();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font,
                "Settings save to config\\overtake.json.",
                width / 2, 30, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addToggle(int x, int y, String label, Supplier<Boolean> value, Runnable change) {
        addControl(x, y, 152, () -> label + ": " + (value.get() ? "ON" : "OFF"), change);
    }

    private void addControl(int x, int y, int width, Supplier<String> label, Runnable change) {
        Button button = Button.builder(Component.literal(label.get()), clicked -> {
                    change.run();
                    OvertakeClient.CONFIG.validate();
                    OvertakeClient.CONFIG.save();
                    refreshLabels();
                })
                .bounds(x, y, width, 20)
                .build();
        controls.add(new SettingControl(button, label));
        addRenderableWidget(button);
    }

    private void refreshLabels() {
        for (SettingControl control : controls) {
            control.button.setMessage(Component.literal(control.label.get()));
        }
    }

    private record SettingControl(Button button, Supplier<String> label) {
    }
}
