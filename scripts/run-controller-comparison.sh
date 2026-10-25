#!/usr/bin/env bash
set -euo pipefail

PACKETS="${PACKETS:-100}"
PAYLOAD="${PAYLOAD:-256}"
SEED="${SEED:-20261025}"
OUTPUT_DIR="${OUTPUT_DIR:-results/comparison}"

mkdir -p "$OUTPUT_DIR"
for controller in fixed aimd adaptive; do
  java -cp target/classes edu.adaptivecc.experiments.BenchmarkMain \
    "--controller=$controller" \
    "--packets=$PACKETS" \
    "--payload=$PAYLOAD" \
    "--seed=$SEED" \
    "--output=$OUTPUT_DIR/$controller.csv"
done

python analysis/compare_results.py \
  "$OUTPUT_DIR/fixed.csv" "$OUTPUT_DIR/aimd.csv" "$OUTPUT_DIR/adaptive.csv" \
  --output-dir "$OUTPUT_DIR/report"
