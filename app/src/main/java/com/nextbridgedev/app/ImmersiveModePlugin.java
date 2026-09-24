package com.nextbridgedev.app;

import android.view.View;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "ImmersiveMode")
public class ImmersiveModePlugin extends Plugin {

    @PluginMethod
    public void enter(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                android.view.Window window = getActivity().getWindow();
                View decorView = window.getDecorView();

                // 1. Modern Android 11+ (API 30+) insets controller
                WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decorView);
                if (controller != null) {
                    controller.hide(WindowInsetsCompat.Type.systemBars());
                    controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }

                // 2. Legacy sticky immersive flags for comprehensive backward compatibility
                decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );

                call.resolve();
            } catch (Exception e) {
                call.reject(e.getMessage());
            }
        });
    }

    @PluginMethod
    public void exit(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                android.view.Window window = getActivity().getWindow();
                View decorView = window.getDecorView();

                // 1. Modern Android 11+ insets controller
                WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decorView);
                if (controller != null) {
                    controller.show(WindowInsetsCompat.Type.systemBars());
                }

                // 2. Clear legacy flags
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);

                call.resolve();
            } catch (Exception e) {
                call.reject(e.getMessage());
            }
        });
    }

    @PluginMethod
    public void enterPip(PluginCall call) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getActivity().runOnUiThread(() -> {
                try {
                    android.app.PictureInPictureParams.Builder builder = new android.app.PictureInPictureParams.Builder();
                    builder.setAspectRatio(new android.util.Rational(16, 9));
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        builder.setAutoEnterEnabled(true);
                    }
                    boolean success = getActivity().enterPictureInPictureMode(builder.build());
                    if (success) {
                        call.resolve();
                    } else {
                        call.reject("Could not enter Picture-in-Picture");
                    }
                } catch (Exception e) {
                    call.reject(e.getMessage());
                }
            });
        } else {
            call.reject("Picture-in-Picture requires Android 8.0 or higher");
        }
    }

    @PluginMethod
    public void setVideoPlaying(PluginCall call) {
        boolean playing = Boolean.TRUE.equals(call.getBoolean("playing", false));
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setVideoPlaying(playing);
        }
        call.resolve();
    }

    @PluginMethod
    public void isPipSupported(PluginCall call) {
        boolean supported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
            && getActivity().getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE);
        com.getcapacitor.JSObject ret = new com.getcapacitor.JSObject();
        ret.put("supported", supported);
        call.resolve(ret);
    }
}
