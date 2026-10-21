package name.modid.client.gui;

import name.modid.client.OvertakeClient;
import name.modid.client.config.StunKeybind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class OvertakeKeybindScreen extends Screen {
    private final Screen parent;
    private final List<BindingControl> controls = new ArrayList<>();
    private String waitingFor;
    private String message = "Select a binding, then press a letter, digit, F1-F12, or mouse button 2-5.";

    public OvertakeKeybindScreen(Screen parent) {
        super(Component.literal("OverTake Utility Keybinds"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        controls.clear();
        int center = width / 2;
        int y = Math.max(66, (height - 190) / 2);
        addBinding(center, y, "pearl", "Ender Pearl");
        addBinding(center, y + 28, "stun", "Stun");
        addBinding(center, y + 56, "crystal", "Crystal");
        addBinding(center, y + 84, "cart", "Minecart");
        addBinding(center, y + 112, "pot", "Healing potion");
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(center - 75, height - 32, 150, 20).build());
        refreshLabels();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, message, width / 2, 34,
                waitingFor == null ? 0xFFAAAAAA : 0xFFFFD56A);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (waitingFor == null) return super.keyPressed(event);
        int keyCode = event.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            waitingFor = null;
            message = "Binding cancelled.";
            return true;
        }
        String key = supportedKeyName(keyCode);
        if (key == null) {
            message = "Unsupported key. Use a letter, digit, F1-F12, or mouse button 2-5.";
            return true;
        }
        setBinding(waitingFor, key);
        waitingFor = null;
        message = "Keybind saved.";
        refreshLabels();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (waitingFor != null) {
            int button = event.button();
            if (button >= GLFW.GLFW_MOUSE_BUTTON_2 && button <= GLFW.GLFW_MOUSE_BUTTON_5) {
                setBinding(waitingFor, "mouse" + (button + 1));
                waitingFor = null;
                message = "Keybind saved.";
                refreshLabels();
            } else {
                message = "Unsupported mouse button. Use buttons 2-5.";
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addBinding(int center, int y, String id, String label) {
        Button button = Button.builder(Component.empty(), clicked -> {
                    waitingFor = id;
                    message = "Press a supported key or mouse button. Esc cancels.";
                })
                .bounds(center - 154, y, 308, 20)
                .build();
        controls.add(new BindingControl(button, id, label));
        addRenderableWidget(button);
    }

    private void refreshLabels() {
        for (BindingControl control : controls) {
            String key = switch (control.id) {
                case "pearl" -> OvertakeClient.CONFIG.pearlKeybind;
                case "stun" -> OvertakeClient.CONFIG.stunKeybind;
                case "crystal" -> OvertakeClient.CONFIG.crystalKeybind;
                case "cart" -> OvertakeClient.CONFIG.cartKeybind;
                case "pot" -> OvertakeClient.CONFIG.healingPotionKeybind;
                default -> throw new IllegalStateException("Unknown keybind control: " + control.id);
            };
            control.button.setMessage(Component.literal(control.label + ": " + key));
        }
    }

    private static String supportedKeyName(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            return Character.toString((char) keyCode);
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            return Character.toString((char) keyCode);
        }
        if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F12) {
            return "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
        }
        return null;
    }

    private static void setBinding(String id, String key) {
        String normalized = StunKeybind.normalize(key);
        switch (id) {
            case "pearl" -> OvertakeClient.CONFIG.pearlKeybind = normalized;
            case "stun" -> OvertakeClient.CONFIG.stunKeybind = normalized;
            case "crystal" -> OvertakeClient.CONFIG.crystalKeybind = normalized;
            case "cart" -> OvertakeClient.CONFIG.cartKeybind = normalized;
            case "pot" -> OvertakeClient.CONFIG.healingPotionKeybind = normalized;
            default -> throw new IllegalStateException("Unknown keybind control: " + id);
        }
        OvertakeClient.CONFIG.validate();
        OvertakeClient.CONFIG.save();
    }

    private record BindingControl(Button button, String id, String label) {
    }
}
