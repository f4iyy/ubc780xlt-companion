package io.github.f4iyy.ubc780xlt.protocol;

import java.io.IOException;
import java.util.List;

/** Lists serial ports and opens them. Implemented by the real port layer and by the simulator. */
public interface LinkFactory {

    /** Names of the serial ports currently available (e.g. {@code COM3}, {@code ttyUSB0}). */
    List<String> listPorts();

    /** Opens {@code port} at {@code baud} (8 data bits, no parity, 1 stop bit). */
    SerialLink open(String port, int baud) throws IOException;
}
