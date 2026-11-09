package com.jahangir.devlogcat;

import android.content.Context;

/**
 * Configuration entry point for DevLogCat.
 *
 * <p>The library no longer provides a custom logging API. Every call the host app makes to
 * {@link android.util.Log} is captured automatically by {@link LogCollector} and forwarded to
 * the viewer app. Use this class only to initialise manually or to change the viewer package.
 */
public final class DevLog {

    private static volatile String viewerPackage = "com.jahangir";

    private DevLog() {
    }

    /**
     * Optional manual initialisation. The Jetpack Startup initializer already calls this.
     */
    public static void init(Context context) {
        LogCollector.initialize(context);
    }

    /**
     * Set the package name of the viewer app that receives the DEV_LOGGER broadcasts.
     */
    public static void setViewerPackage(String packageName) {
        if (packageName != null && !packageName.trim().isEmpty()) {
            viewerPackage = packageName.trim();
        }
    }

    public static String getViewerPackage() {
        return viewerPackage;
    }

    public static boolean isInitialized() {
        return LogCollector.isInitialized();
    }
}
