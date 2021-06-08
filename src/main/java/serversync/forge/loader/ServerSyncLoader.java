package serversync.forge.loader;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
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

    private Thread serverThread;

    public ServerSyncLoader() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        // ** We should be in the root folder for Minecraft as our CWD when forge loads.

        try (Stream<Path> fileStream = Files.list(Paths.get(""))) {
            List<Path> serversync = fileStream
                .parallel()
                .filter(f -> f.getFileName().toString().matches("serversync-(\\d+\\.\\d+\\.\\d+)(?:-(\\w+)|-(\\w+\\.\\d+))*\\.jar"))
                .collect(Collectors.toList());

            if (serversync.size() < 1) {
                LOGGER.error("Failed to find ServerSync, have you added it to your minecraft folder?");
                return;
            }

            if (serversync.size() > 1) {
                LOGGER.error(String.format(
                    "Found multiple versions of ServerSync: %s, remove the excess versions.",
                    serversync
                ));
                return;
            }

            // We have enforced that serversync exists already so this hackery is less awful than usual.
            // Loads our serversync.jar into a class loader and reflectively calls the start server sequence
            URLClassLoader child =  new URLClassLoader(new URL[]{serversync.get(0).toUri().toURL()}, this.getClass().getClassLoader());
            Class<?> serversyncClass = Class.forName("com.superzanti.serversync.ServerSync", true, child);
            Object ssi = serversyncClass.newInstance();
            Field rootDir = serversyncClass.getDeclaredField("rootDir");
            rootDir.set(null, Paths.get(""));
            Method runServer = serversyncClass.getDeclaredMethod("runInServerMode");
            runServer.setAccessible(true);
            LOGGER.info("Starting ServerSync server via forge loader: " + Reference.VERSION);
            serverThread = (Thread) runServer.invoke(ssi);
        } catch (IOException | ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            // Filth!!, but meh
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        serverThread.interrupt();
    }
}
