package dev.datnguyen.missionscheduler.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetworkLabTest {
    @Test void datagramArrivesThroughRealSocketAndPauseStopsNewPackets() throws Exception {
        try (var fixture = new Fixture()) {
            var lab = fixture.lab;
            lab.start(); lab.tick(); lab.stop();
            for (int i = 0; i < 100 && lab.snapshot().received() == 0; i++) { Thread.sleep(10); lab.tick(); }
            var s = lab.snapshot();
            assertEquals(1, s.sent()); assertEquals(1, s.received());
            assertEquals(0, s.dropped()); assertEquals(0, s.inFlight());
            assertTrue(s.meanLatencyMs() >= 35);
            assertNotEquals(s.senderPort(), s.receiverPort());
            lab.tick(); assertEquals(1, lab.snapshot().sent());
        }
    }
    @Test void outageDropsPacketsAndBackupRestoresDelivery() throws Exception {
        try (var fixture = new Fixture()) {
            var lab = fixture.lab;
            lab.configure(new NetworkLab.Controls(false, false, 0, 0));
            lab.start(); lab.tick(); lab.stop();
            assertEquals("UNREACHABLE", lab.snapshot().route());
            assertEquals(1, lab.snapshot().dropped()); assertEquals(0, lab.snapshot().received());
            lab.configure(new NetworkLab.Controls(false, true, 0, 0));
            lab.start(); lab.tick(); lab.stop();
            for (int i = 0; i < 100 && lab.snapshot().received() == 0; i++) { Thread.sleep(10); lab.tick(); }
            var s = lab.snapshot();
            assertEquals("BACKUP", s.route()); assertEquals(1, s.received());
            assertEquals(50, s.deliveryPercent()); assertTrue(s.meanLatencyMs() >= 110);
            assertEquals(s.sent(), s.received() + s.dropped() + s.inFlight());
            lab.reset(); assertEquals(0, lab.snapshot().sent()); assertFalse(lab.snapshot().running());
            assertEquals("PRIMARY", lab.snapshot().route());
        }
    }
    @Test void rejectsUnboundedControls() {
        assertThrows(IllegalArgumentException.class, () -> new NetworkLab.Controls(true, true, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new NetworkLab.Controls(true, true, 401, 0));
        assertThrows(IllegalArgumentException.class, () -> new NetworkLab.Controls(true, true, 0, 51));
    }
    @Test void originalSchedulingRegressions() throws Exception {
        dev.datnguyen.missionscheduler.MissionSchedulerTests.main(new String[0]);
    }
    private static final class Fixture implements AutoCloseable {
        final NetworkLab lab;
        Fixture() throws Exception { lab = new NetworkLab(); }
        public void close() throws Exception { lab.close(); }
    }
}
