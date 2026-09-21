package com.nextbridgedev.app;

import android.content.Intent;
import android.os.Build;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "DownloadService")
public class DownloadServicePlugin extends Plugin {
    private static DownloadServicePlugin instance;

    @Override
    public void load() {
        super.load();
        instance = this;
    }

    public static void notifyAction(String action, String downloadId) {
        if (instance != null) {
            JSObject ret = new JSObject();
            ret.put("action", action);
            ret.put("downloadId", downloadId);
            instance.notifyListeners("downloadAction", ret);
        }
    }

    @PluginMethod
    public void startDownload(PluginCall call) {
        try {
            String title = call.getString("title", "Downloading Lectures...");
            String subtext = call.getString("subtext", "Starting download...");
            int progress = call.getInt("progress", 0);
            boolean isPaused = call.getBoolean("isPaused", false);
            String downloadId = call.getString("downloadId", "");

            Intent intent = new Intent(getContext(), DownloadForegroundService.class);
            intent.setAction(DownloadForegroundService.ACTION_START);
            intent.putExtra(DownloadForegroundService.EXTRA_TITLE, title);
            intent.putExtra(DownloadForegroundService.EXTRA_SUBTEXT, subtext);
            intent.putExtra(DownloadForegroundService.EXTRA_PROGRESS, progress);
            intent.putExtra(DownloadForegroundService.EXTRA_IS_PAUSED, isPaused);
            intent.putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, downloadId);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(intent);
            } else {
                getContext().startService(intent);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to start download service: " + e.getMessage());
        }
    }

    @PluginMethod
    public void updateProgress(PluginCall call) {
        try {
            String title = call.getString("title", "Downloading Lectures...");
            String subtext = call.getString("subtext", "In progress...");
            int progress = call.getInt("progress", 0);
            boolean isPaused = call.getBoolean("isPaused", false);
            String downloadId = call.getString("downloadId", "");

            Intent intent = new Intent(getContext(), DownloadForegroundService.class);
            intent.setAction(DownloadForegroundService.ACTION_UPDATE);
            intent.putExtra(DownloadForegroundService.EXTRA_TITLE, title);
            intent.putExtra(DownloadForegroundService.EXTRA_SUBTEXT, subtext);
            intent.putExtra(DownloadForegroundService.EXTRA_PROGRESS, progress);
            intent.putExtra(DownloadForegroundService.EXTRA_IS_PAUSED, isPaused);
            intent.putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, downloadId);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(intent);
            } else {
                getContext().startService(intent);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to update download service: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stopDownload(PluginCall call) {
        try {
            Intent intent = new Intent(getContext(), DownloadForegroundService.class);
            intent.setAction(DownloadForegroundService.ACTION_STOP);
            getContext().startService(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to stop download service: " + e.getMessage());
        }
    }
}
