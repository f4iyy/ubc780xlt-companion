package io.github.f4iyy.ubc780xlt.protocol.serial;

import com.fazecast.jSerialComm.SerialPort;
import io.github.f4iyy.ubc780xlt.protocol.LinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.SerialLink;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Real serial ports through jSerialComm (Windows, Linux, macOS). */
public final class JSerialCommFactory implements LinkFactory {

    @Override
    public List<String> listPorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(SerialPort::getSystemPortName)
                .sorted()
                .toList();
    }

    @Override
    public SerialLink open(String portName, int baud) throws IOException {
        SerialPort port = SerialPort.getCommPort(portName);
        port.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 50, 0);
        if (!port.openPort()) {
            throw new IOException("Cannot open " + portName);
        }
        return new JSerialCommLink(port);
    }
}
