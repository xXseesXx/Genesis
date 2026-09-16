#!/usr/bin/env bash
set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$root_dir"

task="${1:-help}"
if (($#)); then shift; fi
argument=""
verbose=0
for value in "$@"; do
  if [[ "$value" == --verbose ]]; then
    verbose=1
  elif [[ -z "$argument" ]]; then
    argument="$value"
  else
    printf "Unexpected argument '%s'.\n" "$value" >&2
    exit 2
  fi
done
target="$argument"
port="${argument:-8787}"

targets=(core continents hydrology-labs tectonic rivers tectonic-viewer refinement geology all)

usage() {
  printf '%s\n' \
    'Genesis terrain lab' \
    '  ./genesis.sh check [target]       # default: tectonic' \
    '  ./genesis.sh gallery [target]     # default: tectonic-viewer' \
    '  ./genesis.sh serve [port]' \
    '  ./genesis.sh build                # Java 8 core JAR only' \
    '  ./genesis.sh list' \
    '  add --verbose to stream tool/test output'
}

contains_target() {
  local candidate="$1" item
  for item in "${targets[@]}"; do [[ "$item" == "$candidate" ]] && return 0; done
  return 1
}

run_quiet() {
  local label="$1" log_name="$2" start elapsed
  shift 2
  start=$SECONDS
  if (( verbose )); then
    "$@"
  elif ! "$@" >"build/logs/${log_name}-$$.log" 2>&1; then
    tail -n 80 "build/logs/${log_name}-$$.log"
    printf 'FAIL %s (log: build/logs/%s-%s.log)\n' "$label" "$log_name" "$$" >&2
    return 1
  fi
  elapsed=$((SECONDS - start))
  printf 'OK  %s (%ss)\n' "$label" "$elapsed"
}

case "$task" in
  help|-h|--help) usage; exit 0 ;;
  list) printf '%s\n' "${targets[@]}"; exit 0 ;;
  build|check|test|serve|gallery) ;;
  *) printf "Unknown task '%s'.\n" "$task" >&2; usage >&2; exit 2 ;;
esac

if [[ "$task" == check && -z "$target" ]]; then target=tectonic; fi
if [[ "$task" == test && -z "$target" ]]; then target=all; fi
if [[ "$task" == gallery && -z "$target" ]]; then target=tectonic-viewer; fi
if [[ "$task" == check || "$task" == test || "$task" == gallery ]]; then
  if ! contains_target "$target"; then
    printf "Unknown target '%s'. Run ./genesis.sh list.\n" "$target" >&2
    exit 2
  fi
fi
if [[ "$task" == serve ]]; then
  if [[ ! "$port" =~ ^[0-9]+$ ]] || ((port < 1 || port > 65535)); then
    printf "Invalid port '%s'.\n" "$port" >&2
    exit 2
  fi
fi
if [[ "$task" == build && -n "$target" ]]; then
  printf 'build does not accept a target.\n' >&2
  exit 2
fi

mkdir -p build/classes build/gallery build/logs
mapfile -t core_sources < <(find core/src/main/java -type f -name '*.java' -print | sort)
run_quiet 'compile core' compile-core javac --release 8 -encoding UTF-8 -d build/classes "${core_sources[@]}"

if [[ "$task" == build ]]; then
  run_quiet 'package core JAR' package-core jar --create --file build/genesis-core.jar -C build/classes genesis/core
  exit 0
fi

source_roots=(oracle/src harness/server/src)
if [[ "$task" == check || "$task" == test || "$task" == gallery ]]; then source_roots+=(harness/gates/src); fi
mapfile -t harness_sources < <(find "${source_roots[@]}" -type f -name '*.java' -print | sort)
compile_label='compile gates'
[[ "$task" == serve ]] && compile_label='compile viewer'
run_quiet "$compile_label" compile-harness javac --release 21 -encoding UTF-8 -cp build/classes -d build/classes "${harness_sources[@]}"

case "$task" in
  check|test)
    run_quiet "check $target" "check-$target" java -Djava.awt.headless=true -cp build/classes genesis.harness.Gates "$target"
    ;;
  gallery)
    run_quiet "gallery $target" "gallery-$target" java -Djava.awt.headless=true -cp build/classes genesis.harness.Gates gallery "$target"
    ;;
  serve)
    printf 'SERVE http://127.0.0.1:%s/ (Ctrl+C to stop)\n' "$port"
    exec java -Djava.awt.headless=true -cp build/classes genesis.harness.Server "$port"
    ;;
esac
