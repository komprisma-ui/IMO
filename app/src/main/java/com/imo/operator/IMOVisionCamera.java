package com.imo.operator;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Front-camera visual sensor for IMO. Frames are kept only in memory and are never written to disk.
 * This is visual context, not biometric identity verification.
 */
public final class IMOVisionCamera implements AutoCloseable {
    private final Context context;
    private final Object lock = new Object();
    private HandlerThread thread;
    private Handler handler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private volatile byte[] latestJpeg;

    public IMOVisionCamera(Context context) { this.context = context.getApplicationContext(); }

    public boolean start() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return false;
        synchronized (lock) { if (camera != null) return true; }
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String id = findFrontCamera(manager);
            if (id == null) return false;
            CameraCharacteristics cc = manager.getCameraCharacteristics(id);
            android.util.Size size = chooseSize(cc);
            reader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 2);
            reader.setOnImageAvailableListener(r -> {
                Image image = null;
                try {
                    image = r.acquireLatestImage();
                    if (image == null) return;
                    ByteBuffer b = image.getPlanes()[0].getBuffer();
                    byte[] data = new byte[b.remaining()];
                    b.get(data);
                    if (data.length > 0) latestJpeg = data;
                } finally { if (image != null) image.close(); }
            }, handlerOrStart());
            CountDownLatch opened = new CountDownLatch(1);
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) {
                    synchronized (lock) { camera = d; }
                    opened.countDown();
                    createSession(d);
                }
                @Override public void onDisconnected(CameraDevice d) { d.close(); synchronized (lock) { if (camera == d) camera = null; } opened.countDown(); }
                @Override public void onError(CameraDevice d, int error) { d.close(); synchronized (lock) { if (camera == d) camera = null; } opened.countDown(); }
            }, handlerOrStart());
            if (!opened.await(4, TimeUnit.SECONDS)) { close(); return false; }
            return camera != null;
        } catch (Exception e) { close(); return false; }
    }

    private Handler handlerOrStart() {
        if (handler != null) return handler;
        thread = new HandlerThread("IMO-Vision-Camera");
        thread.start();
        handler = new Handler(thread.getLooper());
        return handler;
    }

    private void createSession(CameraDevice d) {
        try {
            final Surface surface = reader.getSurface();
            d.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) {
                    synchronized (lock) { session = s; }
                    try {
                        android.hardware.camera2.CaptureRequest.Builder b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        b.addTarget(surface);
                        b.set(android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                                android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        s.setRepeatingRequest(b.build(), null, handler);
                    } catch (CameraAccessException ignored) { }
                }
                @Override public void onConfigureFailed(CameraCaptureSession s) { }
            }, handler);
        } catch (Exception ignored) { }
    }

    private static String findFrontCamera(CameraManager m) throws CameraAccessException {
        for (String id : m.getCameraIdList()) {
            CameraCharacteristics c = m.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) return id;
        }
        return null;
    }

    private static android.util.Size chooseSize(CameraCharacteristics c) {
        android.util.Size[] sizes = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                .getOutputSizes(ImageFormat.JPEG);
        if (sizes == null || sizes.length == 0) return new android.util.Size(640, 480);
        return Arrays.stream(sizes)
                .filter(s -> s.getWidth() <= 640 && s.getHeight() <= 480)
                .min(Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight()))
                .orElse(sizes[sizes.length - 1]);
    }

    /** Returns the most recent JPEG frame, or null if the camera has not produced one yet. */
    public byte[] latestFrame() {
        byte[] x = latestJpeg;
        return x == null ? null : Arrays.copyOf(x, x.length);
    }

    public boolean hasFrame() { return latestJpeg != null && latestJpeg.length > 0; }

    @Override public void close() {
        synchronized (lock) {
            try { if (session != null) session.close(); } catch (Exception ignored) { }
            try { if (camera != null) camera.close(); } catch (Exception ignored) { }
            session = null; camera = null;
        }
        try { if (reader != null) reader.close(); } catch (Exception ignored) { }
        reader = null; latestJpeg = null;
        if (thread != null) { thread.quitSafely(); thread = null; handler = null; }
    }
}
