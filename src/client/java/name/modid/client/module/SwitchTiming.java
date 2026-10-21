package name.modid.client.module;

public final class SwitchTiming {
    private SwitchTiming() {
    }

    public static boolean isExpired(int pendingTicks, int delay) {
        return pendingTicks > Math.max(3, delay + 2);
    }
}
