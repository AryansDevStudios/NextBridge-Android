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

public class DownloadForegroundService extends Service {
    public static final String CHANNEL_ID = "nextbridge_downloads_channel";
    public static final int NOTIFICATION_ID = 9901;

    public static final String ACTION_START = "com.nextbridgedev.app.ACTION_START_DOWNLOAD";
    public static final String ACTION_UPDATE = "com.nextbridgedev.app.ACTION_UPDATE_DOWNLOAD";
    public static final String ACTION_STOP = "com.nextbridgedev.app.ACTION_STOP_DOWNLOAD";

    public static final String ACTION_PAUSE_CLICK = "com.nextbridgedev.app.ACTION_PAUSE_CLICK";
    public static final String ACTION_RESUME_CLICK = "com.nextbridgedev.app.ACTION_RESUME_CLICK";
    public static final String ACTION_CANCEL_CLICK = "com.nextbridgedev.app.ACTION_CANCEL_CLICK";

    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_SUBTEXT = "extra_subtext";
    public static final String EXTRA_PROGRESS = "extra_progress";
    public static final String EXTRA_IS_PAUSED = "extra_is_paused";
    public static final String EXTRA_DOWNLOAD_ID = "extra_download_id";

    private NotificationManager notificationManager;
    private String currentTitle = "Downloading Lectures...";
    private String currentSubtext = "Preparing download...";
    private int currentProgress = 0;
    private boolean isPaused = false;
    private String currentDownloadId = "";

    private final BroadcastReceiver actionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
            if (ACTION_PAUSE_CLICK.equals(action)) {
                DownloadServicePlugin.notifyAction("pause", downloadId);
            } else if (ACTION_RESUME_CLICK.equals(action)) {
                DownloadServicePlugin.notifyAction("resume", downloadId);
            } else if (ACTION_CANCEL_CLICK.equals(action)) {
                DownloadServicePlugin.notifyAction("cancel", downloadId);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
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
            if (ACTION_START.equals(action) || ACTION_UPDATE.equals(action)) {
                if (intent.hasExtra(EXTRA_TITLE)) currentTitle = intent.getStringExtra(EXTRA_TITLE);
                if (intent.hasExtra(EXTRA_SUBTEXT)) currentSubtext = intent.getStringExtra(EXTRA_SUBTEXT);
                if (intent.hasExtra(EXTRA_PROGRESS)) currentProgress = intent.getIntExtra(EXTRA_PROGRESS, 0);
                if (intent.hasExtra(EXTRA_IS_PAUSED)) isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false);
                if (intent.hasExtra(EXTRA_DOWNLOAD_ID)) currentDownloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID);

                Notification notification = buildNotification();
                startForeground(NOTIFICATION_ID, notification);
            } else if (ACTION_STOP.equals(action)) {
                stopForeground(true);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification() {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent toggleIntent = new Intent(isPaused ? ACTION_RESUME_CLICK : ACTION_PAUSE_CLICK);
        toggleIntent.putExtra(EXTRA_DOWNLOAD_ID, currentDownloadId);
        PendingIntent pendingToggle = PendingIntent.getBroadcast(
            this, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent cancelIntent = new Intent(ACTION_CANCEL_CLICK);
        cancelIntent.putExtra(EXTRA_DOWNLOAD_ID, currentDownloadId);
        PendingIntent pendingCancel = PendingIntent.getBroadcast(
            this, 2, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(currentTitle)
            .setContentText(currentSubtext)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, Math.max(0, Math.min(100, currentProgress)), false)
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

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(actionReceiver);
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
