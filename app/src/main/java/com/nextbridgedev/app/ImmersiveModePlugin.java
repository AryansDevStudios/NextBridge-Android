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
                View decorView = getActivity().getWindow().getDecorView();
                WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getActivity().getWindow(), decorView);
                if (controller != null) {
                    controller.hide(WindowInsetsCompat.Type.systemBars());
                    controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
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
                View decorView = getActivity().getWindow().getDecorView();
                WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getActivity().getWindow(), decorView);
                if (controller != null) {
                    controller.show(WindowInsetsCompat.Type.systemBars());
                }
                call.resolve();
            } catch (Exception e) {
                call.reject(e.getMessage());
            }
        });
    }
}
