package edu.adaptivecc.experiments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the exact benchmark inputs beside the CSV output. */
public final class ExperimentManifestWriter {
    private ExperimentManifestWriter() { }

    public static Path write(Path output, BenchmarkConfig config) throws IOException {
        Path manifest = output.resolveSibling(output.getFileName() + ".properties");
        if (manifest.getParent() != null) Files.createDirectories(manifest.getParent());
        String contents = "controller=" + config.controllerName() + "\n"
                + "packetCount=" + config.packetCount() + "\n"
                + "payloadSize=" + config.payloadSize() + "\n"
                + "timeoutMillis=" + config.timeoutMillis() + "\n"
                + "maxAttempts=" + config.maxAttempts() + "\n"
                + "rateBytesPerSecond=" + config.rateBytesPerSecond() + "\n"
                + "seed=" + config.seed() + "\n"
                + "scenario=" + config.scenario() + "\n"
                + "networkProfile=" + config.networkProfile() + "\n"
                + "output=" + output.toAbsolutePath() + "\n";
        Files.writeString(manifest, contents);
        return manifest;
    }
}
