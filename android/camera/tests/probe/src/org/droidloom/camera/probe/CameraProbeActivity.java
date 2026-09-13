package org.droidloom.camera.probe;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

/** A headless Camera2 smoke test for the Droidloom external camera provider. */
public final class CameraProbeActivity extends Activity {
    private static final String TAG = "DroidloomCameraProbe";
    private static final int CAMERA_PERMISSION = 42;
    private static final long TIMEOUT_MS = 10_000;

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private Handler mainHandler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private final AtomicInteger frames = new AtomicInteger();
    private volatile boolean shuttingDown;
    private final Runnable timeout = () -> {
        int count = frames.get();
        Log.i(TAG, "probe finished: frames=" + count);
        if (count < 3) {
            Log.e(TAG, "probe failed: fewer than three YUV_420_888 frames received");
        }
        finish();
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        mainHandler = new Handler(getMainLooper());
        cameraThread = new HandlerThread("droidloom-camera-probe");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        } else {
            cameraHandler.post(this::probe);
        }
        mainHandler.postDelayed(timeout, TIMEOUT_MS);
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request != CAMERA_PERMISSION || grants.length == 0
                || grants[0] != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "probe failed: CAMERA permission was denied");
            finish();
            return;
        }
        cameraHandler.post(this::probe);
    }

    private void probe() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] ids = manager.getCameraIdList();
            Log.i(TAG, "camera IDs=" + Arrays.toString(ids));
            if (ids.length == 0) {
                Log.e(TAG, "probe failed: CameraService exposes no devices");
                return;
            }
            String selected = selectFront(manager, ids);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(selected);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            android.hardware.camera2.params.StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null || map.getOutputSizes(ImageFormat.YUV_420_888) == null) {
                Log.e(TAG, "probe failed: camera " + selected + " has no YUV output sizes");
                return;
            }
            android.util.Size size = chooseSize(map.getOutputSizes(ImageFormat.YUV_420_888));
            Log.i(TAG, "opening camera=" + selected + " facing=" + facing
                    + " format=YUV_420_888 size=" + size.getWidth() + "x" + size.getHeight());
            reader = ImageReader.newInstance(size.getWidth(), size.getHeight(),
                    ImageFormat.YUV_420_888, 3);
            reader.setOnImageAvailableListener(this::onImage, cameraHandler);
            manager.openCamera(selected, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice device) {
                    if (shuttingDown) {
                        device.close();
                        return;
                    }
                    camera = device;
                    try {
                        device.createCaptureSession(Collections.singletonList(reader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override public void onConfigured(CameraCaptureSession configured) {
                                        if (shuttingDown) {
                                            configured.close();
                                            return;
                                        }
                                        session = configured;
                                        try {
                                            CaptureRequest.Builder builder = configured.getDevice()
                                                    .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                            builder.addTarget(reader.getSurface());
                                            CaptureRequest request = builder.build();
                                            configured.setRepeatingRequest(request, null, cameraHandler);
                                            Log.i(TAG, "capture repeating request started");
                                        } catch (CameraAccessException e) {
                                            Log.e(TAG, "failed to start repeating request", e);
                                        }
                                    }
                                    @Override public void onConfigureFailed(CameraCaptureSession ignored) {
                                        Log.e(TAG, "probe failed: capture session configuration failed");
                                    }
                                }, cameraHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "failed to create capture session", e);
                    }
                }
                @Override public void onDisconnected(CameraDevice device) {
                    Log.e(TAG, "camera disconnected");
                    device.close();
                }
                @Override public void onError(CameraDevice device, int error) {
                    Log.e(TAG, "camera open error=" + error);
                    device.close();
                }
            }, cameraHandler);
        } catch (CameraAccessException | RuntimeException e) {
            Log.e(TAG, "probe failed while enumerating/opening camera", e);
        }
    }

    private static String selectFront(CameraManager manager, String[] ids) throws CameraAccessException {
        for (String id : ids) {
            Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (Integer.valueOf(CameraCharacteristics.LENS_FACING_FRONT).equals(facing)) {
                return id;
            }
        }
        Log.w(TAG, "no front-facing camera; selecting first available camera");
        return ids[0];
    }

    private static android.util.Size chooseSize(android.util.Size[] sizes) {
        return Arrays.stream(sizes)
                .min(Comparator.comparingLong(size -> Math.abs((long) size.getWidth() * size.getHeight() - 640L * 480L)))
                .orElseThrow(() -> new IllegalArgumentException("empty YUV size list"));
    }

    private void onImage(ImageReader source) {
        if (shuttingDown) return;
        Image image;
        try {
            image = source.acquireLatestImage();
        } catch (IllegalStateException e) {
            if (!shuttingDown) Log.e(TAG, "failed to acquire camera image", e);
            return;
        }
        if (image == null) return;
        try {
            int count = frames.incrementAndGet();
            if (count == 1 || count % 30 == 0) {
                Log.i(TAG, "frame=" + count + " dimensions=" + image.getWidth() + "x" + image.getHeight()
                        + " planes=" + image.getPlanes().length);
            }
        } finally {
            image.close();
        }
    }

    @Override
    protected void onDestroy() {
        shuttingDown = true;
        if (mainHandler != null) mainHandler.removeCallbacks(timeout);
        if (session != null) session.close();
        if (camera != null) camera.close();
        if (reader != null) reader.close();
        if (cameraThread != null) {
            cameraThread.quitSafely();
        }
        super.onDestroy();
    }
}
