package org.gue.remoteneon;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

public class NeonAccessibilityService extends AccessibilityService {
    private static NeonAccessibilityService instance;

    public static NeonAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // tidak dipakai
    }

    @Override
    public void onInterrupt() {
        // tidak dipakai
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    public void performTap(float xPercent, float yPercent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;

        int x = (int) (xPercent * width);
        int y = (int) (yPercent * height);

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));

        dispatchGesture(builder.build(), null, null);
    }

    public void performSwipe(float x1Pct, float y1Pct, float x2Pct, float y2Pct, long duration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;

        int x1 = (int) (x1Pct * width);
        int y1 = (int) (y1Pct * height);
        int x2 = (int) (x2Pct * width);
        int y2 = (int) (y2Pct * height);

        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));

        dispatchGesture(builder.build(), null, null);
    }

    public void performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public void performHome() {
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public void performRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS);
    }
}
