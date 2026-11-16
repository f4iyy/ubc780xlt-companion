package io.github.f4iyy.ubc780xlt.protocol;

import java.io.IOException;

/** Something that can send one command to the scanner and return its one-line answer. */
@FunctionalInterface
public interface CommandSender {

    Response send(String command) throws IOException;
}
