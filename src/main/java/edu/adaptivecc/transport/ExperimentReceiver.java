package edu.adaptivecc.transport;

import java.io.IOException;

/** Receiver lifecycle used by benchmark implementations. */
public interface ExperimentReceiver extends AutoCloseable {
    int port();
    void start();
    long deliveredPackets();
    @Override void close() throws IOException;
}
