#!/bin/sh
# Taps the centre of the first on-screen element whose text or content
# description is exactly "$1". Dumps the screen fresh each time, since every
# tap changes it. Used by CI to drive the emulator the way a person would.
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb shell cat /sdcard/ui.xml > ui.xml
node=$(grep -oE "(text|content-desc)=\"$1\"[^>]*" ui.xml | head -1)
bounds=$(echo "$node" | grep -o 'bounds="[^"]*"')
# bounds="[l,t][r,b]" -> four numbers
set -- $(echo "$bounds" | tr -c '0-9' ' ')
if [ $# -ne 4 ]; then
  echo "tap-text: element not found"
  exit 1
fi
adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
