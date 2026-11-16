package io.github.f4iyy.ubc780xlt.protocol;

import java.io.IOException;

/** One open serial connection. Lines are terminated by a carriage return ({@code \r}) only. */
public interface SerialLink extends AutoCloseable {

    /** Sends {@code line} followed by {@code \r}. */
    void write(String line) throws IOException;

    /**
     * Reads one line (without the terminator).
     *
     * @return the line, or {@code null} when nothing complete arrived within {@code timeoutMs}
     * @throws IOException when the link is broken (cable removed, port closed)
     */
    String readLine(int timeoutMs) throws IOException;

    /** Drops anything received and not yet read. */
    void discardInput() throws IOException;

    boolean isOpen();

    @Override
    void close();
}
