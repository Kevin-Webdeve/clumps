package name.modid.client.config;

import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class StunKeybind {
    private StunKeybind() {
    }

    public static String normalize(String name) {
        String normalized = name.toUpperCase(Locale.ROOT);
        return normalized.startsWith("MOUSE") ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    public static boolean isValid(String name) {
        return parse(name) != null;
    }

    public static Key parse(String name) {
        if (name == null) return null;
        String normalized = name.toUpperCase(Locale.ROOT);
        if (normalized.length() == 1 && normalized.charAt(0) >= 'A' && normalized.charAt(0) <= 'Z') {
            return new Key(false, GLFW.GLFW_KEY_A + normalized.charAt(0) - 'A');
        }
        if (normalized.length() == 1 && normalized.charAt(0) >= '0' && normalized.charAt(0) <= '9') {
            return new Key(false, GLFW.GLFW_KEY_0 + normalized.charAt(0) - '0');
        }
        if (normalized.matches("F([1-9]|1[0-2])")) {
            return new Key(false, GLFW.GLFW_KEY_F1 + Integer.parseInt(normalized.substring(1)) - 1);
        }
        if (normalized.matches("MOUSE[2-5]")) {
            return new Key(true, Integer.parseInt(normalized.substring(5)) - 1);
        }
        return null;
    }

    public record Key(boolean mouse, int code) {
    }
}
