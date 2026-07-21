package com.jahangir.devlogcat;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;

public class Log {
    
    private static Context appContext;
    private static String viewerPackage = "com.jahangir.logviewer";
    private static volatile boolean initialized = false;
    private static final Object lock = new Object();
    
    /**
     * Internal initialization method
     * Called automatically by Startup library
     */
    static void initialize(@NonNull Context context) {
        synchronized (lock) {
            if (initialized) {
                return;
            }
            
            if (context == null) {
                throw new IllegalArgumentException("Context cannot be null");
            }
            
            appContext = context.getApplicationContext();
            initialized = true;
            
            android.util.Log.d("DevLogCat", "DevLog initialized for package: " + appContext.getPackageName());
        }
    }
    
    /**
     * Public init method (optional - for manual initialization)
     */
    public static void init(@NonNull Context context) {
        initialize(context);
    }
    
    /**
     * Ensure DevLog is initialized before use
     */
    private static void ensureInitialized() {
        if (!initialized) {
            // If not initialized, try to get context from Application
            try {
                // Try to get Application context via reflection (fallback)
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
                Context context = (Context) activityThreadClass.getMethod("getApplication").invoke(activityThread);
                
                if (context != null) {
                    initialize(context);
                    android.util.Log.d("DevLogCat", "Auto-initialized via reflection fallback");
                }
            } catch (Exception e) {
                android.util.Log.w("DevLogCat", "Could not auto-initialize: " + e.getMessage());
            }
        }
        
        if (!initialized) {
            android.util.Log.w("DevLogCat", 
                "DevLog not initialized. Add initialization provider in AndroidManifest.xml " +
                "or call DevLog.init(context) in your Application class.");
        }
    }
    
    /**
     * Main send method (context-free version)
     */
    public static void send(String level, String tag, String message) {
	if (message == null) message = "null";
        ensureInitialized();
        
        // Always log to system logcat
        logToSystem(level, tag, message);
        
        // Send to viewer app if initialized
        if (initialized) {
            try {
                sendSingleBroadcast(level, tag, message);
            } catch (Exception e) {
                android.util.Log.e("DevLogCat", "Failed to send to viewer", e);
            }
        }
    }
    
    /**
     * Send method with context (backward compatibility)
     */
    public static void send(Context context, String level, String tag, String message) {
        // Initialize if not already done
        if (context != null && !initialized) {
            init(context);
        }
        
        // Use context-free version
        send(level, tag, message);
    }
    
    private static void logToSystem(String level, String tag, String message) {
        switch (level.toUpperCase()) {
            case "VERBOSE":
                android.util.Log.v(tag, message);
                break;
            case "DEBUG":
                android.util.Log.d(tag, message);
                break;
            case "INFO":
                android.util.Log.i(tag, message);
                break;
            case "WARN":
                android.util.Log.w(tag, message);
                break;
            case "ERROR":
                android.util.Log.e(tag, message);
                break;
            default:
                android.util.Log.d(tag, message);
        }
    }
    
    private static void sendSingleBroadcast(String level, String tag, String message) {
        String appName = appContext.getString(appContext.getApplicationInfo().labelRes);
        try {
            Intent intent = new Intent("DEV_LOGGER");
            intent.putExtra("level", level);
            intent.putExtra("tag", tag);
            intent.putExtra("msg", message);
            intent.putExtra("pkg", appContext.getPackageName());
            intent.putExtra("time", System.currentTimeMillis());
            intent.putExtra("pid", android.os.Process.myPid());
            intent.putExtra("tid", android.os.Process.myTid());
            intent.putExtra("app_name", appName);
            intent.setPackage(viewerPackage);
            appContext.sendBroadcast(intent);
            // Log success
            String truncatedMsg = message.length() > 30 ? message.substring(0, 30) + "..." : message;
            android.util.Log.d("DevLogCat", "Log sent to viewer: [" + level + "] " + tag + " - " + truncatedMsg);
        } catch (Exception e) {
            android.util.Log.e("DevLogCat", "Failed to send broadcast", e);
        }
    }
    
    // ================= CONTEXT-FREE METHODS =================
    
    public static void v(String tag, String message) {
        send("VERBOSE", tag, message);
    }
    
    public static void d(String tag, String message) {
        send("DEBUG", tag, message);
    }
    
    public static void i(String tag, String message) {
        send("INFO", tag, message);
    }
    
    public static void w(String tag, String message) {
        send("WARN", tag, message);
    }
    
    public static void e(String tag, String message) {
        send("ERROR", tag, message);
    }
    
    // ================= CONTEXT METHODS (Backward Compatible) =================
    
    public static void d(Context context, String tag, String message) {
        send(context, "DEBUG", tag, message);
    }
    
    public static void i(Context context, String tag, String message) {
        send(context, "INFO", tag, message);
    }
    
    public static void w(Context context, String tag, String message) {
        send(context, "WARN", tag, message);
    }
    
    public static void e(Context context, String tag, String message) {
        send(context, "ERROR", tag, message);
    }
    
    // ================= CONFIGURATION METHODS =================
    
    /**
     * Set custom viewer app package
     */
    public static void setViewerPackage(String packageName) {
        viewerPackage = packageName;
        android.util.Log.d("DevLogCat", "Viewer package set to: " + packageName);
    }
    
    /**
     * Get current viewer package
     */
    public static String getViewerPackage() {
        return viewerPackage;
    }
    
    /**
     * Check if DevLog is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Get application context (for advanced use)
     */
    public static Context getAppContext() {
        ensureInitialized();
        return appContext;
    }
}
