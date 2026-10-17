package edu.adaptivecc.protocol;

public enum MessageType {
    DATA(1), ACK(2), FIN(3), FIN_ACK(4);

    private final int code;

    MessageType(int code) { this.code = code; }

    public int code() { return code; }

    public static MessageType fromCode(int code) {
        for (MessageType type : values()) {
            if (type.code == code) return type;
        }
        throw new IllegalArgumentException("Unknown message type: " + code);
    }
}
