package io.github.f4iyy.ubc780xlt.protocol.serial;

import com.fazecast.jSerialComm.SerialPort;
import io.github.f4iyy.ubc780xlt.protocol.SerialLink;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** A jSerialComm port used as a line-oriented link ({@code \r} terminated). */
final class JSerialCommLink implements SerialLink {

    private final SerialPort port;
    private final StringBuilder pending = new StringBuilder();

    JSerialCommLink(SerialPort port) {
        this.port = port;
    }

    @Override
    public void write(String line) throws IOException {
        byte[] bytes = (line + "\r").getBytes(StandardCharsets.US_ASCII);
        int written = port.writeBytes(bytes, bytes.length);
        if (written < 0) {
            throw new IOException("Write failed on " + port.getSystemPortName());
        }
    }

    @Override
    public String readLine(int timeoutMs) throws IOException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        byte[] one = new byte[1];
        while (true) {
            int end = pending.indexOf("\r");
            if (end >= 0) {
                String line = pending.substring(0, end);
                pending.delete(0, end + 1);
                return line;
            }
            if (System.nanoTime() >= deadline) {
                return null;
            }
            if (!port.isOpen()) {
                throw new IOException("Port closed: " + port.getSystemPortName());
            }
            int n = port.readBytes(one, 1);
            if (n < 0) {
                throw new IOException("Read failed on " + port.getSystemPortName());
            }
            if (n == 1 && one[0] != '\n') {
                pending.append((char) (one[0] & 0xFF));
            }
        }
    }

    @Override
    public void discardInput() throws IOException {
        pending.setLength(0);
        int available = port.bytesAvailable();
        if (available < 0) {
            throw new IOException("Port lost: " + port.getSystemPortName());
        }
        if (available > 0) {
            port.readBytes(new byte[available], available);
        }
    }

    @Override
    public boolean isOpen() {
        return port.isOpen();
    }

    @Override
    public void close() {
        port.closePort();
    }
}
