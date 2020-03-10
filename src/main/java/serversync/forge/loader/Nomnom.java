package serversync.forge.loader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

public class Nomnom implements Runnable {
    private InputStream input;
    private Consumer<String> consumer;

    public Nomnom(InputStream input, Consumer<String> consumer) {
        this.input = input;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        new BufferedReader(new InputStreamReader(input)).lines().forEach(consumer);
    }
}
