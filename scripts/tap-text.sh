#!/bin/sh
# Taps the centre of the first on-screen element whose text is exactly "$2",
# reading positions from a uiautomator dump saved at "$1". Used by CI to drive
# the emulator the way a person would.
node=$(grep -o "text=\"$2\"[^>]*" "$1" | head -1)
bounds=$(echo "$node" | grep -o 'bounds="[^"]*"')
# bounds="[l,t][r,b]" -> four numbers
set -- $(echo "$bounds" | tr -c '0-9' ' ')
if [ $# -ne 4 ]; then
  echo "tap-text: element not found"
  exit 1
fi
adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
