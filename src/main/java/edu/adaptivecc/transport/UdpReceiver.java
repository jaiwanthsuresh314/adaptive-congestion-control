package edu.adaptivecc.transport;

import edu.adaptivecc.protocol.MessageType;
import edu.adaptivecc.protocol.ProtocolPacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public final class UdpReceiver implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(UdpReceiver.class.getName());
    private final DatagramSocket socket;
    private final Set<Long> receivedSequences = ConcurrentHashMap.newKeySet();
    private final AtomicLong deliveredPackets = new AtomicLong();
    private final CountDownLatch receiveLoopReady = new CountDownLatch(1);
    private volatile boolean running;
    private Thread worker;

    public UdpReceiver(int port) throws SocketException { this.socket = new DatagramSocket(port); }
    public int port() { return socket.getLocalPort(); }
    public long deliveredPackets() { return deliveredPackets.get(); }

    public synchronized void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::runLoop, "adaptivecc-receiver");
        worker.setDaemon(true);
        worker.start();
        try {
            if (!receiveLoopReady.await(1, TimeUnit.SECONDS)) throw new IllegalStateException("Receiver worker did not become ready");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting receiver", e);
        }
    }

    private void runLoop() {
        byte[] storage = new byte[ProtocolPacket.MAX_PACKET_SIZE];
        receiveLoopReady.countDown();
        while (running) {
            try {
                DatagramPacket datagram = new DatagramPacket(storage, storage.length);
                socket.receive(datagram);
                ProtocolPacket packet = ProtocolPacket.decode(datagram.getData(), datagram.getLength());
                if (packet.type() == MessageType.DATA) {
                    if (receivedSequences.add(packet.sequenceNumber())) deliveredPackets.incrementAndGet();
                    send(ProtocolPacket.ack(packet.sequenceNumber(), System.nanoTime()), datagram.getSocketAddress());
                } else if (packet.type() == MessageType.FIN) {
                    send(ProtocolPacket.finAck(packet.sequenceNumber(), System.nanoTime()), datagram.getSocketAddress());
                }
            } catch (IOException e) {
                if (running) LOG.warning("Receiver socket error: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                LOG.warning("Dropped malformed datagram: " + e.getMessage());
            }
        }
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
