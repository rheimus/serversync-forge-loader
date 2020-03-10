package serversync.forge.loader;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import serversync.generated.Reference;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.System.getenv;

@Mod(Reference.MODID)
public class ServerSyncLoader {
    private static final Logger LOGGER = LogManager.getLogger();
    private Process serversyncProcess;

    public ServerSyncLoader() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        // ** WE should be in the root folder for Minecraft as our CWD when forge loads.

        try (Stream<Path> fileStream = Files.list(Paths.get(""))) {
            List<Path> serversync = fileStream
                .parallel()
                .filter(f -> f.getFileName().toString().matches("serversync-\\d\\.\\d\\.\\d\\.jar"))
                .collect(Collectors.toList());

            if (serversync.size() > 1) {
                LOGGER.error(String.format(
                    "Found multiple versions of ServerSync: %s, remove the excess versions.",
                    serversync
                ));
                return;
            }

            try {
                ProcessBuilder pb = new ProcessBuilder();
                pb.command("java", "-jar", serversync.get(0).getFileName().toString(), "server");
                LOGGER.info(String.format("Starting ServerSync with command: %s", pb.command()));
                serversyncProcess = pb.start();

                Nomnom yummyInfo = new Nomnom(serversyncProcess.getInputStream(), LOGGER::info);
                Nomnom yummyError = new Nomnom(serversyncProcess.getErrorStream(), LOGGER::error);
                ExecutorService service = Executors.newFixedThreadPool(2);
                service.submit(yummyInfo);
                service.submit(yummyError);
            } catch (IOException pe) {
                LOGGER.error("Failed to start a ServerSync process!");
                pe.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        // Run cleanup when forge exits.
        if (serversyncProcess != null && serversyncProcess.isAlive()) {
            LOGGER.info("Shutting down ServerSync");
            serversyncProcess.destroy();
        }
    }
}
