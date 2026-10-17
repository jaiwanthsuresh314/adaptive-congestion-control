package edu.adaptivecc.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

public record ProtocolPacket(MessageType type, long sequenceNumber, long acknowledgementNumber,
                             long timestampNanos, byte[] payload) {
    public static final short MAGIC = (short) 0xACCC;
    public static final byte VERSION = 1;
    public static final int HEADER_SIZE = 32;
    public static final int MAX_PAYLOAD_SIZE = 1200;
    public static final int MAX_PACKET_SIZE = HEADER_SIZE + MAX_PAYLOAD_SIZE;

    public ProtocolPacket {
        if (type == null) throw new IllegalArgumentException("type is required");
        if (payload == null) throw new IllegalArgumentException("payload is required");
        if (payload.length > MAX_PAYLOAD_SIZE) throw new IllegalArgumentException("payload is too large");
        payload = Arrays.copyOf(payload, payload.length);
    }

    public static ProtocolPacket data(long sequence, long timestamp, byte[] payload) {
        return new ProtocolPacket(MessageType.DATA, sequence, 0, timestamp, payload);
    }

    public static ProtocolPacket ack(long sequence, long timestamp) {
        return new ProtocolPacket(MessageType.ACK, 0, sequence, timestamp, new byte[0]);
    }

    public static ProtocolPacket fin(long sequence, long timestamp) {
        return new ProtocolPacket(MessageType.FIN, sequence, 0, timestamp, new byte[0]);
    }

    public static ProtocolPacket finAck(long sequence, long timestamp) {
        return new ProtocolPacket(MessageType.FIN_ACK, 0, sequence, timestamp, new byte[0]);
    }

    @Override public byte[] payload() { return Arrays.copyOf(payload, payload.length); }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProtocolPacket that)) return false;
        return sequenceNumber == that.sequenceNumber
                && acknowledgementNumber == that.acknowledgementNumber
                && timestampNanos == that.timestampNanos
                && type == that.type
                && Arrays.equals(payload, that.payload);
    }

    @Override public int hashCode() {
        return Objects.hash(type, sequenceNumber, acknowledgementNumber, timestampNanos, Arrays.hashCode(payload));
    }

    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payload.length).order(ByteOrder.BIG_ENDIAN);
        buffer.putShort(MAGIC).put(VERSION).put((byte) type.code())
                .putLong(sequenceNumber).putLong(acknowledgementNumber)
                .putInt(payload.length).putLong(timestampNanos).put(payload);
        return buffer.array();
    }

    public static ProtocolPacket decode(byte[] bytes, int length) {
        if (bytes == null || length < HEADER_SIZE || length > bytes.length) {
            throw new IllegalArgumentException("Invalid packet length");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.BIG_ENDIAN);
        if (buffer.getShort() != MAGIC) throw new IllegalArgumentException("Invalid packet magic");
        if (buffer.get() != VERSION) throw new IllegalArgumentException("Unsupported protocol version");
        MessageType type = MessageType.fromCode(Byte.toUnsignedInt(buffer.get()));
        long sequence = buffer.getLong();
        long acknowledgement = buffer.getLong();
        int payloadLength = buffer.getInt();
        long timestamp = buffer.getLong();
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_SIZE || payloadLength != length - HEADER_SIZE) {
            throw new IllegalArgumentException("Invalid payload length");
        }
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        return new ProtocolPacket(type, sequence, acknowledgement, timestamp, payload);
    }
}
