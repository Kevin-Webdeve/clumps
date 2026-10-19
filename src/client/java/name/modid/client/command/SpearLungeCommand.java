package name.modid.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import name.modid.client.OvertakeClient;
import name.modid.client.module.SpearLungeFeature;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class SpearLungeCommand {
    private SpearLungeCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ot")
                .then(literal("sp")
                        .executes(context -> {
                            SpearLungeFeature.setEnabled(context.getSource().getClient(),
                                    !OvertakeClient.CONFIG.dashEnabled);
                            return 1;
                        })
                        .then(literal("on").executes(context -> {
                            SpearLungeFeature.setEnabled(context.getSource().getClient(), true);
                            return 1;
                        }))
                        .then(literal("off").executes(context -> {
                            SpearLungeFeature.setEnabled(context.getSource().getClient(), false);
                            return 1;
                        }))
                        .then(delayCommand())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource>
    delayCommand() {
        return literal("delay")
                .then(argument("milliseconds", IntegerArgumentType.integer(0, 5000))
                        .executes(context -> {
                            int delay = IntegerArgumentType.getInteger(context, "milliseconds");
                            OvertakeClient.CONFIG.settings().spearLungeSwitchDelayMs = delay;
                            boolean saved = OvertakeClient.CONFIG.save();
                            context.getSource().sendFeedback(Component.literal(
                                    "Spear Lunge switch delay: " + delay + " ms"
                                            + (saved ? "." : " (could not save config).")));
                            return saved ? 1 : 0;
                        }));
    }
}
