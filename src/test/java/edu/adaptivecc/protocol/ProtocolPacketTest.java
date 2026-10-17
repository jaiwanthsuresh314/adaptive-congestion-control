package edu.adaptivecc.protocol;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolPacketTest {
    @Test void roundTripPreservesFields() {
        ProtocolPacket original = ProtocolPacket.data(42, 1234, new byte[]{1, 2, 3});
        assertEquals(original, ProtocolPacket.decode(original.encode(), original.encode().length));
    }

    @Test void rejectsTruncatedAndMalformedPackets() {
        assertThrows(IllegalArgumentException.class, () -> ProtocolPacket.decode(new byte[ProtocolPacket.HEADER_SIZE - 1], ProtocolPacket.HEADER_SIZE - 1));
        byte[] encoded = ProtocolPacket.ack(1, 2).encode();
        encoded[0] = 0;
        assertThrows(IllegalArgumentException.class, () -> ProtocolPacket.decode(encoded, encoded.length));
    }

    @Test void rejectsPayloadLengthMismatch() {
        byte[] encoded = ProtocolPacket.data(1, 2, new byte[]{7}).encode();
        encoded[23] = 0;
        assertThrows(IllegalArgumentException.class, () -> ProtocolPacket.decode(encoded, encoded.length));
    }
}
