package edu.adaptivecc.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IntervalMetricsCsvWriterTest {
    @TempDir Path tempDir;

    @Test void writesIntervalHeaderAndRates() throws Exception {
        Path output = tempDir.resolve("intervals.csv");
        IntervalMetricsCsvWriter.write(output, List.of(new IntervalMetrics(
                1_000_000_000L, 100_000_000L, 2, 1, 1, 0,
                300, 100, 4.5, 0.0)));

        String csv = Files.readString(output);
        assertTrue(csv.startsWith("interval_start_nanos,interval_duration_nanos"));
        assertTrue(csv.contains("3000.000,1000.000"));
    }
}
