package edu.adaptivecc.experiments;

import java.nio.file.Path;

/** Validated, reproducible configuration for one benchmark invocation. */
public record BenchmarkConfig(int packetCount, int payloadSize, int timeoutMillis, int maxAttempts,
                              String controllerName, long rateBytesPerSecond, long seed,
                              String scenario, String networkProfile, Path output) {
    public BenchmarkConfig {
        if (packetCount <= 0 || payloadSize <= 0 || payloadSize > 1200) {
            throw new IllegalArgumentException("packets and payload must be positive; payload must be <= 1200");
        }
        if (timeoutMillis <= 0 || maxAttempts <= 0 || rateBytesPerSecond <= 0) {
            throw new IllegalArgumentException("timeout, attempts, and rate must be positive");
        }
        if (!controllerName.equalsIgnoreCase("fixed") && !controllerName.equalsIgnoreCase("aimd")
                && !controllerName.equalsIgnoreCase("adaptive")) {
            throw new IllegalArgumentException("controller must be fixed, aimd, or adaptive");
        }
        if (scenario.isBlank() || networkProfile.isBlank()) {
            throw new IllegalArgumentException("scenario and network profile cannot be blank");
        }
    }

    public static BenchmarkConfig fromArgs(String[] args) {
        return new BenchmarkConfig(intArg(args, "packets", 100), intArg(args, "payload", 256),
                intArg(args, "timeout", 250), intArg(args, "attempts", 3),
                stringArg(args, "controller", "fixed"), longArg(args, "rate", 1_000_000L),
                longArg(args, "seed", 20261025L), stringArg(args, "scenario", "localhost"),
                stringArg(args, "network", "uncontrolled"),
                Path.of(stringArg(args, "output", "results/loopback-benchmark.csv")));
    }

    private static int intArg(String[] args, String name, int fallback) {
        return Integer.parseInt(stringArg(args, name, Integer.toString(fallback)));
    }

    private static long longArg(String[] args, String name, long fallback) {
        return Long.parseLong(stringArg(args, name, Long.toString(fallback)));
    }

    private static String stringArg(String[] args, String name, String fallback) {
        String prefix = "--" + name + "=";
        for (String arg : args) if (arg.startsWith(prefix)) return arg.substring(prefix.length());
        return fallback;
    }
}
