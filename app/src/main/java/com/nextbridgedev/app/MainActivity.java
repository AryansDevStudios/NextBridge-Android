package com.nextbridgedev.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final int NOTIFICATION_PERMISSION_CODE = 9902;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Enforce 32-bit ARGB window buffer with alpha channel support for popups & selection handles
        getWindow().setFormat(PixelFormat.RGBA_8888);

        registerPlugin(ImmersiveModePlugin.class);
        registerPlugin(DownloadServicePlugin.class);
        super.onCreate(savedInstanceState);

        // Ensure the WebView backing layer is hardware-accelerated for proper clipping
        if (this.bridge != null && this.bridge.getWebView() != null) {
            WebView webView = this.bridge.getWebView();
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        // Ensure FLAG_SECURE is cleared by default so screenshots/screen recording
        // are allowed across the normal GUI, notes, and profile.
        // FLAG_SECURE is only dynamically enabled during active video playback.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);

        // Ensure status bar (notification panel) and navigation bar match the black theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(android.graphics.Color.parseColor("#0a0a0a"));
            getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#0a0a0a"));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            // Clear light status bar flag so icons in the status bar are WHITE/LIGHT
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Clear light navigation bar flag so nav buttons are WHITE/LIGHT
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }

        requestAppPermissions();
    }

    private void requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            }
        }
    }
}