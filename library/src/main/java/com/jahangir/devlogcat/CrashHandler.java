package com.jahangir.devlogcat;

import android.content.Context;
import android.content.Intent;
import android.os.Process;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final Context context;

    public CrashHandler(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable e) {

        try {
            String stackTrace = android.util.Log.getStackTraceString(e);

            String appName = context.getString(context.getApplicationInfo().labelRes);

            Intent intent = new Intent("DEV_LOGGER");
            intent.setPackage("com.jahangir.logviewer");

            intent.putExtra("level", "CRASH");
            intent.putExtra("tag", "UNCAUGHT_EXCEPTION");
            intent.putExtra("msg", stackTrace);
            intent.putExtra("pkg", context.getPackageName());
            intent.putExtra("time", System.currentTimeMillis());
            intent.putExtra("pid", Process.myPid());
            intent.putExtra("tid", Process.myTid());
            intent.putExtra("app_name", appName);
            intent.putExtra("thread", thread.getName());

            context.sendBroadcast(intent);

            android.util.Log.e("DevLogCat", "Crash captured and broadcasted");

            // ছোট delay দেই যাতে broadcast send হওয়ার সময় পায়
            Thread.sleep(300);

        } catch (Exception ex) {
            android.util.Log.e("DevLogCat", "CrashHandler failed", ex);
        }

        // Default system crash handler call (App still crashes normally)
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, e);
        } else {
            Process.killProcess(Process.myPid());
            System.exit(1);
        }
    }
}