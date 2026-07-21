package com.jahangir.devlogcat;

import android.content.Context;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogCollector {

private static final Pattern LOG_PATTERN =
        Pattern.compile("^(\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d+)\\s+([ADEIW])/(.*?)\\((\\d+)\\):\\s+(.*)");

    private static volatile boolean mInitialized;
    private static Context mContext;

    public static void initialize(Context context) {
        if (mInitialized) return;
        mInitialized = true;
        mContext = context.getApplicationContext();
        start();
    }

    private static void start() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                clear();

                Process process = Runtime.getRuntime().exec(
                        new String[]{"logcat", "-v", "time"}
                );

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

                String line;
                while ((line = reader.readLine()) != null) {
                    parseAndBroadcast(line);
                }

            } catch (Exception e) {
                send("ERROR", "LOGGER", "Logger failure: " + e);
            }
        });
    }

    private static void clear() throws IOException {
        Runtime.getRuntime().exec("logcat -c");
    }


    private static void parseAndBroadcast(String line) {
    
        Matcher m = LOG_PATTERN.matcher(line);
    
        if (m.matches()) {
            String timeStr = m.group(1);
            String type = m.group(2);
            String tag = m.group(3);
            String message = m.group(5);
    
            String level = "DEBUG";
            switch (type) {
                case "E": level = "ERROR"; break;
                case "W": level = "WARN"; break;
                case "I": level = "INFO"; break;
                case "D": level = "DEBUG"; break;
                case "A": level = "ASSERT"; break;
            }
    
            send(level, tag, message);
        }
    }

    private static void send(String level, String tag, String message) {
        String appName = mContext.getString(mContext.getApplicationInfo().labelRes);
        
        Intent intent = new Intent("DEV_LOGGER");
        intent.putExtra("level", level);
        intent.putExtra("tag", tag);
        intent.putExtra("msg", message);
        intent.putExtra("pkg", mContext.getPackageName());
        intent.putExtra("time", System.currentTimeMillis());
        intent.putExtra("pid", android.os.Process.myPid());
        intent.putExtra("tid", android.os.Process.myTid());
        intent.putExtra("app_name", appName);
            
        intent.setPackage("com.jahangir.logviewer");
        mContext.sendBroadcast(intent);
    }
}