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
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final int NOTIFICATION_PERMISSION_CODE = 9902;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar);

        // Enforce 32-bit ARGB window buffer with alpha channel support for popups & selection handles
        getWindow().setFormat(PixelFormat.RGBA_8888);

        // Temporarily commented out to prevent it from altering window bounds
        // and breaking the GPU overlay compositor for text selection.
        // registerPlugin(ImmersiveModePlugin.class);

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

            // Replaces the deprecated decor.setSystemUiVisibility() which disrupts modern window compositing
            WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (windowInsetsController != null) {
                // False enforces light text/icons against the #0a0a0a dark background
                windowInsetsController.setAppearanceLightStatusBars(false);
                windowInsetsController.setAppearanceLightNavigationBars(false);
            }
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