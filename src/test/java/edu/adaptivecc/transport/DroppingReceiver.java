package edu.adaptivecc.transport;

import edu.adaptivecc.protocol.MessageType;
import edu.adaptivecc.protocol.ProtocolPacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test-only receiver that drops one selected DATA packet and optionally delays ACKs. */
final class DroppingReceiver implements AutoCloseable {
    private final DatagramSocket socket;
    private final long dropSequence;
    private final int acknowledgementDelayMillis;
    private final Set<Long> delivered = ConcurrentHashMap.newKeySet();
    private final AtomicInteger dropped = new AtomicInteger();
    private final AtomicBoolean dropApplied = new AtomicBoolean();
    private volatile boolean running;
    private Thread worker;

    DroppingReceiver(long dropSequence, int acknowledgementDelayMillis) throws SocketException {
        if (acknowledgementDelayMillis < 0) throw new IllegalArgumentException("delay cannot be negative");
        this.socket = new DatagramSocket(0);
        this.dropSequence = dropSequence;
        this.acknowledgementDelayMillis = acknowledgementDelayMillis;
    }

    int port() { return socket.getLocalPort(); }
    int deliveredPackets() { return delivered.size(); }
    int droppedPackets() { return dropped.get(); }

    void start() {
        running = true;
        worker = new Thread(this::runLoop, "adaptivecc-dropping-receiver");
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
                    if (packet.sequenceNumber() == dropSequence && dropApplied.compareAndSet(false, true)) {
                        dropped.incrementAndGet();
                        continue;
                    }
                    delivered.add(packet.sequenceNumber());
                    if (acknowledgementDelayMillis > 0) Thread.sleep(acknowledgementDelayMillis);
                    send(ProtocolPacket.ack(packet.sequenceNumber(), System.nanoTime()), datagram.getSocketAddress());
                } else if (packet.type() == MessageType.FIN) {
                    send(ProtocolPacket.finAck(packet.sequenceNumber(), System.nanoTime()), datagram.getSocketAddress());
                }
            } catch (SocketException e) {
                if (running) throw new RuntimeException(e);
            } catch (IOException | IllegalArgumentException e) {
                if (running) throw new RuntimeException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
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
