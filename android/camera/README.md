# Host camera provider

Both Droidloom products build the pinned AOSP AIDL external camera provider.
The repository supplies its init service, VINTF `external/0` registration and
`external_camera_config.xml`. The runtime exposes an explicitly selected host
V4L2 capture device as Android `/dev/video0`; without that selection, the provider
has no camera to enumerate. Start the producer before starting Droidloom.

The AOSP provider at hardware/interfaces commit
`0162af698935100a590b7359581ac8b1b80693e5` accepts MJPEG and Z16 input. Its YU12
code handles output buffers, so it cannot directly capture scrcpy's YU12 stream.
`0001-droidloom-v4l2-camera-input.patch` adds single-planar V4L2 YUV420 input to
format enumeration, color capability and stream metadata, and both online and
offline capture. The negotiated V4L2 row stride survives offline handoff; raw
frames are validated before their Y, U and V planes are copied into the upstream
scaling/JPEG pipeline. Camera mute still supplies the upstream solid-color frame.
MJPEG decoding and capture remain supported.

`LensFacing` is a Droidloom configuration extension accepting `front`, `back` or
`external`. The configured phone feed is front-facing and already upright, so
the product uses `front` and sensor orientation 0. The default if this extension
is absent remains `external`. Hardware level remains AOSP's `EXTERNAL`; this does
not advertise autofocus or camera2 full/level-3 support. YU12 color capture
advertises `BACKWARD_COMPATIBLE`, which the pinned framework uses to include the
device in legacy Camera API enumeration.

The service runs as `cameraserver` with camera and graphics groups, without host
realtime scheduling capabilities or Android CPU task-profile assumptions.
The device VINTF manifest declares the provider instance
`ICameraProvider/external/0`; servicemanager requires that declaration before
the binary can register.

The raw-plane regression runs in `cargo test --locked -j 1 -p droidloom-update`.
To also run it with address and undefined-behavior sanitizers on the host:

```sh
clang++ -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined \
  -Iandroid/camera/include android/camera/tests/yuv420_input.cpp \
  -o .work/camera-yuv-test
.work/camera-yuv-test
```

The complete provider must additionally be built by `droidloom-update`, then
verified in Android with `dumpsys media.camera` and a real Camera/Camera2 capture.
Source integration and this host test alone do not prove a working camera stream.
