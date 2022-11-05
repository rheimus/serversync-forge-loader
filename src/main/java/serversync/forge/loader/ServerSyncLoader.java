package serversync.forge.loader;

import serversync.forge.loader.commands.ServerSyncCommand;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import serversync.generated.Reference;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mod(Reference.MODID)
public class ServerSyncLoader {
    private static final Logger LOGGER = LogManager.getLogger();
    private static Thread SERVER_THREAD;
    private static List<Path> SERVERSYNC_BIN;

    public ServerSyncLoader() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        SERVER_THREAD = LoadServerSync(this);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SERVER_THREAD.interrupt();
    }

    @SubscribeEvent
    public void RegisterCommads(RegisterCommandsEvent event) {
        ServerSyncCommand.register(event.getDispatcher(), this);
    }

    private static boolean CheckServerSync() {
        // ** We should be in the root folder for Minecraft as our CWD when forge loads.
        try (Stream<Path> fileStream = Files.list(Paths.get(""))) {
            SERVERSYNC_BIN = fileStream
                .parallel()
                .filter(f -> f.getFileName().toString().matches("serversync-(\\d+\\.\\d+\\.\\d+)(?:-(\\w+)|-(\\w+\\.\\d+))*\\.jar"))
                .collect(Collectors.toList());

            if (SERVERSYNC_BIN.size() < 1) {
                LOGGER.error("Failed to find ServerSync, have you added it to your minecraft folder?");
                return false;
            }

            if (SERVERSYNC_BIN.size() > 1) {
                LOGGER.error(String.format(
                    "Found multiple versions of ServerSync: %s, remove the excess versions.",
                    SERVERSYNC_BIN
                ));
                return false;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to find ServerSync!");
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static Thread LoadServerSync(ServerSyncLoader parent) {
        if (!CheckServerSync() || parent == null)
            return null;

        try {
            URLClassLoader child =  new URLClassLoader(new URL[]{SERVERSYNC_BIN.get(0).toUri().toURL()}, parent.getClass().getClassLoader());
            Class<?> serversyncClass = Class.forName("com.superzanti.serversync.ServerSync", true, child);
            Object ssi = serversyncClass.newInstance();
            Field rootDir = serversyncClass.getDeclaredField("rootDir");
            rootDir.set(null, Paths.get(""));
            Method runServer = serversyncClass.getDeclaredMethod("runInServerMode");
            runServer.setAccessible(true);
            LOGGER.info("Starting ServerSync server via forge loader: " + Reference.VERSION);
            SERVER_THREAD = (Thread) runServer.invoke(ssi);
        } catch (IOException | ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            LOGGER.error("Failed to run ServerSync!");
            e.printStackTrace();
            SERVER_THREAD = null;
        }

        return SERVER_THREAD;
    }

    public static boolean StopServerSync() {
        if (SERVER_THREAD != null) {
            if (!SERVER_THREAD.isAlive())
                return true;

            try {
                SERVER_THREAD.interrupt();
                return true;
            } catch (Exception e) {
                return false;
            }
        } else return false;
    }
}
