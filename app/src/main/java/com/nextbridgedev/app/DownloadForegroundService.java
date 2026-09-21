package com.nextbridgedev.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadForegroundService extends Service {
    public static final String CHANNEL_ID = "nextbridge_downloads_channel";
    public static final int NOTIFICATION_ID = 9901;

    public static final String ACTION_START_TASK = "com.nextbridgedev.app.ACTION_START_TASK";
    public static final String ACTION_PAUSE_TASK = "com.nextbridgedev.app.ACTION_PAUSE_TASK";
    public static final String ACTION_RESUME_TASK = "com.nextbridgedev.app.ACTION_RESUME_TASK";
    public static final String ACTION_CANCEL_TASK = "com.nextbridgedev.app.ACTION_CANCEL_TASK";
    public static final String ACTION_STOP_SERVICE = "com.nextbridgedev.app.ACTION_STOP_SERVICE";

    public static final String ACTION_PAUSE_CLICK = "com.nextbridgedev.app.ACTION_PAUSE_CLICK";
    public static final String ACTION_RESUME_CLICK = "com.nextbridgedev.app.ACTION_RESUME_CLICK";
    public static final String ACTION_CANCEL_CLICK = "com.nextbridgedev.app.ACTION_CANCEL_CLICK";

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SUBJECT = "subjectName";
    public static final String EXTRA_FOLDER = "folderPath";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_THUMBNAIL = "thumbnail";
    public static final String EXTRA_QUALITY = "quality";
    public static final String EXTRA_PLAYLIST_URL = "playlistUrl";
    public static final String EXTRA_TOTAL_BYTES = "totalBytes";

    private static DownloadForegroundService instance;
    public static DownloadForegroundService getInstance() { return instance; }

    private NotificationManager notificationManager;
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final ExecutorService taskQueue = Executors.newSingleThreadExecutor();

    public static class Task {
        public String id;
        public String title;
        public String subjectName;
        public String folderPath;
        public long duration;
        public String thumbnail;
        public String quality;
        public String playlistUrl;
        public long totalBytes;
        public AtomicLong downloadedBytes = new AtomicLong(0);
        public AtomicInteger completedSegments = new AtomicInteger(0);
        public int totalSegments = 0;
        public int percent = 0;
        public long speed = 0; // bytes per sec
        public long eta = 0; // seconds
        public volatile String status = "downloading"; // downloading | paused | completed | cancelled | error
        public AtomicBoolean isCancelled = new AtomicBoolean(false);
        public AtomicBoolean isPaused = new AtomicBoolean(false);

        public long lastBytesChecked = 0;
        public long lastTimeChecked = System.currentTimeMillis();
    }

    private final BroadcastReceiver actionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String downloadId = intent.getStringExtra(EXTRA_ID);
            if (ACTION_PAUSE_CLICK.equals(action)) {
                pauseTask(downloadId);
            } else if (ACTION_RESUME_CLICK.equals(action)) {
                resumeTask(downloadId);
            } else if (ACTION_CANCEL_CLICK.equals(action)) {
                cancelTask(downloadId);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PAUSE_CLICK);
        filter.addAction(ACTION_RESUME_CLICK);
        filter.addAction(ACTION_CANCEL_CLICK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(actionReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_TASK.equals(action)) {
                String id = intent.getStringExtra(EXTRA_ID);
                if (id != null && !id.isEmpty() && !tasks.containsKey(id)) {
                    Task task = new Task();
                    task.id = id;
                    task.title = intent.getStringExtra(EXTRA_TITLE);
                    task.subjectName = intent.getStringExtra(EXTRA_SUBJECT);
                    task.folderPath = intent.getStringExtra(EXTRA_FOLDER);
                    task.duration = intent.getLongExtra(EXTRA_DURATION, 0);
                    task.thumbnail = intent.getStringExtra(EXTRA_THUMBNAIL);
                    task.quality = intent.getStringExtra(EXTRA_QUALITY);
                    task.playlistUrl = intent.getStringExtra(EXTRA_PLAYLIST_URL);
                    task.totalBytes = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0);

                    tasks.put(id, task);
                    startForeground(NOTIFICATION_ID, buildNotification());
                    taskQueue.submit(() -> executeDownload(task));
                }
            } else if (ACTION_PAUSE_TASK.equals(action)) {
                String id = intent.getStringExtra(EXTRA_ID);
                pauseTask(id);
            } else if (ACTION_RESUME_TASK.equals(action)) {
                String id = intent.getStringExtra(EXTRA_ID);
                resumeTask(id);
            } else if (ACTION_CANCEL_TASK.equals(action)) {
                String id = intent.getStringExtra(EXTRA_ID);
                cancelTask(id);
            } else if (ACTION_STOP_SERVICE.equals(action)) {
                stopForeground(true);
                stopSelf();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // App was swiped away from Recent Apps!
        // Keep downloading in background if active tasks remain
        if (tasks.isEmpty()) {
            stopForeground(true);
            stopSelf();
        }
    }

    public void pauseTask(String id) {
        Task t = tasks.get(id);
        if (t != null) {
            t.isPaused.set(true);
            t.status = "paused";
            updateNotification();
            DownloadServicePlugin.notifyAction("pause", id);
        }
    }

    public void resumeTask(String id) {
        Task t = tasks.get(id);
        if (t != null) {
            t.isPaused.set(false);
            t.status = "downloading";
            t.lastTimeChecked = System.currentTimeMillis();
            t.lastBytesChecked = t.downloadedBytes.get();
            updateNotification();
            DownloadServicePlugin.notifyAction("resume", id);
        }
    }

    public void cancelTask(String id) {
        Task t = tasks.get(id);
        if (t != null) {
            t.isCancelled.set(true);
            t.status = "cancelled";
            tasks.remove(id);
            File taskDir = new File(getFilesDir(), "downloads/" + id);
            deleteRecursive(taskDir);
            DownloadServicePlugin.notifyAction("cancel", id);

            if (tasks.isEmpty()) {
                stopForeground(true);
                stopSelf();
            } else {
                updateNotification();
            }
        }
    }

    public JSONArray getActiveTasksJson() {
        JSONArray arr = new JSONArray();
        for (Task t : tasks.values()) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", t.id);
                obj.put("title", t.title);
                obj.put("subjectName", t.subjectName);
                obj.put("quality", t.quality);
                obj.put("percent", t.percent);
                long downloaded = t.downloadedBytes.get();
                long total = Math.max(downloaded, t.totalBytes);
                obj.put("downloadedBytes", downloaded);
                obj.put("totalBytes", total);
                obj.put("formattedDownloaded", formatBytes(downloaded));
                obj.put("formattedTotal", formatBytes(total));
                obj.put("speed", formatSpeed(t.speed));
                obj.put("eta", formatEta(t.eta));
                obj.put("status", t.status);
                arr.put(obj);
            } catch (Exception ignored) {}
        }
        return arr;
    }

    private void executeDownload(Task task) {
        try {
            File taskDir = new File(getFilesDir(), "downloads/" + task.id);
            if (!taskDir.exists()) taskDir.mkdirs();

            // 1. Fetch variant playlist
            URL url = new URL(task.playlistUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String line;
            List<String> rawLines = new ArrayList<>();
            List<String> segUrls = new ArrayList<>();

            String baseUrl = task.playlistUrl;
            int lastSlash = baseUrl.lastIndexOf('/');
            String basePrefix = (lastSlash != -1) ? baseUrl.substring(0, lastSlash + 1) : "";

            while ((line = reader.readLine()) != null) {
                rawLines.add(line);
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    String fullSegUrl;
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        fullSegUrl = trimmed;
                    } else if (trimmed.startsWith("/")) {
                        URL baseUri = new URL(task.playlistUrl);
                        fullSegUrl = baseUri.getProtocol() + "://" + baseUri.getHost() + trimmed;
                    } else {
                        fullSegUrl = basePrefix + trimmed;
                    }
                    segUrls.add(fullSegUrl);
                }
            }
            reader.close();
            conn.disconnect();

            task.totalSegments = segUrls.size();

            // Generate relative index.m3u8 for offline playback
            int segIndex = 0;
            StringBuilder relativePlaylist = new StringBuilder();
            for (String l : rawLines) {
                String trimmed = l.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    relativePlaylist.append("segment_").append(segIndex).append(".ts\n");
                    segIndex++;
                } else {
                    relativePlaylist.append(l).append("\n");
                }
            }

            File m3u8File = new File(taskDir, "index.m3u8");
            FileOutputStream m3u8Out = new FileOutputStream(m3u8File);
            m3u8Out.write(relativePlaylist.toString().getBytes(StandardCharsets.UTF_8));
            m3u8Out.close();

            // 2. Download segments with 8 worker threads
            ExecutorService pool = Executors.newFixedThreadPool(8);
            AtomicInteger nextIndex = new AtomicInteger(0);
            List<Future<?>> futures = new ArrayList<>();

            for (int w = 0; w < 8; w++) {
                futures.add(pool.submit(() -> {
                    byte[] buffer = new byte[16384];
                    while (true) {
                        if (task.isCancelled.get()) break;

                        while (task.isPaused.get() && !task.isCancelled.get()) {
                            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                        }
                        if (task.isCancelled.get()) break;

                        int idx = nextIndex.getAndIncrement();
                        if (idx >= segUrls.size()) break;

                        String sUrl = segUrls.get(idx);
                        File segFile = new File(taskDir, "segment_" + idx + ".ts");

                        if (segFile.exists() && segFile.length() > 0) {
                            task.downloadedBytes.addAndGet(segFile.length());
                            task.completedSegments.incrementAndGet();
                            continue;
                        }

                        boolean success = false;
                        for (int retry = 0; retry < 3 && !success && !task.isCancelled.get(); retry++) {
                            try {
                                URL sUri = new URL(sUrl);
                                HttpURLConnection sConn = (HttpURLConnection) sUri.openConnection();
                                sConn.setConnectTimeout(15000);
                                sConn.setReadTimeout(20000);
                                sConn.connect();

                                if (sConn.getResponseCode() == 200) {
                                    InputStream in = sConn.getInputStream();
                                    File tempFile = new File(taskDir, "segment_" + idx + ".ts.tmp");
                                    FileOutputStream out = new FileOutputStream(tempFile);
                                    int bytesRead;
                                    while ((bytesRead = in.read(buffer)) != -1) {
                                        if (task.isCancelled.get()) {
                                            out.close();
                                            tempFile.delete();
                                            break;
                                        }
                                        out.write(buffer, 0, bytesRead);
                                        task.downloadedBytes.addAndGet(bytesRead);
                                    }
                                    out.close();
                                    in.close();
                                    sConn.disconnect();

                                    if (!task.isCancelled.get()) {
                                        tempFile.renameTo(segFile);
                                        task.completedSegments.incrementAndGet();
                                        success = true;
                                    }
                                } else {
                                    sConn.disconnect();
                                }
                            } catch (Exception e) {
                                try { Thread.sleep(600); } catch (InterruptedException ignored) {}
                            }
                        }
                    }
                }));
            }

            // Monitor progress loop
            while (!task.isCancelled.get() && task.completedSegments.get() < task.totalSegments) {
                updateTaskMetrics(task);
                updateNotification();
                DownloadServicePlugin.notifyProgress(task);
                try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            }

            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception ignored) {}
            }
            pool.shutdown();

            if (task.isCancelled.get()) {
                deleteRecursive(taskDir);
                tasks.remove(task.id);
                updateNotification();
                return;
            }

            // Task Completed
            task.percent = 100;
            task.status = "completed";
            saveCompletedToRegistry(this, task);
            tasks.remove(task.id);

            DownloadServicePlugin.notifyCompleted(task);

            if (tasks.isEmpty()) {
                stopForeground(true);
                stopSelf();
            } else {
                updateNotification();
            }

        } catch (Exception e) {
            e.printStackTrace();
            task.status = "error";
            updateNotification();
        }
    }

    private void updateTaskMetrics(Task task) {
        int total = Math.max(1, task.totalSegments);
        int done = task.completedSegments.get();
        task.percent = Math.min(99, (int) Math.round(((double) done / total) * 100));

        long now = System.currentTimeMillis();
        long timeDiff = now - task.lastTimeChecked;
        if (timeDiff >= 1000) {
            long curBytes = task.downloadedBytes.get();
            long bytesDiff = curBytes - task.lastBytesChecked;
            task.speed = Math.max(0, (bytesDiff * 1000) / timeDiff);

            long totalEst = Math.max(curBytes, task.totalBytes);
            long remBytes = Math.max(0, totalEst - curBytes);
            task.eta = (task.speed > 0) ? (remBytes / task.speed) : 0;

            task.lastTimeChecked = now;
            task.lastBytesChecked = curBytes;
        }
    }

    private void updateNotification() {
        if (tasks.isEmpty()) {
            stopForeground(true);
            stopSelf();
            return;
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Task activeTask = null;
        for (Task t : tasks.values()) {
            activeTask = t;
            break;
        }

        String title = (activeTask != null) ? activeTask.title : "Downloading Lectures...";
        boolean isPaused = (activeTask != null && activeTask.isPaused.get());
        String activeId = (activeTask != null) ? activeTask.id : "";
        int progress = (activeTask != null) ? activeTask.percent : 0;

        String subtext;
        if (activeTask != null) {
            long cur = activeTask.downloadedBytes.get();
            long tot = Math.max(cur, activeTask.totalBytes);
            subtext = formatBytes(cur) + " / " + formatBytes(tot);
            if (isPaused) {
                subtext += " • Paused";
            } else if (activeTask.speed > 0) {
                subtext += " • " + formatSpeed(activeTask.speed) + " • " + formatEta(activeTask.eta);
            }
        } else {
            subtext = "Preparing download...";
        }

        Intent toggleIntent = new Intent(isPaused ? ACTION_RESUME_CLICK : ACTION_PAUSE_CLICK);
        toggleIntent.putExtra(EXTRA_ID, activeId);
        PendingIntent pendingToggle = PendingIntent.getBroadcast(
            this, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent cancelIntent = new Intent(ACTION_CANCEL_CLICK);
        cancelIntent.putExtra(EXTRA_ID, activeId);
        PendingIntent pendingCancel = PendingIntent.getBroadcast(
            this, 2, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(subtext)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(0xFFF59E0B)
            .setProgress(100, Math.max(0, Math.min(100, progress)), false)
            .addAction(
                isPaused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause,
                isPaused ? "Resume" : "Pause",
                pendingToggle
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                pendingCancel
            );

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "NextBridge Downloads",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active lecture download progress, speed, and controls");
            channel.enableVibration(false);
            channel.setShowBadge(false);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    public static synchronized void saveCompletedToRegistry(Context context, Task task) {
        try {
            File regFile = new File(context.getFilesDir(), "downloads/registry.json");
            JSONArray array = new JSONArray();
            if (regFile.exists()) {
                FileInputStream fis = new FileInputStream(regFile);
                byte[] data = new byte[(int) regFile.length()];
                fis.read(data);
                fis.close();
                String jsonStr = new String(data, StandardCharsets.UTF_8);
                if (!jsonStr.trim().isEmpty()) {
                    array = new JSONArray(jsonStr);
                }
            }

            JSONArray updated = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (!task.id.equals(obj.optString("id"))) {
                    updated.put(obj);
                }
            }

            JSONObject newRecord = new JSONObject();
            newRecord.put("id", task.id);
            newRecord.put("title", task.title);
            newRecord.put("subjectName", task.subjectName);
            newRecord.put("folderPath", task.folderPath);
            newRecord.put("duration", task.duration);
            newRecord.put("thumbnail", task.thumbnail);
            newRecord.put("quality", task.quality);
            newRecord.put("path", "downloads/" + task.id);
            long total = task.downloadedBytes.get() > 0 ? task.downloadedBytes.get() : task.totalBytes;
            newRecord.put("sizeBytes", total);
            newRecord.put("formattedSize", formatBytes(total));

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            newRecord.put("downloadedAt", sdf.format(new Date()));

            JSONArray finalArray = new JSONArray();
            finalArray.put(newRecord);
            for (int i = 0; i < updated.length(); i++) {
                finalArray.put(updated.get(i));
            }

            File dir = regFile.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();

            FileOutputStream fos = new FileOutputStream(regFile);
            fos.write(finalArray.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized JSONArray getCompletedFromRegistry(Context context) {
        try {
            File regFile = new File(context.getFilesDir(), "downloads/registry.json");
            if (regFile.exists()) {
                FileInputStream fis = new FileInputStream(regFile);
                byte[] data = new byte[(int) regFile.length()];
                fis.read(data);
                fis.close();
                String jsonStr = new String(data, StandardCharsets.UTF_8);
                if (!jsonStr.trim().isEmpty()) {
                    return new JSONArray(jsonStr);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new JSONArray();
    }

    public static synchronized void deleteDownloadFromRegistry(Context context, String downloadId) {
        try {
            File regFile = new File(context.getFilesDir(), "downloads/registry.json");
            if (regFile.exists()) {
                JSONArray array = getCompletedFromRegistry(context);
                JSONArray updated = new JSONArray();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    if (!downloadId.equals(obj.optString("id"))) {
                        updated.put(obj);
                    }
                }
                FileOutputStream fos = new FileOutputStream(regFile);
                fos.write(updated.toString().getBytes(StandardCharsets.UTF_8));
                fos.close();
            }

            File dir = new File(context.getFilesDir(), "downloads/" + downloadId);
            deleteRecursive(dir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory != null && fileOrDirectory.exists()) {
            if (fileOrDirectory.isDirectory()) {
                File[] children = fileOrDirectory.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            fileOrDirectory.delete();
        }
    }

    public static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public static String formatSpeed(long bytesPerSec) {
        if (bytesPerSec <= 0) return "0 B/s";
        return formatBytes(bytesPerSec) + "/s";
    }

    public static String formatEta(long seconds) {
        if (seconds <= 0) return "calculating...";
        if (seconds < 60) return seconds + "s remaining";
        long m = seconds / 60;
        long s = seconds % 60;
        if (m < 60) return m + "m " + s + "s remaining";
        long h = m / 60;
        return h + "h " + (m % 60) + "m remaining";
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(actionReceiver);
        } catch (Exception ignored) {}
        taskQueue.shutdownNow();
        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
