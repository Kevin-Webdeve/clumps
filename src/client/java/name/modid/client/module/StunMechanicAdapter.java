package name.modid.client.module;

import net.minecraft.client.Minecraft;

public interface StunMechanicAdapter {
    StunMechanicAdapter UNSUPPORTED = new StunMechanicAdapter() {
        @Override
        public boolean isSupported(Minecraft client) {
            return false;
        }

        @Override
        public String unsupportedReason() {
            return "No stun mechanic adapter installed";
        }
    };

    boolean isSupported(Minecraft client);

    String unsupportedReason();
}
