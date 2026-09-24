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

        registerPlugin(ImmersiveModePlugin.class);
        registerPlugin(DownloadServicePlugin.class);
        super.onCreate(savedInstanceState);

        // Ensure the WebView backing layer is hardware-accelerated for proper clipping and allows text selection
        if (this.bridge != null && this.bridge.getWebView() != null) {
            WebView webView = this.bridge.getWebView();
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            webView.setLongClickable(true);
            webView.setHapticFeedbackEnabled(true);
        }

        // Ensure FLAG_SECURE is cleared so screenshots/screen recording
        // and text copying are completely permitted across the entire app, notes, and PDFs.
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

    private boolean isVideoPlaying = false;

    public void setVideoPlaying(boolean playing) {
        this.isVideoPlaying = playing;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                android.app.PictureInPictureParams.Builder builder = new android.app.PictureInPictureParams.Builder();
                builder.setAspectRatio(new android.util.Rational(16, 9));
                builder.setAutoEnterEnabled(playing);
                setPictureInPictureParams(builder.build());
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isVideoPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                android.app.PictureInPictureParams.Builder builder = new android.app.PictureInPictureParams.Builder();
                builder.setAspectRatio(new android.util.Rational(16, 9));
                enterPictureInPictureMode(builder.build());
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (this.bridge != null) {
            String script = "window.dispatchEvent(new CustomEvent('app:pip-mode-change', { detail: { isPip: " + isInPictureInPictureMode + " } }));";
            this.bridge.eval(script, null);
        }
    }

    private void requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            }
        }
    }
}