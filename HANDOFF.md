# AdaptiveCC Handoff

Last updated: 2026-10-17

## Current status

Milestones 1–4 are implemented and verified. This file is the single handoff record for future sessions; update this file rather than creating another progress document.

## Implemented

- Binary UDP protocol with magic, version, message type, sequence number, ACK number, payload length, timestamp, and payload.
- DATA, ACK, FIN, and FIN_ACK messages with strict length validation.
- UDP sender and receiver using real `DatagramSocket` communication.
- Receiver duplicate suppression and ACK generation.
- Bounded sender window with configurable timeout, retry limit, and window size.
- Timeout-based retransmission of unacknowledged DATA packets.
- Transfer metrics: original bytes, acknowledged packets, transmissions, retransmissions, goodput, RTT average, and RTT variation.
- CSV writer and executable localhost benchmark comparing window sizes 1, 4, and 16.
- Shared `CongestionController` interface used by transport feedback.
- Fixed-rate controller with configurable window and byte-rate pacing.
- AIMD baseline with additive increase, multiplicative decrease, and configurable window bounds.
- Controller unit tests and benchmark selection through `--controller=fixed|aimd`.

## Verification

Command:

```text
"C:\Program Files\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd" test
```

Result: `BUILD SUCCESS`; 8 tests passed, 0 failures, 0 errors.

Benchmark command:

```text
java -cp target\classes edu.adaptivecc.experiments.BenchmarkMain --packets=100 --payload=256 --timeout=250 --attempts=3 --output=results\loopback-benchmark.csv
```

Observed localhost results are in `results/loopback-benchmark.csv`. With 100 packets of 256 bytes and no impairment, all three windows acknowledged 100/100 packets with zero retransmissions. The measured goodput was approximately 0.81 MB/s for window 1, 2.30 MB/s for window 4, and 4.05 MB/s for window 16. These are local benchmark observations, not network-capacity claims.

Baseline commands:

```text
java -cp target\\classes edu.adaptivecc.experiments.BenchmarkMain --controller=fixed --packets=100 --payload=256 --rate=1000000 --output=results\\fixed-baseline.csv
java -cp target\\classes edu.adaptivecc.experiments.BenchmarkMain --controller=aimd --packets=100 --payload=256 --output=results\\aimd-baseline.csv
```

Observed baseline results are in `results/fixed-baseline.csv` and `results/aimd-baseline.csv`. The fixed-rate run acknowledged 100/100 packets with zero retransmissions for windows 1, 4, and 16; measured goodput was approximately 136,464, 166,536, and 160,959 bytes/sec respectively. The AIMD run acknowledged 100/100 packets with zero retransmissions and ended at window 14, with approximately 1,518,810 bytes/sec goodput. These are single localhost observations, not comparative network conclusions.

## Important limitations

- RTT samples are recorded only for packets acknowledged on their first transmission; retransmitted packets are excluded from RTT statistics because their sample is ambiguous.
- The benchmark currently runs on localhost only and does not inject loss, delay, bandwidth limits, or competing traffic.
- Throughput/goodput metrics are currently transfer summaries, not timestamped time-series events.
- The adaptive congestion controller is not implemented yet.
- No command-line sender/receiver entry points exist yet; `BenchmarkMain` is the current executable demonstration.

## Next work

1. Add timestamped event collection and CSV time-series output.
2. Add deterministic impairment hooks or a test receiver that drops selected sequence numbers to exercise retransmission metrics.
3. Implement and test the deterministic adaptive controller.
4. Add README setup/run instructions and experiment methodology.

## Git state

Git is configured with the user-provided identity and `origin` points to `https://github.com/jaiwanthsuresh314/adaptive-congestion-control.git`. Milestones 1–3 are on `main`; Milestone 4 is being prepared on branch `milestone-4-baselines` for review. Future sessions should update this handoff file only for progress tracking.
