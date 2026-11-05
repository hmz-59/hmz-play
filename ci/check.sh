#!/bin/bash
# Automatic check of the latest HMZ Play release on an Android 9 (API 28) emulator.
set -x
PKG=com.hmz.play
OUT=out
mkdir -p $OUT
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
adb wait-for-device
adb shell wm size 1280x720
adb shell settings put system accelerometer_rotation 0
adb shell wm density 160
adb shell settings put system user_rotation 1
adb emu geo fix 3.0600 50.6300
adb install -g HMZ-Play.apk || { echo "INSTALL FAILED" > $OUT/report.txt; exit 0; }
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.data.SplitService
adb shell settings put secure accessibility_enabled 1
adb shell cmd notification allow_listener $PKG/$PKG.data.MediaListener
adb logcat -c
adb shell am start -n $PKG/.MainActivity
sleep 20
adb emu geo fix 3.0600 50.6300
shot 01_home
for s in APPS MUSIC NOTIFS FUEL VTC LIVE SETTINGS; do
  adb shell am start -n $PKG/.MainActivity --es hmz_screen $s
  sleep 7
  shot "02_$s"
done
# lower part of settings
adb shell input swipe 700 600 700 150 400; sleep 2; shot 03_SETTINGS_2
adb shell input swipe 700 600 700 150 400; sleep 2; shot 03_SETTINGS_3
adb shell input swipe 700 600 700 150 400; sleep 2; shot 03_SETTINGS_4
adb shell input swipe 700 600 700 150 400; sleep 2; shot 03_SETTINGS_5
adb shell am start -n $PKG/.MainActivity --es hmz_screen VTC; sleep 4
adb shell input swipe 700 600 700 150 400; sleep 2; shot 03_VTC_2
# Affluence again (data had time to load)
adb shell am start -n $PKG/.MainActivity --es hmz_screen LIVE; sleep 20; shot 04_LIVE_loaded
adb shell am start -n $PKG/.MainActivity --es hmz_screen FUEL; sleep 10; shot 04_FUEL_loaded
adb shell am start -n $PKG/.MainActivity --es hmz_demo nav; sleep 12; shot 09_nav_destination
adb shell am start -n $PKG/.MainActivity --es hmz_screen HOME; sleep 3
for d in alert sign endofday; do
  adb shell am start -n $PKG/.MainActivity --es hmz_screen HOME --es hmz_demo $d
  sleep 3; shot "05_demo_$d"
  adb shell am start -n $PKG/.MainActivity --es hmz_demo close; sleep 2
done
adb shell am start -n $PKG/.MainActivity --es hmz_demo doors; sleep 1; shot 05_demo_doors
sleep 10
adb shell am start -n $PKG/.MainActivity --es hmz_screen HOME; sleep 15; shot 06_home_after
# open another app to see the dock (CarPlay bar)
adb shell am start -a android.settings.SETTINGS; sleep 4; shot 07_dock_over_other_app
adb shell input keyevent KEYCODE_HOME; sleep 4; shot 08_after_home_key
{
  echo "=== crashes ==="
  adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ANR in" -A25 | head -200
  echo "=== HMZ warnings/errors ==="
  adb logcat -d | grep -iE "hmz|com\.hmz" | grep -E " [EW] " | head -150
  echo "=== process alive ==="
  adb shell pidof $PKG
  echo "=== crash file ==="
  adb shell run-as $PKG sh -c 'ls -R files; cat files/crash*.txt 2>/dev/null' | head -80
} > $OUT/report.txt 2>&1
exit 0
