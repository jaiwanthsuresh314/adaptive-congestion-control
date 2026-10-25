package edu.adaptivecc.transport;

import edu.adaptivecc.protocol.MessageType;
import edu.adaptivecc.protocol.ProtocolPacket;
import edu.adaptivecc.metrics.TransferMetrics;
import edu.adaptivecc.congestion.CongestionController;
import edu.adaptivecc.congestion.FixedRateController;
import edu.adaptivecc.metrics.NetworkEvent;
import edu.adaptivecc.metrics.TimeSeriesRecorder;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;

public final class UdpSender {
    private final InetSocketAddress destination;
    private final int acknowledgementTimeoutMillis;
    private final int maxAttempts;
    private final CongestionController controller;
    private final TimeSeriesRecorder eventRecorder;

    public UdpSender(InetSocketAddress destination, int acknowledgementTimeoutMillis, int maxAttempts) {
        this(destination, acknowledgementTimeoutMillis, maxAttempts, 1);
    }

    public UdpSender(InetSocketAddress destination, int acknowledgementTimeoutMillis, int maxAttempts, int windowSize) {
        this(destination, acknowledgementTimeoutMillis, maxAttempts, new FixedRateController(windowSize, Long.MAX_VALUE));
    }

    public UdpSender(InetSocketAddress destination, int acknowledgementTimeoutMillis, int maxAttempts, CongestionController controller) {
        this(destination, acknowledgementTimeoutMillis, maxAttempts, controller, null);
    }

    public UdpSender(InetSocketAddress destination, int acknowledgementTimeoutMillis, int maxAttempts,
                     CongestionController controller, TimeSeriesRecorder eventRecorder) {
        if (acknowledgementTimeoutMillis <= 0 || maxAttempts <= 0) throw new IllegalArgumentException("timeouts and attempts must be positive");
        if (controller == null) throw new IllegalArgumentException("controller is required");
        this.destination = destination;
        this.acknowledgementTimeoutMillis = acknowledgementTimeoutMillis;
        this.maxAttempts = maxAttempts;
        this.controller = controller;
        this.eventRecorder = eventRecorder;
    }

    public int send(byte[][] payloads) throws IOException {
        return sendWithMetrics(payloads).acknowledgedPackets();
    }

    public TransferMetrics sendWithMetrics(byte[][] payloads) throws IOException {
        long started = System.nanoTime();
        long originalBytes = 0;
        for (byte[] payload : payloads) {
            if (payload == null) throw new IllegalArgumentException("payload cannot be null");
            originalBytes += payload.length;
        }
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(acknowledgementTimeoutMillis);
            DataTransfer data = sendDataWithWindow(socket, payloads);
            ProtocolPacket fin = ProtocolPacket.fin(payloads.length, System.nanoTime());
            if (!transmitUntilAcknowledged(socket, fin)) throw new IOException("No FIN-ACK");
            return new TransferMetrics(payloads.length, data.acknowledged(), data.transmissions(), originalBytes,
                    System.nanoTime() - started, data.rttSamplesNanos());
        }
    }

    private DataTransfer sendDataWithWindow(DatagramSocket socket, byte[][] payloads) throws IOException {
        Map<Integer, ProtocolPacket> packets = new HashMap<>();
        Map<Integer, Integer> attempts = new HashMap<>();
        Map<Integer, Long> deadlines = new HashMap<>();
        Set<Integer> acknowledged = new HashSet<>();
        Map<Integer, Long> firstSendNanos = new HashMap<>();
        List<Long> rttSamplesNanos = new ArrayList<>();
        int[] transmissions = {0};
        int nextSequence = 0;

        while (acknowledged.size() < payloads.length) {
            while (nextSequence < payloads.length && packets.size() < controller.congestionWindow()) {
                ProtocolPacket packet = ProtocolPacket.data(nextSequence, System.nanoTime(), payloads[nextSequence]);
                packets.put(nextSequence, packet);
                transmit(socket, packet, attempts, deadlines, firstSendNanos, transmissions);
                nextSequence++;
            }

            try {
                byte[] storage = new byte[ProtocolPacket.MAX_PACKET_SIZE];
                DatagramPacket response = new DatagramPacket(storage, storage.length);
                socket.receive(response);
                ProtocolPacket responsePacket = ProtocolPacket.decode(response.getData(), response.getLength());
                if (responsePacket.type() == MessageType.ACK) {
                    int sequence = Math.toIntExact(responsePacket.acknowledgementNumber());
                    if (packets.containsKey(sequence)) {
                        ProtocolPacket acknowledgedPacket = packets.get(sequence);
                        long rttNanos = System.nanoTime() - firstSendNanos.get(sequence);
                        recordEvent(NetworkEvent.EventType.PACKET_ACKNOWLEDGED, sequence,
                                attempts.get(sequence) == 1 ? rttNanos : 0, acknowledgedPacket.payload().length);
                        acknowledged.add(sequence);
                        packets.remove(sequence);
                        deadlines.remove(sequence);
                        if (attempts.get(sequence) == 1) rttSamplesNanos.add(rttNanos);
                        // Karn's algorithm: an ACK for a retransmitted packet has no
                        // unambiguous RTT sample, so it must not train congestion control.
                        if (attempts.get(sequence) == 1) {
                            controller.onAcknowledgement(rttNanos, acknowledgedPacket.payload().length);
                        }
                    }
                }
            } catch (SocketTimeoutException ignored) {
                retransmitExpired(socket, packets, attempts, deadlines, firstSendNanos, transmissions);
            }
        }
        return new DataTransfer(acknowledged.size(), transmissions[0], rttSamplesNanos);
    }

    private void retransmitExpired(DatagramSocket socket, Map<Integer, ProtocolPacket> packets,
                                   Map<Integer, Integer> attempts, Map<Integer, Long> deadlines,
                                   Map<Integer, Long> firstSendNanos, int[] transmissions) throws IOException {
        long now = System.nanoTime();
        boolean expired = false;
        for (Map.Entry<Integer, ProtocolPacket> entry : packets.entrySet()) {
            if (now >= deadlines.get(entry.getKey())) {
                expired = true;
                transmit(socket, entry.getValue(), attempts, deadlines, firstSendNanos, transmissions);
            }
        }
        if (expired) controller.onTimeout();
    }

    private void transmit(DatagramSocket socket, ProtocolPacket packet, Map<Integer, Integer> attempts,
                          Map<Integer, Long> deadlines, Map<Integer, Long> firstSendNanos, int[] transmissions) throws IOException {
        int sequence = Math.toIntExact(packet.sequenceNumber());
        int attempt = attempts.getOrDefault(sequence, 0) + 1;
        if (attempt > maxAttempts) {
            recordEvent(NetworkEvent.EventType.PACKET_LOST, sequence, 0, packet.payload().length);
            throw new IOException("No ACK for sequence " + sequence);
        }
        byte[] encoded = packet.encode();
        long pacingDelay = controller.pacingDelayNanos(packet.payload().length);
        if (pacingDelay > 0) {
            try { Thread.sleep(pacingDelay / 1_000_000L, (int) (pacingDelay % 1_000_000L)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted while pacing", e); }
        }
        socket.send(new DatagramPacket(encoded, encoded.length, destination));
        attempts.put(sequence, attempt);
        firstSendNanos.putIfAbsent(sequence, System.nanoTime());
        transmissions[0]++;
        recordEvent(attempt == 1 ? NetworkEvent.EventType.PACKET_SENT : NetworkEvent.EventType.PACKET_RETRANSMITTED,
                sequence, 0, packet.payload().length);
        deadlines.put(sequence, System.nanoTime() + acknowledgementTimeoutMillis * 1_000_000L);
    }

    private void recordEvent(NetworkEvent.EventType type, long sequence, long rttNanos, long payloadBytes) {
        if (eventRecorder != null) eventRecorder.record(new NetworkEvent(System.nanoTime(), type, sequence, rttNanos, payloadBytes));
    }

    private record DataTransfer(int acknowledged, int transmissions, List<Long> rttSamplesNanos) { }

    private boolean transmitUntilAcknowledged(DatagramSocket socket, ProtocolPacket packet) throws IOException {
        byte[] encoded = packet.encode();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            socket.send(new DatagramPacket(encoded, encoded.length, destination));
            byte[] storage = new byte[ProtocolPacket.MAX_PACKET_SIZE];
            try {
                while (true) {
                    DatagramPacket response = new DatagramPacket(storage, storage.length);
                    socket.receive(response);
                    ProtocolPacket responsePacket = ProtocolPacket.decode(response.getData(), response.getLength());
                    boolean matches = packet.type() == MessageType.FIN
                            ? responsePacket.type() == MessageType.FIN_ACK && responsePacket.acknowledgementNumber() == packet.sequenceNumber()
                            : responsePacket.type() == MessageType.ACK && responsePacket.acknowledgementNumber() == packet.sequenceNumber();
                    if (matches) return true;
                }
            } catch (SocketTimeoutException ignored) { }
        }
        return false;
    }
}
