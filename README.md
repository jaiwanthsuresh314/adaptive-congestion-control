# Adaptive Congestion Control

AdaptiveCC is an educational Java implementation of a custom reliable transport protocol over UDP. The project investigates how a sender can adjust transmission behavior using feedback such as acknowledgements, timeouts, RTT, and delivery measurements.

The goal is to compare a fixed-rate sender, an AIMD baseline, and a deterministic adaptive controller under repeatable network conditions. The project uses real Java UDP sockets; it is not a TCP implementation and should only be used on localhost, systems under your control, or authorized test networks.

## Current status

Milestones 1–5 are implemented, and Milestone 6 experimentation infrastructure is in progress:

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
- Validated benchmark configuration with deterministic payload generation from a recorded seed.
- Properties manifest written beside every benchmark summary CSV.
- Interval metric CSV output at 100 ms resolution.
- Congestion-window change events for controller response plots.

Controlled network experiments and the optional frontend remain future work. Offline CSV analysis is available.

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

The current suite covers packet encoding and validation, controller rules, localhost UDP communication, bounded windows, deterministic loss/delay/reordering impairment, Karn-style retransmission handling, benchmark configuration manifests, interval CSV output, benchmark execution, fairness calculation, interval time-series aggregation, and metric calculations. The latest verified result is 50 passing tests with no failures or errors.

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
--seed=20261025
--scenario=localhost
--network=uncontrolled
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

Each benchmark summary now has matching event, interval, and properties files. For example, `adaptive-localhost-adaptive-events.csv`, `adaptive-localhost-adaptive-intervals.csv`, and `adaptive.csv.properties`. Interval files contain sending rate, goodput, RTT, retransmission, and loss values at 100 ms resolution. Event files also record congestion-window changes.

## Offline plots

Install the optional analysis dependencies and plot a benchmark run:

```powershell
python -m pip install -r analysis/requirements.txt
python analysis/plot_results.py results/benchmark.csv --events results
```

The script writes `benchmark-summary.png` and `packet-sends.png` under `results/plots`. Add `--intervals results` to also generate `interval-rates-rtt.png`. Generated plots are local analysis artifacts and should not be committed unless they are intentionally included as documented experiment results.

To combine multiple benchmark summaries into one comparison report:

```powershell
python analysis/compare_results.py results/fixed.csv results/aimd.csv results/adaptive.csv --output-dir results/report
```

This produces `comparison.csv` and `comparison.png`. The report includes source file, scenario, acknowledgement rate, observed retransmission rate, goodput, and average RTT. It does not claim that a controller is better without controlled, repeated experiments.

To run all three controllers with identical localhost inputs:

```powershell
.\scripts\run-controller-comparison.ps1 -Packets 100 -Payload 256 -Seed 20261025
```

On Linux or macOS, use the equivalent shell runner:

```bash
PACKETS=100 PAYLOAD=256 SEED=20261025 ./scripts/run-controller-comparison.sh
```

For bottleneck, delay, and loss experiments, run the Java comparison through an authorized Linux `tc netem` environment. Record the interface, rate, delay, loss, seed, and trial number in the generated manifest; restore the interface configuration after every trial.

The guarded Linux matrix runner executes stable, bottleneck, delay, loss, and recovery profiles:

```bash
mvn test
ALLOW_TC_NETEM=1 TC_INTERFACE=lo ./scripts/run-netem-matrix.sh
```

The runner requires explicit authorization and an interface name, cleans up its temporary root qdisc on exit, and writes scenario/network metadata into each manifest. The recovery profile is currently a repeatable impaired profile; a true mid-run capacity transition remains a follow-up experiment.

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

1. Run repeated authorized netem trials and record measured results.
2. Add a true mid-run bandwidth transition and competing-flow runner.
3. Document experiment methodology, limitations, and conclusions based on measured data.

## Frontend plan

The frontend should remain isolated from the Java transport and congestion-control code initially. A React/Vite/TypeScript dashboard can load completed experiment files from a selected results directory or through drag-and-drop. It should not launch traffic or modify network settings in its first version.

The frontend data contract should be the generated manifest, summary CSV, interval CSV, and event CSV files. Its first views should be controller comparison, sending rate/goodput over time, RTT and estimated queueing delay, congestion-window changes, retransmission/loss events, and multi-flow fairness. A later phase may add a read-only Java HTTP or WebSocket service for live metrics; network control should remain an explicit, separately authorized operation.

Final evaluation reports must state the host OS/JDK, controller parameters, packet size, duration, network-emulation settings, random seed, number of trials, and whether a value is measured or estimated. Do not infer router queue occupancy from RTT alone, and do not treat localhost throughput as a bottleneck-capacity result.

## Project handoff

This README is also the maintained handoff record for future sessions. Update it when a milestone changes the implementation, test results, benchmark results, limitations, or next steps. Avoid creating a second progress document.

The current integration branch is `feature/impairment-integration`; `main` contains the previously merged milestones. The GitHub repository is [jaiwanthsuresh314/adaptive-congestion-control](https://github.com/jaiwanthsuresh314/adaptive-congestion-control).
