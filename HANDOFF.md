# AdaptiveCC Handoff

Last updated: 2026-10-17

## Current status

Milestones 1–3 are implemented and verified. This file is the single handoff record for future sessions; update this file rather than creating another progress document.

## Implemented

- Binary UDP protocol with magic, version, message type, sequence number, ACK number, payload length, timestamp, and payload.
- DATA, ACK, FIN, and FIN_ACK messages with strict length validation.
- UDP sender and receiver using real `DatagramSocket` communication.
- Receiver duplicate suppression and ACK generation.
- Bounded sender window with configurable timeout, retry limit, and window size.
- Timeout-based retransmission of unacknowledged DATA packets.
- Transfer metrics: original bytes, acknowledged packets, transmissions, retransmissions, goodput, RTT average, and RTT variation.
- CSV writer and executable localhost benchmark comparing window sizes 1, 4, and 16.

## Verification

Command:

```text
"C:\Program Files\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd" test
```

Result: `BUILD SUCCESS`; 6 tests passed, 0 failures, 0 errors.

Benchmark command:

```text
java -cp target\classes edu.adaptivecc.experiments.BenchmarkMain --packets=100 --payload=256 --timeout=250 --attempts=3 --output=results\loopback-benchmark.csv
```

Observed localhost results are in `results/loopback-benchmark.csv`. With 100 packets of 256 bytes and no impairment, all three windows acknowledged 100/100 packets with zero retransmissions. The measured goodput was approximately 0.81 MB/s for window 1, 2.30 MB/s for window 4, and 4.05 MB/s for window 16. These are local benchmark observations, not network-capacity claims.

## Important limitations

- RTT samples are recorded only for packets acknowledged on their first transmission; retransmitted packets are excluded from RTT statistics because their sample is ambiguous.
- The benchmark currently runs on localhost only and does not inject loss, delay, bandwidth limits, or competing traffic.
- Throughput/goodput metrics are currently transfer summaries, not timestamped time-series events.
- Fixed-rate, AIMD, and adaptive congestion controllers are not implemented yet.
- No command-line sender/receiver entry points exist yet; `BenchmarkMain` is the current executable demonstration.

## Next work

1. Add timestamped event collection and CSV time-series output.
2. Add deterministic impairment hooks or a test receiver that drops selected sequence numbers to exercise retransmission metrics.
3. Implement the fixed-rate controller and controller interface.
4. Implement the AIMD baseline.
5. Implement and test the deterministic adaptive controller.
6. Add README setup/run instructions and experiment methodology.

## Git state

The project directory originally had no `.git` directory or configured remote. A local repository has now been initialized and all project files are staged, but the first commit is blocked because Git author identity is not configured. No remote is configured, so pushing also requires the user’s repository URL. Do not invent either the author identity or remote.
