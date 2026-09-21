package com.nextbridgedev.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONArray;

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

    public static void notifyProgress(DownloadForegroundService.Task task) {
        if (instance != null && task != null) {
            JSObject ret = new JSObject();
            ret.put("id", task.id);
            ret.put("title", task.title);
            ret.put("quality", task.quality);
            ret.put("percent", task.percent);
            long downloaded = task.downloadedBytes.get();
            long total = Math.max(downloaded, task.totalBytes);
            ret.put("downloadedBytes", downloaded);
            ret.put("totalBytes", total);
            ret.put("formattedDownloaded", DownloadForegroundService.formatBytes(downloaded));
            ret.put("formattedTotal", DownloadForegroundService.formatBytes(total));
            ret.put("speed", DownloadForegroundService.formatSpeed(task.speed));
            ret.put("eta", DownloadForegroundService.formatEta(task.eta));
            ret.put("status", task.status);
            instance.notifyListeners("downloadProgress", ret);
        }
    }

    public static void notifyCompleted(DownloadForegroundService.Task task) {
        if (instance != null && task != null) {
            JSObject ret = new JSObject();
            ret.put("id", task.id);
            ret.put("title", task.title);
            ret.put("quality", task.quality);
            ret.put("formattedSize", DownloadForegroundService.formatBytes(task.downloadedBytes.get()));
            instance.notifyListeners("downloadCompleted", ret);
        }
    }

    @PluginMethod
    public void startDownload(PluginCall call) {
        try {
            String id = call.getString("id", "");
            String title = call.getString("title", "Lecture");
            String subjectName = call.getString("subjectName", "Course");
            String folderPath = call.getString("folderPath", "");
            long duration = call.getInt("duration", 0);
            String thumbnail = call.getString("thumbnail", "");
            String quality = call.getString("quality", "720p");
            String playlistUrl = call.getString("playlistUrl", "");
            long totalBytes = call.getInt("totalBytes", 0);

            if (id.isEmpty() || playlistUrl.isEmpty()) {
                call.reject("Missing download id or playlist URL");
                return;
            }

            Intent intent = new Intent(getContext(), DownloadForegroundService.class);
            intent.setAction(DownloadForegroundService.ACTION_START_TASK);
            intent.putExtra(DownloadForegroundService.EXTRA_ID, id);
            intent.putExtra(DownloadForegroundService.EXTRA_TITLE, title);
            intent.putExtra(DownloadForegroundService.EXTRA_SUBJECT, subjectName);
            intent.putExtra(DownloadForegroundService.EXTRA_FOLDER, folderPath);
            intent.putExtra(DownloadForegroundService.EXTRA_DURATION, duration);
            intent.putExtra(DownloadForegroundService.EXTRA_THUMBNAIL, thumbnail);
            intent.putExtra(DownloadForegroundService.EXTRA_QUALITY, quality);
            intent.putExtra(DownloadForegroundService.EXTRA_PLAYLIST_URL, playlistUrl);
            intent.putExtra(DownloadForegroundService.EXTRA_TOTAL_BYTES, totalBytes);

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
    public void pauseDownload(PluginCall call) {
        try {
            String id = call.getString("id", "");
            Intent intent = new Intent(getContext(), DownloadForegroundService.class);
            intent.setAction(DownloadForegroundService.ACTION_PAUSE_TASK);
            intent.putExtra(DownloadForegroundService.EXTRA_ID, id);
            getContext().startService(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to pause download: " + e.getMessage());
        }
    }

    @PluginMethod
    public void resumeDownload(PluginCall call) {
        try {
            String id = call.getString("id", "");
            Intent intent = new Intent(getContext(), DownloadForegroundService.class);
            intent.setAction(DownloadForegroundService.ACTION_RESUME_TASK);
            intent.putExtra(DownloadForegroundService.EXTRA_ID, id);
            getContext().startService(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to resume download: " + e.getMessage());
        }
    }

    @PluginMethod
    public void cancelDownload(PluginCall call) {
        try {
            String id = call.getString("id", "");
            Intent intent = new Intent(getContext(), DownloadForegroundService.class);
            intent.setAction(DownloadForegroundService.ACTION_CANCEL_TASK);
            intent.putExtra(DownloadForegroundService.EXTRA_ID, id);
            getContext().startService(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to cancel download: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getActiveTasks(PluginCall call) {
        DownloadForegroundService service = DownloadForegroundService.getInstance();
        JSObject ret = new JSObject();
        if (service != null) {
            try {
                ret.put("tasks", new JSArray(service.getActiveTasksJson().toString()));
            } catch (Exception e) {
                ret.put("tasks", new JSArray());
            }
        } else {
            ret.put("tasks", new JSArray());
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void getCompletedDownloads(PluginCall call) {
        JSONArray arr = DownloadForegroundService.getCompletedFromRegistry(getContext());
        JSObject ret = new JSObject();
        try {
            ret.put("downloads", new JSArray(arr.toString()));
        } catch (Exception e) {
            ret.put("downloads", new JSArray());
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void deleteDownload(PluginCall call) {
        String id = call.getString("id", "");
        if (!id.isEmpty()) {
            DownloadForegroundService.deleteDownloadFromRegistry(getContext(), id);
        }
        call.resolve();
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        JSObject ret = new JSObject();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean granted = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            ret.put("notifications", granted ? "granted" : "prompt");
        } else {
            ret.put("notifications", "granted");
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9902);
            }
        }
        JSObject ret = new JSObject();
        ret.put("notifications", "requested");
        call.resolve(ret);
    }
}
