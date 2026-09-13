# Camera2 probe APK

This small headless APK verifies the Camera2 contract exposed by Droidloom's
external camera provider. It requests `CAMERA`, enumerates camera IDs, prefers a
front-facing device, opens a `YUV_420_888` `ImageReader`, and runs a repeating
preview request for at most ten seconds. Frames are counted and closed; raw
camera data is never written to disk. A successful run logs a non-zero frame
count under `DroidloomCameraProbe`.

Build with the locally installed Android SDK (from the repository root):

```sh
SDK=${ANDROID_HOME:-$HOME/android-sdk}
BT="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-35/android.jar"
OUT="$PWD/.work/camera-probe"
rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/res" "$OUT/apk" "$OUT/dex"
javac -source 8 -target 8 -classpath "$PLATFORM" -d "$OUT/classes" \
  android/camera/tests/probe/src/org/droidloom/camera/probe/CameraProbeActivity.java
"$BT/d8" --lib "$PLATFORM" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$PLATFORM" \
  --manifest android/camera/tests/probe/AndroidManifest.xml
mkdir -p "$OUT/staged" && unzip -q "$OUT/unsigned.apk" -d "$OUT/staged"
cp "$OUT/dex/classes.dex" "$OUT/staged/"
(cd "$OUT/staged" && zip -qr ../unsigned-with-dex.apk .)
"$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" \
  --ks-pass pass:android --out "$OUT/camera-probe.apk" "$OUT/unsigned-with-dex.apk"
```

Install and run on a test device after the provider is active:

```sh
adb install -r .work/camera-probe/camera-probe.apk
adb shell am start -n org.droidloom.camera.probe/.CameraProbeActivity
adb logcat -s DroidloomCameraProbe:I '*:S'
```
