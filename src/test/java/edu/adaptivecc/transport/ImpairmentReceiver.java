package edu.adaptivecc.transport;

import edu.adaptivecc.protocol.MessageType;
import edu.adaptivecc.protocol.ProtocolPacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only receiver for deterministic loss, delay, and ACK reordering. */
final class ImpairmentReceiver implements AutoCloseable {
    private final DatagramSocket socket = new DatagramSocket(0);
    private final Map<Long, AtomicInteger> remainingDrops;
    private final Map<Long, Integer> acknowledgementDelaysMillis;
    private final Set<Long> delivered = ConcurrentHashMap.newKeySet();
    private final AtomicInteger dropped = new AtomicInteger();
    private volatile boolean running;
    private Thread worker;

    ImpairmentReceiver(Map<Long, Integer> drops, Map<Long, Integer> acknowledgementDelaysMillis) throws SocketException {
        this.remainingDrops = new ConcurrentHashMap<>();
        drops.forEach((sequence, count) -> {
            if (count < 0) throw new IllegalArgumentException("drop count cannot be negative");
            this.remainingDrops.put(sequence, new AtomicInteger(count));
        });
        acknowledgementDelaysMillis.forEach((sequence, delay) -> {
            if (delay < 0) throw new IllegalArgumentException("delay cannot be negative");
        });
        this.acknowledgementDelaysMillis = Map.copyOf(acknowledgementDelaysMillis);
    }

    int port() { return socket.getLocalPort(); }
    int deliveredPackets() { return delivered.size(); }
    int droppedPackets() { return dropped.get(); }

    void start() {
        running = true;
        worker = new Thread(this::runLoop, "adaptivecc-impairment-receiver");
        worker.setDaemon(true);
        worker.start();
    }

    private void runLoop() {
        while (running) {
            try {
                byte[] storage = new byte[ProtocolPacket.MAX_PACKET_SIZE];
                DatagramPacket datagram = new DatagramPacket(storage, storage.length);
                socket.receive(datagram);
                ProtocolPacket packet = ProtocolPacket.decode(datagram.getData(), datagram.getLength());
                if (packet.type() == MessageType.DATA) {
                    AtomicInteger drops = remainingDrops.get(packet.sequenceNumber());
                    if (drops != null && drops.getAndDecrement() > 0) {
                        dropped.incrementAndGet();
                        continue;
                    }
                    delivered.add(packet.sequenceNumber());
                    int delay = acknowledgementDelaysMillis.getOrDefault(packet.sequenceNumber(), 0);
                    ProtocolPacket acknowledgement = ProtocolPacket.ack(packet.sequenceNumber(), System.nanoTime());
                    if (delay > 0) {
                        Thread delayedAck = new Thread(() -> {
                            try {
                                Thread.sleep(delay);
                                send(acknowledgement, datagram.getSocketAddress());
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } catch (IOException e) {
                                if (running) throw new RuntimeException(e);
                            }
                        }, "adaptivecc-delayed-ack");
                        delayedAck.setDaemon(true);
                        delayedAck.start();
                    } else {
                        send(acknowledgement, datagram.getSocketAddress());
                    }
                } else if (packet.type() == MessageType.FIN) {
                    send(ProtocolPacket.finAck(packet.sequenceNumber(), System.nanoTime()), datagram.getSocketAddress());
                }
            } catch (SocketException e) {
                if (running) throw new RuntimeException(e);
            } catch (IOException | IllegalArgumentException e) {
                if (running) throw new RuntimeException(e);
            }
        }
    }

    private void send(ProtocolPacket packet, SocketAddress destination) throws IOException {
        byte[] bytes = packet.encode();
        socket.send(new DatagramPacket(bytes, bytes.length, destination));
    }

    @Override public void close() {
        running = false;
        socket.close();
        if (worker != null) {
            try { worker.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
