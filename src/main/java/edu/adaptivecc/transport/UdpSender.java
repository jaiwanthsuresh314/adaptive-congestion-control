package edu.adaptivecc.transport;

import edu.adaptivecc.protocol.MessageType;
import edu.adaptivecc.protocol.ProtocolPacket;
import edu.adaptivecc.metrics.TransferMetrics;

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
    private final int windowSize;

    public UdpSender(InetSocketAddress destination, int acknowledgementTimeoutMillis, int maxAttempts) {
        this(destination, acknowledgementTimeoutMillis, maxAttempts, 1);
    }

    public UdpSender(InetSocketAddress destination, int acknowledgementTimeoutMillis, int maxAttempts, int windowSize) {
        if (acknowledgementTimeoutMillis <= 0 || maxAttempts <= 0) throw new IllegalArgumentException("timeouts and attempts must be positive");
        if (windowSize <= 0) throw new IllegalArgumentException("window size must be positive");
        this.destination = destination;
        this.acknowledgementTimeoutMillis = acknowledgementTimeoutMillis;
        this.maxAttempts = maxAttempts;
        this.windowSize = windowSize;
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
            while (nextSequence < payloads.length && packets.size() < windowSize) {
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
                        acknowledged.add(sequence);
                        packets.remove(sequence);
                        deadlines.remove(sequence);
                        if (attempts.get(sequence) == 1) rttSamplesNanos.add(System.nanoTime() - firstSendNanos.get(sequence));
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
        for (Map.Entry<Integer, ProtocolPacket> entry : packets.entrySet()) {
            if (now >= deadlines.get(entry.getKey())) transmit(socket, entry.getValue(), attempts, deadlines, firstSendNanos, transmissions);
        }
    }

    private void transmit(DatagramSocket socket, ProtocolPacket packet, Map<Integer, Integer> attempts,
                          Map<Integer, Long> deadlines, Map<Integer, Long> firstSendNanos, int[] transmissions) throws IOException {
        int sequence = Math.toIntExact(packet.sequenceNumber());
        int attempt = attempts.getOrDefault(sequence, 0) + 1;
        if (attempt > maxAttempts) throw new IOException("No ACK for sequence " + sequence);
        byte[] encoded = packet.encode();
        socket.send(new DatagramPacket(encoded, encoded.length, destination));
        attempts.put(sequence, attempt);
        firstSendNanos.putIfAbsent(sequence, System.nanoTime());
        transmissions[0]++;
        deadlines.put(sequence, System.nanoTime() + acknowledgementTimeoutMillis * 1_000_000L);
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
