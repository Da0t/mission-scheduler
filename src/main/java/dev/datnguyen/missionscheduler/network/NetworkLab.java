package dev.datnguyen.missionscheduler.network;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.time.Instant;
import java.util.*;

/** Real loopback UDP transport, with an explicitly modeled two-route network. */
@Service
public class NetworkLab {
    public record Controls(boolean primaryUp, boolean backupUp, int delayMs, int lossPercent) {
        public Controls {
            if (delayMs < 0 || delayMs > 400 || lossPercent < 0 || lossPercent > 50)
                throw new IllegalArgumentException("Delay must be 0–400 ms and loss 0–50%.");
        }
    }
    public record Event(long id, String time, String type, String message) {}
    public record Sample(long sequence, double latencyMs) {}
    public record Snapshot(boolean running, Controls controls, String route, long sent,
                           long received, long dropped, int inFlight, double deliveryPercent,
                           double meanLatencyMs, double jitterMs, int senderPort, int receiverPort,
                           List<Sample> samples, List<Event> events) {}
    private record Packet(long sequence, long started, long due) {}
    private final DatagramChannel sender;
    private final DatagramChannel receiver;
    private final InetSocketAddress destination;
    private final Random random = new Random(42);
    private final PriorityQueue<Packet> pending = new PriorityQueue<>(Comparator.comparingLong(Packet::due));
    private final Map<Long, Long> awaiting = new HashMap<>();
    private final Deque<Event> events = new ArrayDeque<>();
    private final Deque<Sample> samples = new ArrayDeque<>();
    private Controls controls = new Controls(true, true, 0, 0);
    private boolean running;
    private long sequence, sent, received, dropped, eventId, nextSend;
    private double totalLatency, totalJitter, lastLatency;

    public NetworkLab() throws IOException {
        var loopback = InetAddress.getByName("127.0.0.1");
        receiver = DatagramChannel.open();
        receiver.bind(new InetSocketAddress(loopback, 0));
        receiver.configureBlocking(false);
        destination = (InetSocketAddress) receiver.getLocalAddress();
        sender = DatagramChannel.open();
        sender.bind(new InetSocketAddress(loopback, 0));
        sender.configureBlocking(false);
        log("READY", "UDP endpoints bound to loopback. Ready to transmit.");
    }

    public synchronized Snapshot start() {
        if (!running) { running = true; nextSend = 0; log("START", "Telemetry stream started · 4 datagrams/sec"); }
        return snapshot();
    }
    public synchronized Snapshot stop() {
        if (running) { running = false; log("PAUSE", "New transmissions paused; in-flight packets will drain."); }
        return snapshot();
    }
    public synchronized Snapshot configure(Controls value) {
        String old = route();
        controls = Objects.requireNonNull(value);
        if (!old.equals(route())) log("ROUTE", old + " → " + route());
        log("CONFIG", "Added delay " + value.delayMs + " ms · injected loss " + value.lossPercent + "%");
        return snapshot();
    }
    public synchronized Snapshot reset() throws IOException {
        running = false; pending.clear(); awaiting.clear(); samples.clear(); events.clear();
        while (receiver.receive(ByteBuffer.allocate(64)) != null) { /* drain previous run */ }
        sent = received = dropped = 0;
        totalLatency = totalJitter = lastLatency = 0;
        controls = new Controls(true, true, 0, 0);
        random.setSeed(42);
        log("RESET", "New session. Routes and impairments restored.");
        return snapshot();
    }
    private String route() { return controls.primaryUp ? "PRIMARY" : controls.backupUp ? "BACKUP" : "UNREACHABLE"; }

    @Scheduled(fixedDelay = 10)
    public synchronized void tick() throws IOException {
        long now = System.nanoTime();
        if (running && now >= nextSend) {
            nextSend = now + 250_000_000L;
            long id = ++sequence; sent++;
            if (route().equals("UNREACHABLE") || random.nextInt(100) < controls.lossPercent) {
                dropped++;
                log("DROP", "Packet #" + id + (route().equals("UNREACHABLE") ? " · no available route" : " · injected loss"));
            } else {
                int delay = (controls.primaryUp ? 35 : 110) + controls.delayMs + random.nextInt(16);
                pending.add(new Packet(id, now, now + delay * 1_000_000L));
            }
        }
        while (!pending.isEmpty() && pending.peek().due <= now) {
            Packet packet = pending.remove();
            ByteBuffer bytes = ByteBuffer.allocate(16).putLong(packet.sequence).putLong(packet.started);
            bytes.flip();
            if (sender.send(bytes, destination) == 16) awaiting.put(packet.sequence, packet.started);
            else { dropped++; log("DROP", "Local UDP send buffer unavailable"); }
        }
        ByteBuffer bytes = ByteBuffer.allocate(64);
        while (receiver.receive(bytes) != null) {
            bytes.flip();
            if (bytes.remaining() == 16) {
                long id = bytes.getLong();
                Long started = awaiting.remove(id);
                if (started != null) {
                    double latency = (System.nanoTime() - started) / 1_000_000.0;
                    if (received > 0) totalJitter += Math.abs(latency - lastLatency);
                    lastLatency = latency; totalLatency += latency; received++;
                    samples.addLast(new Sample(id, Math.round(latency * 10.0) / 10.0));
                    if (samples.size() > 80) samples.removeFirst();
                    log("RX", "Packet #" + id + " · 16 bytes · " + Math.round(latency) + " ms");
                }
            }
            bytes.clear();
        }
        var iterator = awaiting.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue() > 2_000_000_000L) {
                iterator.remove(); dropped++; log("TIMEOUT", "Packet #" + entry.getKey() + " not received");
            }
        }
    }
    public synchronized Snapshot snapshot() {
        try {
            long resolved = received + dropped;
            return new Snapshot(running, controls, route(), sent, received, dropped,
                    pending.size() + awaiting.size(), resolved == 0 ? 100 : received * 100.0 / resolved,
                    received == 0 ? 0 : totalLatency / received,
                    received < 2 ? 0 : totalJitter / (received - 1),
                    ((InetSocketAddress) sender.getLocalAddress()).getPort(), destination.getPort(),
                    List.copyOf(samples), List.copyOf(events));
        } catch (IOException e) { throw new IllegalStateException("UDP endpoint unavailable", e); }
    }
    private void log(String type, String message) {
        events.addFirst(new Event(++eventId, Instant.now().toString(), type, message));
        if (events.size() > 100) events.removeLast();
    }
    @PreDestroy
    public synchronized void close() throws IOException { sender.close(); receiver.close(); }
}
