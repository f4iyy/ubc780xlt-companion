package io.github.f4iyy.ubc780xlt.transfer;

import io.github.f4iyy.ubc780xlt.protocol.CommandSender;
import io.github.f4iyy.ubc780xlt.protocol.Response;
import io.github.f4iyy.ubc780xlt.protocol.ScannerTimeoutException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** What the reader and the writer have in common: sending with one retry, progress, cancellation, warnings. */
abstract class TransferSupport {

    private final CommandSender sender;
    private final Progress progress;
    private final BooleanSupplier cancelled;
    private final List<String> warnings = new ArrayList<>();
    private final int total;
    private int done;

    TransferSupport(CommandSender sender, Progress progress, BooleanSupplier cancelled, int total) {
        this.sender = sender;
        this.progress = progress;
        this.cancelled = cancelled;
        this.total = total;
    }

    /** Warnings collected so far (an unexpected or refused answer does not stop the transfer). */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    void warn(String message) {
        warnings.add(message);
    }

    /** Starts a step: stops if cancelled, then reports the progress. */
    void step(String task) throws TransferCancelledException {
        if (cancelled.getAsBoolean()) {
            throw new TransferCancelledException();
        }
        progress.update(task, done++, total);
    }

    void finished(String task) {
        progress.update(task, total, total);
    }

    /** Sends a command; one silent answer is tolerated (the scanner may be busy) and asked again. */
    String ask(String command) throws IOException {
        try {
            return sender.send(command).text();
        } catch (ScannerTimeoutException e) {
            return sender.send(command).text();
        }
    }

    /**
     * Sends {@code MA} or {@code PM}: these answer with the channel line ({@code C001 F...}), not {@code OK}.
     * Anything else is a warning for {@code what}.
     */
    boolean tellChannel(String command, String what) throws IOException {
        String answer = ask(command);
        if (ScannerFormat.parseChannel(answer).isEmpty()) {
            warn(what + ": " + command + " answered " + answer);
            return false;
        }
        return true;
    }

    /** Sends a command that must answer {@code OK}; anything else is a warning for {@code what}. */
    boolean tell(String command, String what) throws IOException {
        Response r;
        try {
            r = sender.send(command);
        } catch (ScannerTimeoutException e) {
            r = sender.send(command);
        }
        if (!r.isOk() && !isEcho(command, r)) {
            warn(what + ": " + command + " answered " + r.text());
            return false;
        }
        return true;
    }

    /** Some commands (search limits, priority channel) are confirmed by the scanner repeating them instead of OK. */
    private static boolean isEcho(String command, Response r) {
        return r.kind() == Response.Kind.DATA && r.text().strip().equals(command.strip());
    }
}
