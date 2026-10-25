"""Combine benchmark summaries into a comparison report.

Example:
    python analysis/compare_results.py results/*.csv --output-dir results/report
"""

from __future__ import annotations

import argparse
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("summaries", nargs="+", type=Path)
    parser.add_argument("--output-dir", type=Path, default=Path("results/report"))
    arguments = parser.parse_args()

    frames = []
    for path in arguments.summaries:
        if path.name.endswith("-events.csv") or path.name.endswith(".properties"):
            continue
        frame = pd.read_csv(path)
        if not frame.empty:
            frame.insert(0, "source", path.name)
            frames.append(frame)
    if not frames:
        raise ValueError("no benchmark summary rows were found")

    combined = pd.concat(frames, ignore_index=True)
    combined["acknowledgement_rate"] = combined["acknowledged"] / combined["packets"]
    combined["retransmission_rate_observed"] = combined["retransmissions"] / combined["packets"]
    arguments.output_dir.mkdir(parents=True, exist_ok=True)
    combined.to_csv(arguments.output_dir / "comparison.csv", index=False)

    figure, axes = plt.subplots(2, 1, figsize=(11, 7), constrained_layout=True)
    labels = combined["source"].astype(str) + ":" + combined["scenario"].astype(str)
    axes[0].bar(labels, combined["goodput_bytes_per_sec"])
    axes[0].set_ylabel("Goodput (bytes/s)")
    axes[1].bar(labels, combined["average_rtt_ms"])
    axes[1].set_ylabel("Average RTT (ms)")
    for axis in axes:
        axis.tick_params(axis="x", labelrotation=35)
        axis.grid(axis="y", alpha=0.3)
    figure.savefig(arguments.output_dir / "comparison.png", dpi=150)
    plt.close(figure)


if __name__ == "__main__":
    main()
