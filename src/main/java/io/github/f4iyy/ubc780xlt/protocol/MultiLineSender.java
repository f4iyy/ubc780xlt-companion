package io.github.f4iyy.ubc780xlt.protocol;

import java.io.IOException;
import java.util.List;

/** A {@link CommandSender} that can also read commands answering several lines (such as {@code LCD}). */
public interface MultiLineSender extends CommandSender {

    List<String> sendLines(String command, int lineCount) throws IOException;
}
