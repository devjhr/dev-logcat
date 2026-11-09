package com.jahangir.devlogcat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captures the host app's own logcat output (same UID only, no READ_LOGS needed)
 * and forwards it to the viewer app in small batches over the DEV_LOGGER broadcast.
 */
public final class LogCollector {

    public static final String ACTION = "DEV_LOGGER";
    public static final String EXTRA_LOGS = "logs";

    public static final String KEY_LEVEL = "level";
    public static final String KEY_TAG = "tag";
    public static final String KEY_MSG = "msg";
    public static final String KEY_PKG = "pkg";
    public static final String KEY_TIME = "time";
    public static final String KEY_PID = "pid";
    public static final String KEY_TID = "tid";
    public static final String KEY_APP_NAME = "app_name";

    private static final int MAX_BATCH = 100;
    private static final int MAX_MESSAGE_CHARS = 32000;
    private static final int MAX_BATCH_CHARS = 120000;
    private static final long FLUSH_INTERVAL_MS = 200L;
    private static final long MULTILINE_IDLE_MS = 250L;
    private static final long RECONNECT_DELAY_MS = 1000L;

    private static final Pattern THREADTIME = Pattern.compile(
            "^(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})\\s+" +
                    "(\\d+)\\s+(\\d+)\\s+([VDIWEFA])\\s+(.*?)\\s*:\\s?(.*)$");

    private static final Object LOCK = new Object();
    private static final List<Bundle> batch = new ArrayList<>();
    private static final List<String> skipTags = new ArrayList<>();

    private static volatile boolean initialized;
    private static volatile boolean running;
    private static Context appContext;
    private static String appName;
    private static Pending pending;
    private static int batchChars;
    private static Process logcatProcess;
    private static ScheduledExecutorService scheduler;

    private LogCollector() {
    }

    public static void initialize(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            appContext = context.getApplicationContext();
            appName = loadAppName(appContext);
            skipTags.add("DevLogCat");
            skipTags.add("logviewerReceiver");
            initialized = true;
            running = true;

            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "DevLogCat-sender");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleWithFixedDelay(
                    LogCollector::flushTick,
                    FLUSH_INTERVAL_MS,
                    FLUSH_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);

            Thread reader = new Thread(LogCollector::readLoop, "DevLogCat-collector");
            reader.setDaemon(true);
            reader.start();
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void stop() {
        running = false;
        synchronized (LOCK) {
            if (logcatProcess != null) {
                logcatProcess.destroy();
                logcatProcess = null;
            }
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        initialized = false;
    }

    private static void readLoop() {
        while (running) {
            Process process = null;
            try {
                process = new ProcessBuilder(
                        "logcat",
                        "-v", "threadtime",
                        "-T", "1",
                        "--pid=" + android.os.Process.myPid())
                        .redirectErrorStream(true)
                        .start();
                synchronized (LOCK) {
                    logcatProcess = process;
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                String line;
                while (running && (line = reader.readLine()) != null) {
                    handleLine(line);
                }
            } catch (Exception ignored) {
                // logcat is unavailable or was killed; retry below
            } finally {
                if (process != null) {
                    process.destroy();
                }
                synchronized (LOCK) {
                    if (logcatProcess == process) {
                        logcatProcess = null;
                    }
                }
            }

            if (running) {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void handleLine(String line) {
        if (line == null) {
            return;
        }
        if (line.startsWith("---------")) {
            flushPending();
            return;
        }

        Matcher matcher = THREADTIME.matcher(line);
        if (matcher.matches()) {
            flushPending();

            String tag = matcher.group(10).trim();
            if (shouldSkip(tag)) {
                return;
            }

            Pending entry = new Pending();
            entry.level = levelOf(matcher.group(9).charAt(0));
            entry.tag = tag.isEmpty() ? "Unknown" : tag;
            entry.msg = matcher.group(11);
            entry.pid = parseInt(matcher.group(7));
            entry.tid = parseInt(matcher.group(8));
            entry.time = parseTime(
                    parseInt(matcher.group(1)),
                    parseInt(matcher.group(2)),
                    parseInt(matcher.group(3)),
                    parseInt(matcher.group(4)),
                    parseInt(matcher.group(5)),
                    parseInt(matcher.group(6)));
            entry.lineAt = SystemClock.elapsedRealtime();

            synchronized (LOCK) {
                pending = entry;
            }
        } else {
            // Continuation of a multi-line log (e.g. a stack trace).
            synchronized (LOCK) {
                if (pending != null) {
                    pending.msg = pending.msg + "\n" + line;
                    pending.lineAt = SystemClock.elapsedRealtime();
                }
            }
        }
    }

    private static boolean shouldSkip(String tag) {
        if (tag.isEmpty()) {
            return false;
        }
        for (String skip : skipTags) {
            if (tag.startsWith(skip)) {
                return true;
            }
        }
        return false;
    }

    private static void flushTick() {
        synchronized (LOCK) {
            if (pending != null
                    && SystemClock.elapsedRealtime() - pending.lineAt >= MULTILINE_IDLE_MS) {
                movePendingToBatchLocked();
            }
            if (!batch.isEmpty()) {
                sendBatchLocked();
            }
        }
    }

    private static void flushPending() {
        synchronized (LOCK) {
            movePendingToBatchLocked();
            if (batch.size() >= MAX_BATCH || batchChars >= MAX_BATCH_CHARS) {
                sendBatchLocked();
            }
        }
    }

    private static void movePendingToBatchLocked() {
        if (pending == null) {
            return;
        }
        Pending entry = pending;
        pending = null;

        Bundle bundle = new Bundle();
        bundle.putString(KEY_LEVEL, entry.level);
        bundle.putString(KEY_TAG, entry.tag);
        bundle.putString(KEY_MSG, truncate(entry.msg));
        bundle.putString(KEY_PKG, appContext.getPackageName());
        bundle.putLong(KEY_TIME, entry.time);
        bundle.putInt(KEY_PID, entry.pid);
        bundle.putInt(KEY_TID, entry.tid);
        bundle.putString(KEY_APP_NAME, appName);
        batch.add(bundle);
        batchChars += entry.tag.length() + entry.msg.length();

        if (batch.size() >= MAX_BATCH || batchChars >= MAX_BATCH_CHARS) {
            sendBatchLocked();
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        if (message.length() <= MAX_MESSAGE_CHARS) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_CHARS) + "\n... [truncated]";
    }

    private static void sendBatchLocked() {
        if (batch.isEmpty() || appContext == null) {
            return;
        }
        ArrayList<Bundle> payload = new ArrayList<>(batch);
        batch.clear();
        batchChars = 0;

        Intent intent = new Intent(ACTION);
        intent.putParcelableArrayListExtra(EXTRA_LOGS, payload);
        intent.setPackage(DevLog.getViewerPackage());
        try {
            appContext.sendBroadcast(intent);
        } catch (Exception ignored) {
            // viewer app missing or broadcast rejected; drop this batch
        }
    }

    private static String levelOf(char type) {
        switch (type) {
            case 'V':
                return "VERBOSE";
            case 'D':
                return "DEBUG";
            case 'I':
                return "INFO";
            case 'W':
                return "WARN";
            case 'E':
                return "ERROR";
            case 'F':
                return "ERROR";
            case 'A':
                return "ASSERT";
            default:
                return "DEBUG";
        }
    }

    private static long parseTime(int month, int day, int hour, int minute, int second, int millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, second);
        calendar.set(Calendar.MILLISECOND, millis);

        long time = calendar.getTimeInMillis();
        if (time > System.currentTimeMillis() + 86400000L) {
            // Log written in December while the device clock is in January.
            time -= 365L * 24L * 60L * 60L * 1000L;
        }
        return time;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String loadAppName(Context context) {
        try {
            CharSequence label = context.getApplicationInfo()
                    .loadLabel(context.getPackageManager());
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        } catch (Exception ignored) {
            // fall through to package name
        }
        return context.getPackageName();
    }

    private static final class Pending {
        String level;
        String tag;
        String msg;
        long time;
        int pid;
        int tid;
        long lineAt;
    }
}
