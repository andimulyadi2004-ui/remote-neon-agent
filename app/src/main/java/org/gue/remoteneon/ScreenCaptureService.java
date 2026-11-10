package org.gue.remoteneon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCapture";
    private static final String CHANNEL_ID = "remote_neon_channel";

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread handlerThread;
    private Handler handler;
    private WebSocketManager wsManager;

    private int screenWidth, screenHeight, screenDensity;
    private int resultCode;
    private Intent data;
    private String serverIP;

    private volatile boolean isCapturing = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Log.i(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent == null) {
                Log.e(TAG, "Intent is null");
                stopSelf();
                return START_NOT_STICKY;
            }

            serverIP = intent.getStringExtra("server_ip");
            resultCode = intent.getIntExtra("result_code", -1);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data = intent.getParcelableExtra("data", Intent.class);
            } else {
                data = intent.getParcelableExtra("data");
            }

            if (serverIP == null || data == null || resultCode == -1) {
                Log.e(TAG, "Missing required extras");
                stopSelf();
                return START_NOT_STICKY;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                        1,
                        createNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                );
            } else {
                startForeground(1, createNotification());
            }

            MediaProjectionManager mgr =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

            mediaProjection = mgr.getMediaProjection(resultCode, data);

            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null");
                stopSelf();
                return START_NOT_STICKY;
            }

            DisplayMetrics dm = getResources().getDisplayMetrics();
            screenWidth = dm.widthPixels;
            screenHeight = dm.heightPixels;
            screenDensity = dm.densityDpi;

            handlerThread = new HandlerThread("ScreenCapture");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());

            mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.w(TAG, "MediaProjection stopped");
                    stopSelf();
                }
            }, handler);

            wsManager = new WebSocketManager(serverIP);
            wsManager.connect();

            startScreenCapture();

        } catch (Exception e) {
            Log.e(TAG, "onStartCommand failed", e);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void startScreenCapture() {
        try {
            int captureWidth = screenWidth / 2;
            int captureHeight = screenHeight / 2;

            imageReader = ImageReader.newInstance(
                    captureWidth,
                    captureHeight,
                    PixelFormat.RGBA_8888,
                    2
            );

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "RemoteNeonDisplay",
                    captureWidth,
                    captureHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    handler
            );

            isCapturing = true;
            handler.postDelayed(this::captureFrame, 500);

            Log.i(TAG, "Screen capture started: " + captureWidth + "x" + captureHeight);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start screen capture", e);
            stopSelf();
        }
    }

    private void captureFrame() {
        if (!isCapturing) return;

        Image image = null;
        Bitmap bitmap = null;

        try {
            image = imageReader.acquireLatestImage();
            if (image == null) {
                handler.postDelayed(this::captureFrame, 500);
                return;
            }

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();

            bitmap = Bitmap.createBitmap(
                    image.getWidth() + rowPadding / pixelStride,
                    image.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);

            if (rowPadding > 0) {
                Bitmap cropped = Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        image.getWidth(),
                        image.getHeight()
                );
                bitmap.recycle();
                bitmap = cropped;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 40, baos);
            byte[] jpegBytes = baos.toByteArray();

            if (wsManager != null && wsManager.isConnected()) {
                wsManager.sendFrame(jpegBytes);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error capturing frame", e);
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            if (image != null) {
                try {
                    image.close();
                } catch (Exception ignored) {
                }
            }
        }

        if (isCapturing) {
            handler.postDelayed(this::captureFrame, 500);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Remote Neon Service",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Remote Neon Agent")
                .setContentText("Streaming layar aktif")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isCapturing = false;

        try {
            if (virtualDisplay != null) virtualDisplay.release();
            if (imageReader != null) imageReader.close();
            if (mediaProjection != null) mediaProjection.stop();
            if (wsManager != null) wsManager.disconnect();
            if (handlerThread != null) handlerThread.quitSafely();
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }

        Log.i(TAG, "Service destroyed");
    }
}
