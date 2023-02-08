package serversync.forge.loader.commands;

import serversync.forge.loader.ServerSyncLoader;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.io.IOException;

public class ServerSyncCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ServerSyncLoader parent) {
        dispatcher.register(Commands.literal("serversync")
            .then(Commands.literal("start").executes(ctx -> { return start(parent); }))
            .then(Commands.literal("restart").executes(ctx -> { return stop() + start(parent); }))
            .then(Commands.literal("stop").executes(ctx -> { return stop(); }))
        );
    }

    private static int start(ServerSyncLoader parent) {
        return (ServerSyncLoader.LoadServerSync(parent) != null ? 0 : 1);
    }

    private static int stop() {
        return (ServerSyncLoader.StopServerSync() ? 0 : 1);
    }
}
