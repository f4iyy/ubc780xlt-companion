package io.github.f4iyy.ubc780xlt.protocol.sim;

import io.github.f4iyy.ubc780xlt.protocol.LinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.SerialLink;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

/** Simulated serial ports, each optionally holding a {@link FakeScanner}. */
public final class FakeLinkFactory implements LinkFactory {

    private final Map<String, FakeScanner> ports = new TreeMap<>();

    /** One port, {@code SIM1}, with a BC780XLT at 9600 baud. */
    public static FakeLinkFactory withSampleScanner() {
        FakeLinkFactory f = new FakeLinkFactory();
        f.addPort("SIM1", FakeScanner.bc780xlt());
        return f;
    }

    /** @param scanner the device on this port, or {@code null} for an empty port */
    public FakeLinkFactory addPort(String name, FakeScanner scanner) {
        ports.put(name, scanner);
        return this;
    }

    @Override
    public List<String> listPorts() {
        return List.copyOf(ports.keySet());
    }

    @Override
    public SerialLink open(String port, int baud) throws IOException {
        if (!ports.containsKey(port)) {
            throw new IOException("No such port: " + port);
        }
        FakeScanner scanner = ports.get(port);
        if (scanner != null && scanner.isUnplugged()) {
            throw new IOException("Port unavailable: " + port);
        }
        return new FakeLink(scanner, baud);
    }

    private static final class FakeLink implements SerialLink {
        private final FakeScanner scanner;
        private final int baud;
        private final Queue<String> answers = new ArrayDeque<>();
        private boolean open = true;

        FakeLink(FakeScanner scanner, int baud) {
            this.scanner = scanner;
            this.baud = baud;
        }

        private void checkUsable() throws IOException {
            if (!open) {
                throw new IOException("Link closed");
            }
            if (scanner != null && scanner.isUnplugged()) {
                throw new IOException("Cable removed");
            }
        }

        @Override
        public void write(String line) throws IOException {
            checkUsable();
            // An empty port, or a wrong speed, gives no usable answer.
            if (scanner != null && scanner.baud() == baud) {
                String out = scanner.handle(line);
                if (out != null) {
                    answers.addAll(List.of(out.split("\n")));
                }
            }
        }

        @Override
        public String readLine(int timeoutMs) throws IOException {
            checkUsable();
            return answers.poll();
        }

        @Override
        public void discardInput() throws IOException {
            checkUsable();
            answers.clear();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
