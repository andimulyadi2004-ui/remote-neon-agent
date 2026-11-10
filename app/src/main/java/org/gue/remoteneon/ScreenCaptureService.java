package org.gue.remoteneon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
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
    private static final String TAG = "ScreenCaptureService";
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

    private boolean isCapturing = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        serverIP = intent.getStringExtra("server_ip");
        resultCode = intent.getIntExtra("result_code", -1);
        data = intent.getParcelableExtra("data");

        if (serverIP == null || data == null || resultCode == -1) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(1, createNotification());

        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mgr.getMediaProjection(resultCode, data);

        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection null");
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

        wsManager = new WebSocketManager(serverIP);
        wsManager.connect();

        startScreenCapture();

        return START_STICKY;
    }

    private void startScreenCapture() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "RemoteNeonDisplay",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                handler
        );

        isCapturing = true;
        handler.postDelayed(this::captureFrame, 100);

        Log.i(TAG, "Screen capture started");
    }

    private void captureFrame() {
        if (!isCapturing) return;

        Image image = imageReader.acquireLatestImage();
        if (image != null) {
            try {
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * screenWidth;

                Bitmap bitmap = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                );
                bitmap.copyPixelsFromBuffer(buffer);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                byte[] jpegBytes = baos.toByteArray();

                if (wsManager != null && wsManager.isConnected()) {
                    wsManager.sendFrame(jpegBytes);
                }

                bitmap.recycle();
            } catch (Exception e) {
                Log.e(TAG, "Error capturing frame", e);
            } finally {
                image.close();
            }
        }

        handler.postDelayed(this::captureFrame, 100);
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
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Remote Neon Agent")
                .setContentText("Sedang streaming layar")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isCapturing = false;

        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) mediaProjection.stop();
        if (wsManager != null) wsManager.disconnect();
        if (handlerThread != null) handlerThread.quitSafely();

        Log.i(TAG, "Service destroyed");
    }
}
