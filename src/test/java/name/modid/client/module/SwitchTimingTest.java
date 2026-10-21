package name.modid.client.module;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchTimingTest {
    @Test
    void allowsMinimumRetryWindowForZeroDelay() {
        assertFalse(SwitchTiming.isExpired(3, 0));
        assertTrue(SwitchTiming.isExpired(4, 0));
    }

    @Test
    void expiryIncludesConfiguredDelayAndTwoTickGrace() {
        assertFalse(SwitchTiming.isExpired(12, 10));
        assertTrue(SwitchTiming.isExpired(13, 10));
    }
}
