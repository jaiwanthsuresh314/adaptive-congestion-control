# Adaptive Congestion Control

AdaptiveCC is an educational Java implementation of a custom reliable transport protocol over UDP. The project investigates how a sender can adjust transmission behavior using feedback such as acknowledgements, timeouts, RTT, and delivery measurements.

The goal is to compare a fixed-rate sender, an AIMD baseline, and a deterministic adaptive controller under repeatable network conditions. The project uses real Java UDP sockets; it is not a TCP implementation and should only be used on localhost, systems under your control, or authorized test networks.

## Current status

Milestones 1–4 are implemented:

- Binary packet format with DATA, ACK, FIN, and FIN_ACK messages.
- Packet validation, sequence numbers, duplicate suppression, and acknowledgement handling.
- Bounded sender window with timeout-based retransmission.
- Transfer metrics for goodput, retransmissions, average RTT, and RTT variation.
- CSV benchmark output.
- Shared congestion-controller interface.
- Fixed-rate controller with configurable pacing and window size.
- AIMD controller with additive increase, multiplicative decrease, and bounded windows.
- Deterministic adaptive controller using smoothed RTT, RTT variation, delay thresholds, timeouts, and delivery-rate pacing.
- Optional sender event recording for sent, acknowledged, retransmitted, and lost packets.
- Benchmark event CSV output, with one event file per benchmark scenario.

Controlled network experiments and offline visualization are future work. Time-series event logging is connected to sender activity.

## Technology

- Java 21 language target or newer JDK; Java 24 works with the current build.
- Maven.
- UDP through `DatagramSocket` and `DatagramPacket`.
- JUnit 5.
- CSV output for later analysis and plotting.

## Build and test

From the project directory:

```powershell
mvn test
```

If Maven is not available on `PATH`, use the Maven executable directly, for example:

```powershell
& "C:\Program Files\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd" test
```

The current suite covers packet encoding and validation, controller rules, localhost UDP communication, bounded windows, deterministic loss/delay/reordering impairment, benchmark execution, and metric calculations. The latest verified result is 24 passing tests with no failures or errors.

## Run the benchmark

Compile and test first, then run the benchmark:

```powershell
java -cp target\classes edu.adaptivecc.experiments.BenchmarkMain
```

Useful options include:

```text
--controller=fixed|aimd|adaptive
--packets=100
--payload=256
--rate=1000000
--timeout=250
--attempts=3
--output=results\benchmark.csv
```

For example, run the fixed-rate baseline:

```powershell
java -cp target\classes edu.adaptivecc.experiments.BenchmarkMain --controller=fixed --packets=100 --payload=256 --rate=1000000 --output=results\fixed-baseline.csv
```

Run AIMD:

```powershell
java -cp target\classes edu.adaptivecc.experiments.BenchmarkMain --controller=aimd --packets=100 --payload=256 --output=results\aimd-baseline.csv
```

Run the adaptive controller:

```powershell
java -cp target\classes edu.adaptivecc.experiments.BenchmarkMain --controller=adaptive --packets=100 --payload=256 --output=results\adaptive.csv
```

The benchmark runs sender and receiver on `127.0.0.1`, compares fixed windows of 1, 4, and 16, or runs the AIMD controller, and writes CSV summaries. Results from the current verified run are kept under `results/` as reproducible examples, not as general network-capacity claims.

Each benchmark summary now has matching time-series files such as `fixed-baseline-localhost-fixed-window-4-events.csv`. These files contain timestamped sender-side events and first-transmission RTT samples where available.

## Interpreting current results

In the latest 100-packet, 256-byte localhost run:

- Fixed-rate windows 1, 4, and 16 acknowledged all packets with zero retransmissions.
- Fixed-rate goodput was approximately 136,464, 166,536, and 160,959 bytes/sec respectively.
- AIMD acknowledged all packets with zero retransmissions and ended at window 14.
- AIMD goodput was approximately 1,518,810 bytes/sec.

These measurements are affected by localhost scheduling and pacing. They do not demonstrate performance under a real bottleneck, loss, propagation delay, or competing traffic.

## Design boundaries

Reliability and congestion control are separate concerns. The transport handles sequence numbers, ACKs, duplicate suppression, bounded outstanding packets, timeouts, and retransmissions. A congestion controller receives ACK and timeout feedback and determines the allowed window and pacing behavior.

The protocol is not TCP-compatible. UDP does not guarantee delivery, ordering, or duplicate suppression, so experiments must treat these as responsibilities of this application-level protocol.

RTT samples are recorded only for packets acknowledged on their first transmission. RTT for a retransmitted packet is ambiguous and is excluded from the current summary. Queueing delay is not yet measured directly, and exact router queue occupancy must not be inferred from these results.

## Future work

The next development steps are:

1. Add controlled experiments for bandwidth changes, propagation delay, random loss, recovery, and competing flows.
2. Add plots for sending rate, throughput, goodput, RTT, retransmissions, and fairness.
3. Add repeatable configuration files and record random seeds for impairment runs.
4. Document experiment methodology, limitations, and conclusions based on measured data.

## Project handoff

This README is also the maintained handoff record for future sessions. Update it when a milestone changes the implementation, test results, benchmark results, limitations, or next steps. Avoid creating a second progress document.

The current integration branch is `feature/impairment-integration`; `main` contains the previously merged milestones. The GitHub repository is [jaiwanthsuresh314/adaptive-congestion-control](https://github.com/jaiwanthsuresh314/adaptive-congestion-control).
