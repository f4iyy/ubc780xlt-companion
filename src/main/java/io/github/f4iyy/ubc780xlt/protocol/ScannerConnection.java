package io.github.f4iyy.ubc780xlt.protocol;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Command channel to the scanner: one command at a time, with a pause between commands.
 * Every frame is traced at DEBUG on the {@code protocol.wire} logger.
 */
public final class ScannerConnection implements AutoCloseable {

    private static final Logger WIRE = LogManager.getLogger("protocol.wire");

    private final SerialLink link;
    private final int timeoutMs;
    private final long interCommandNanos;
    private long lastExchangeNanos;
    private boolean hasExchanged;

    public ScannerConnection(SerialLink link, ConnectionSettings settings) {
        this.link = link;
        this.timeoutMs = settings.timeoutMs();
        this.interCommandNanos = settings.interCommandDelayMs() * 1_000_000L;
    }

    /**
     * Sends one command and waits for its answer.
     *
     * @throws ScannerTimeoutException if the scanner stays silent
     * @throws IOException if the link is broken
     */
    public synchronized Response send(String command) throws IOException {
        pauseBetweenCommands();
        link.discardInput();
        WIRE.debug("TX {}", command);
        link.write(command);
        String line = link.readLine(timeoutMs);
        markExchange();
        if (line == null) {
            WIRE.debug("RX <timeout>");
            throw new ScannerTimeoutException(command, timeoutMs);
        }
        WIRE.debug("RX {}", line);
        return Response.parse(line);
    }

    /**
     * Sends a command whose answer is several lines (e.g. {@code LCD}) and reads exactly {@code lineCount} of them.
     *
     * @throws ScannerTimeoutException if the scanner stops answering before the last line
     */
    public synchronized List<String> sendLines(String command, int lineCount) throws IOException {
        pauseBetweenCommands();
        link.discardInput();
        WIRE.debug("TX {}", command);
        link.write(command);
        List<String> lines = new ArrayList<>(lineCount);
        try {
            while (lines.size() < lineCount) {
                String line = link.readLine(timeoutMs);
                if (line == null) {
                    WIRE.debug("RX <timeout after {} of {} lines>", lines.size(), lineCount);
                    throw new ScannerTimeoutException(command, timeoutMs);
                }
                WIRE.debug("RX {}", line);
                lines.add(line);
            }
        } finally {
            markExchange();
        }
        return lines;
    }

    public boolean isOpen() {
        return link.isOpen();
    }

    @Override
    public void close() {
        link.close();
    }

    private void markExchange() {
        lastExchangeNanos = System.nanoTime();
        hasExchanged = true;
    }

    private void pauseBetweenCommands() throws IOException {
        if (!hasExchanged || interCommandNanos <= 0) {
            return;
        }
        long remaining = interCommandNanos - (System.nanoTime() - lastExchangeNanos);
        if (remaining > 0) {
            try {
                Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while waiting between commands");
            }
        }
    }
}
