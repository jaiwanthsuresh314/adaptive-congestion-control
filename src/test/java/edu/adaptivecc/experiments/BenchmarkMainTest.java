package edu.adaptivecc.experiments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkMainTest {
    @TempDir Path tempDir;

    @Test void adaptiveBenchmarkWritesSummaryAndEventCsv() throws Exception {
        Path output = tempDir.resolve("adaptive.csv");
        BenchmarkMain.main(new String[]{"--controller=adaptive", "--packets=5", "--payload=64", "--timeout=250", "--attempts=3", "--output=" + output});

        assertTrue(Files.size(output) > 0);
        assertTrue(Files.size(tempDir.resolve("adaptive-localhost-adaptive-events.csv")) > 0);
        assertTrue(Files.size(tempDir.resolve("adaptive-localhost-adaptive-intervals.csv")) > 0);
        assertTrue(Files.size(tempDir.resolve("adaptive.csv.properties")) > 0);
    }

    @Test void benchmarkCanRunWithWindowsImpairmentEmulation() throws Exception {
        Path output = tempDir.resolve("impaired.csv");
        BenchmarkMain.main(new String[]{"--controller=adaptive", "--packets=5", "--payload=64",
                "--timeout=50", "--attempts=4", "--drop-sequence=0", "--drop-count=1",
                "--scenario=loss", "--network=application-emulation", "--output=" + output});

        assertTrue(Files.size(output) > 0);
        assertTrue(Files.readString(output.resolveSibling("impaired.csv.properties"))
                .contains("networkProfile=application-emulation"));
    }
}
