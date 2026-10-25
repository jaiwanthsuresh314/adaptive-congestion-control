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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic application-level impairment receiver for Windows and other hosts
 * without router emulation. It still communicates through real UDP sockets.
 */
public final class UdpImpairmentReceiver implements ExperimentReceiver {
    private final DatagramSocket socket;
    private final long dropSequence;
    private final AtomicInteger remainingDrops;
    private final Map<Long, Integer> delayedAckMillis;
    private final Set<Long> receivedSequences = ConcurrentHashMap.newKeySet();
    private final AtomicLong deliveredPackets = new AtomicLong();
    private volatile boolean running;
    private Thread worker;

    public UdpImpairmentReceiver(long dropSequence, int dropCount,
                                 Map<Long, Integer> delayedAckMillis) throws SocketException {
        if (dropSequence < -1 || dropCount < 0) throw new IllegalArgumentException("invalid drop configuration");
        delayedAckMillis.forEach((sequence, delay) -> {
            if (sequence < 0 || delay < 0) throw new IllegalArgumentException("invalid ACK delay configuration");
        });
        this.socket = new DatagramSocket(0);
        this.dropSequence = dropSequence;
        this.remainingDrops = new AtomicInteger(dropCount);
        this.delayedAckMillis = Map.copyOf(delayedAckMillis);
    }

    @Override public int port() { return socket.getLocalPort(); }
    @Override public long deliveredPackets() { return deliveredPackets.get(); }

    @Override public synchronized void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::runLoop, "adaptivecc-udp-impairment-receiver");
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
                    if (packet.sequenceNumber() == dropSequence && remainingDrops.getAndDecrement() > 0) continue;
                    if (receivedSequences.add(packet.sequenceNumber())) deliveredPackets.incrementAndGet();
                    ProtocolPacket acknowledgement = ProtocolPacket.ack(packet.sequenceNumber(), System.nanoTime());
                    int delay = delayedAckMillis.getOrDefault(packet.sequenceNumber(), 0);
                    if (delay == 0) send(acknowledgement, datagram.getSocketAddress());
                    else sendLater(acknowledgement, datagram.getSocketAddress(), delay);
                } else if (packet.type() == MessageType.FIN) {
                    send(ProtocolPacket.finAck(packet.sequenceNumber(), System.nanoTime()), datagram.getSocketAddress());
                }
            } catch (SocketException e) {
                if (running) throw new IllegalStateException("impairment receiver socket failed", e);
            } catch (IOException | IllegalArgumentException e) {
                if (running) throw new IllegalStateException("impairment receiver rejected datagram", e);
            }
        }
    }

    private void sendLater(ProtocolPacket packet, SocketAddress destination, int delayMillis) {
        Thread delayed = new Thread(() -> {
            try {
                Thread.sleep(delayMillis);
                if (running) send(packet, destination);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                if (running) throw new IllegalStateException("delayed ACK failed", e);
            }
        }, "adaptivecc-delayed-ack");
        delayed.setDaemon(true);
        delayed.start();
    }

    private void send(ProtocolPacket packet, SocketAddress destination) throws IOException {
        byte[] bytes = packet.encode();
        socket.send(new DatagramPacket(bytes, bytes.length, destination));
    }

    @Override public synchronized void close() {
        running = false;
        socket.close();
        if (worker != null) {
            try { worker.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
