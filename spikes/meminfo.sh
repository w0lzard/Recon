#!/usr/bin/env bash
# Low-end gate memory sampler: every 1 s, sums PSS over every process owned by the app's UID
# (app + yt-dlp libpython child). Per pid: `dumpsys meminfo <pid>` TOTAL, else Pss summed from
# /proc/<pid>/smaps (needs `adb root`, i.e. an AVD or debuggable build). Stop with Ctrl-C / kill;
# prints the peak sum on exit.
# Usage: spikes/meminfo.sh <package> [adb-serial] > samples.tsv
set -u
PKG=$1; export ANDROID_SERIAL=${2:-${ANDROID_SERIAL:-}}
UID_=$(adb shell dumpsys package "$PKG" | sed -n 's/.*userId=\([0-9]*\).*/\1/p' | head -1)
[ -n "$UID_" ] || { echo "no uid for $PKG" >&2; exit 1; }
peak=0; peak_n=0
trap 'echo "PEAK_KB=$peak PROCS_AT_PEAK=$peak_n" >&2; exit 0' INT TERM
echo -e "time\tpids\tpss_kb_each\tsum_kb"
while :; do
  pids=$(adb shell ps -A -o PID,UID 2>/dev/null | awk -v u="$UID_" '$2==u{print $1}' | tr -d '\r')
  sum=0; n=0; each=""
  for p in $pids; do
    kb=$(adb shell dumpsys meminfo "$p" 2>/dev/null | awk '/^ *TOTAL[ :]/{print $2; exit}' | tr -d '\r')
    [[ "$kb" =~ ^[0-9]+$ ]] || kb=$(adb shell "cat /proc/$p/smaps 2>/dev/null" | awk '/^Pss:/{s+=$2} END{print s+0}' | tr -d '\r')
    sum=$((sum + kb)); n=$((n + 1)); each="$each$p:$kb "
  done
  echo -e "$(date +%T)\t$n\t$each\t$sum"
  if [ "$sum" -gt "$peak" ]; then peak=$sum; peak_n=$n; fi
  sleep 1
done
