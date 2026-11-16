package io.github.f4iyy.ubc780xlt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.history.ReceptionSource.Snapshot;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReceptionTrackerTest {

    /** A clock the test moves by hand. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-06T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(long millis) {
            now = now.plusMillis(millis);
        }
    }

    private static final class FakeSource implements ReceptionSource {
        boolean open;
        int signal = 50;
        boolean fail;
        int snapshots;

        @Override
        public boolean squelchOpen() throws IOException {
            if (fail) {
                throw new IOException("Cable removed");
            }
            return open;
        }

        @Override
        public Snapshot snapshot() {
            snapshots++;
            return new Snapshot(signal, "145.0150", 1, 12, "RELAY", "FM", "67.0");
        }

        @Override
        public int signal() {
            return signal;
        }
    }

    private TestClock clock;
    private FakeSource source;
    private List<Reception> started;
    private List<Reception> finished;
    private ReceptionTracker tracker;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        source = new FakeSource();
        started = new ArrayList<>();
        finished = new ArrayList<>();
        tracker = new ReceptionTracker(source, clock, new ReceptionTracker.Listener() {
            @Override
            public void started(Reception r) {
                started.add(r);
            }

            @Override
            public void finished(Reception r) {
                finished.add(r);
            }
        });
    }

    @Test
    void nothingHappensWhileTheSquelchStaysClosed() throws IOException {
        tracker.poll();
        tracker.poll();
        assertTrue(started.isEmpty());
        assertTrue(finished.isEmpty());
        assertFalse(tracker.inReception());
    }

    @Test
    void aReceptionStartsWhenTheSquelchOpensAndEndsWhenItCloses() throws IOException {
        source.open = true;
        tracker.poll();

        assertTrue(tracker.inReception());
        assertEquals(1, started.size());
        Reception s = started.get(0);
        assertTrue(s.inProgress());
        assertEquals(Instant.parse("2026-11-06T10:00:00Z"), s.start());
        assertEquals(12, s.channel());
        assertEquals(1, s.bank());
        assertEquals("145.0150", s.frequency());
        assertEquals("RELAY", s.tag());
        assertEquals("FM", s.modulation());
        assertEquals("67.0", s.tone());

        clock.advance(3400);
        source.open = false;
        tracker.poll();

        assertFalse(tracker.inReception());
        assertEquals(1, finished.size());
        Reception f = finished.get(0);
        assertFalse(f.inProgress());
        assertEquals(Duration.ofMillis(3400), f.duration());
        assertEquals(s.start(), f.start());
    }

    @Test
    void theStrongestSignalIsKept() throws IOException {
        source.open = true;
        source.signal = 60;
        tracker.poll();
        for (int level : new int[] {90, 140, 70}) {
            clock.advance(100);
            source.signal = level;
            tracker.poll();
        }
        source.open = false;
        clock.advance(100);
        tracker.poll();

        assertEquals(140, finished.get(0).maxSignal());
        assertEquals(1, source.snapshots, "the display is read once, when the reception starts");
    }

    @Test
    void twoReceptionsInARow() throws IOException {
        for (int i = 0; i < 2; i++) {
            source.open = true;
            tracker.poll();
            clock.advance(500);
            source.open = false;
            tracker.poll();
            clock.advance(500);
        }
        assertEquals(2, started.size());
        assertEquals(2, finished.size());
    }

    @Test
    void abortEndsTheReceptionInProgressNow() throws IOException {
        source.open = true;
        tracker.poll();
        clock.advance(2000);
        tracker.abort();

        assertFalse(tracker.inReception());
        assertEquals(Duration.ofSeconds(2), finished.get(0).duration());

        tracker.abort(); // nothing in progress: nothing happens
        assertEquals(1, finished.size());
    }

    @Test
    void aBrokenLinkIsPassedOn() {
        source.fail = true;
        assertThrows(IOException.class, tracker::poll);
    }

    @Test
    void recordHelpers() {
        Reception r = new Reception(Instant.EPOCH, Duration.ZERO, 0, 0, "", "", "", "", 10, true);
        assertEquals(30, r.withMaxSignal(30).maxSignal());
        assertEquals(10, r.withMaxSignal(5).maxSignal(), "never goes down");
        Reception done = r.finished(Duration.ofSeconds(1), 99);
        assertEquals(99, done.maxSignal());
        assertFalse(done.inProgress());
    }
}
