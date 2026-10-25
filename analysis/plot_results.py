"""Plot benchmark summary and sender event CSV files.

Usage:
    python analysis/plot_results.py results/benchmark.csv --events results

The script is intentionally offline: it reads files produced by BenchmarkMain
and never opens sockets or changes experiment state.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd


def plot_summary(summary: pd.DataFrame, output: Path) -> None:
    value_columns = {
        "goodput_bytes_per_sec": "Goodput (bytes/s)",
        "average_rtt_ms": "Average RTT (ms)",
        "retransmissions": "Retransmissions",
    }
    available = [column for column in value_columns if column in summary.columns]
    if not available:
        raise ValueError("summary CSV has no supported metric columns")

    figure, axes = plt.subplots(len(available), 1, squeeze=False, figsize=(10, 3 * len(available)))
    for axis, column in zip(axes[:, 0], available):
        axis.bar(summary["scenario"].astype(str), summary[column])
        axis.set_ylabel(value_columns[column])
        axis.tick_params(axis="x", labelrotation=30)
        axis.grid(axis="y", alpha=0.3)
    figure.tight_layout()
    figure.savefig(output, dpi=150)
    plt.close(figure)


def plot_events(event_files: list[Path], output: Path) -> None:
    figure, axis = plt.subplots(figsize=(10, 5))
    for event_file in event_files:
        events = pd.read_csv(event_file)
        if events.empty:
            continue
        start = events["timestamp_nanos"].min()
        sent = events[events["event_type"].isin(["PACKET_SENT", "PACKET_RETRANSMITTED"])]
        axis.scatter((sent["timestamp_nanos"] - start) / 1_000_000_000, sent["packet_id"],
                     s=10, label=event_file.stem)
        windows = events[events["event_type"] == "WINDOW_CHANGED"]
        if not windows.empty:
            axis.step((windows["timestamp_nanos"] - start) / 1_000_000_000,
                      windows["packet_id"], where="post", alpha=0.5)
    axis.set_xlabel("Elapsed time (s)")
    axis.set_ylabel("Packet sequence")
    axis.grid(alpha=0.3)
    if event_files:
        axis.legend(fontsize="small")
    figure.tight_layout()
    figure.savefig(output, dpi=150)
    plt.close(figure)


def plot_intervals(interval_files: list[Path], output: Path) -> None:
    figure, axes = plt.subplots(2, 1, figsize=(10, 7), sharex=True)
    for interval_file in interval_files:
        intervals = pd.read_csv(interval_file)
        if intervals.empty:
            continue
        elapsed = (intervals["interval_start_nanos"] - intervals["interval_start_nanos"].min()) / 1_000_000_000
        label = interval_file.stem
        axes[0].plot(elapsed, intervals["sending_rate_bytes_per_sec"], label=label)
        axes[0].plot(elapsed, intervals["goodput_bytes_per_sec"], linestyle="--")
        axes[1].plot(elapsed, intervals["average_rtt_ms"], label=label)
    axes[0].set_ylabel("Rate (bytes/s)")
    axes[1].set_ylabel("Average RTT (ms)")
    axes[1].set_xlabel("Elapsed time (s)")
    for axis in axes:
        axis.grid(alpha=0.3)
        axis.legend(fontsize="small")
    figure.tight_layout()
    figure.savefig(output, dpi=150)
    plt.close(figure)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("summary", type=Path, help="BenchmarkMain summary CSV")
    parser.add_argument("--events", type=Path, help="Directory containing *-events.csv files")
    parser.add_argument("--intervals", type=Path, help="Directory containing *-intervals.csv files")
    parser.add_argument("--output-dir", type=Path, default=Path("results/plots"))
    arguments = parser.parse_args()
    arguments.output_dir.mkdir(parents=True, exist_ok=True)

    summary = pd.read_csv(arguments.summary)
    plot_summary(summary, arguments.output_dir / "benchmark-summary.png")
    if arguments.events:
        plot_events(sorted(arguments.events.glob("*-events.csv")),
                    arguments.output_dir / "packet-sends.png")
    if arguments.intervals:
        plot_intervals(sorted(arguments.intervals.glob("*-intervals.csv")),
                       arguments.output_dir / "interval-rates-rtt.png")


if __name__ == "__main__":
    main()
