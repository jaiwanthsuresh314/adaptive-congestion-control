#!/usr/bin/env bash
set -euo pipefail

# Run only on an authorized Linux host. This script changes the qdisc on the
# explicitly supplied interface and removes the temporary qdisc on exit.
: "${TC_INTERFACE:?Set TC_INTERFACE to the authorized interface, for example lo}"
: "${ALLOW_TC_NETEM:?Set ALLOW_TC_NETEM=1 to confirm authorization}"
if [[ "$ALLOW_TC_NETEM" != "1" ]]; then
  echo "Refusing to change traffic control: set ALLOW_TC_NETEM=1 explicitly." >&2
  exit 2
fi

PACKETS="${PACKETS:-500}"
PAYLOAD="${PAYLOAD:-256}"
SEED="${SEED:-20261025}"
OUTPUT_ROOT="${OUTPUT_ROOT:-results/netem}"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$PROJECT_ROOT/$OUTPUT_ROOT"

cleanup() {
  tc qdisc del dev "$TC_INTERFACE" root 2>/dev/null || true
}
trap cleanup EXIT INT TERM

run_case() {
  local name="$1" profile="$2" qdisc="$3"
  echo "Running scenario=$name profile=$profile qdisc=$qdisc"
  if [[ "$qdisc" == "none" ]]; then
    cleanup
  else
    tc qdisc replace dev "$TC_INTERFACE" root netem $qdisc
  fi
  for controller in fixed aimd adaptive; do
    java -cp "$PROJECT_ROOT/target/classes" edu.adaptivecc.experiments.BenchmarkMain \
      "--controller=$controller" --packets="$PACKETS" --payload="$PAYLOAD" \
      --seed="$SEED" --scenario="$name" --network="$profile" \
      --output="$PROJECT_ROOT/$OUTPUT_ROOT/$name-$controller.csv"
  done
}

run_case stable stable none
run_case bottleneck "rate=1mbit" "rate 1mbit"
run_case delay "delay=50ms" "delay 50ms"
run_case loss "loss=2%" "loss 2%"
run_case recovery "rate=1mbit,delay=30ms" "rate 1mbit delay 30ms"

python "$PROJECT_ROOT/analysis/compare_results.py" "$PROJECT_ROOT/$OUTPUT_ROOT"/*.csv \
  --output-dir "$PROJECT_ROOT/$OUTPUT_ROOT/report"
