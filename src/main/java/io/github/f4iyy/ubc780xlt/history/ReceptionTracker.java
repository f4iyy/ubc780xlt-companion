package io.github.f4iyy.ubc780xlt.history;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Turns successive looks at the squelch into receptions: one starts when the squelch opens and ends when it
 * closes. While it lasts the strongest signal is kept.
 *
 * <p>Call {@link #poll()} regularly. A reception shorter than the polling interval can be missed.
 */
public final class ReceptionTracker {

    /** Told when a reception starts and when it ends. */
    public interface Listener {
        void started(Reception reception);

        void finished(Reception reception);
    }

    private final ReceptionSource source;
    private final Clock clock;
    private final Listener listener;
    private Reception current;

    public ReceptionTracker(ReceptionSource source, Clock clock, Listener listener) {
        this.source = source;
        this.clock = clock;
        this.listener = listener;
    }

    public boolean inReception() {
        return current != null;
    }

    /** One look at the scanner. */
    public void poll() throws IOException {
        boolean open = source.squelchOpen();
        if (open && current == null) {
            begin();
        } else if (open) {
            current = current.withMaxSignal(source.signal());
        } else if (current != null) {
            end();
        }
    }

    /** Ends the reception in progress now (link lost, tracking switched off). */
    public void abort() {
        if (current != null) {
            end();
        }
    }

    private void begin() throws IOException {
        Instant now = clock.instant();
        ReceptionSource.Snapshot s = source.snapshot();
        current = new Reception(now, Duration.ZERO, s.bank(), s.channel(), s.frequency(), s.tag(), s.modulation(),
                s.tone(), s.signal(), true);
        listener.started(current);
    }

    private void end() {
        Reception r = current.finished(Duration.between(current.start(), clock.instant()), current.maxSignal());
        current = null;
        listener.finished(r);
    }
}
