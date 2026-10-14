#!/bin/sh
# Throwaway (D12). Reruns after the free-tier daily reset, in owner's order. Logs: spikes/work/rerun1.log
cd "$(dirname "$0")/.." || exit 1
export RUN=rerun1 RUN1=rerun1_s1
P="python3 -I -u spikes/s2.py"
{
  echo "start $(date -Is)"
  $P gemini-3.5-flash A480 r1
  for r in r9 r10; do $P gemini-3.5-flash A480 $r; $P gemini-3.5-flash B $r; done
  for r in r1 r3 r4 r6; do $P gemini-3.6-flash A480 $r; $P gemini-3.6-flash A720 $r; done
  $P gemini-3.6-flash YT r2 r9
  S="python3 -I -u spikes/s1.py gemini-3.6-flash"
  $S B_gemini-3.5-flash r1
  $S A480_gemini-3.5-flash r4 r5 r8
  $S B_gemini-3.5-flash --schema r1
  echo "end $(date -Is)"
} > spikes/work/rerun1.log 2>&1
