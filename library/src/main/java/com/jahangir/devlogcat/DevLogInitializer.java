package com.jahangir.devlogcat;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.startup.Initializer;
import java.util.Collections;
import java.util.List;

/**
 * Jetpack Startup Initializer for auto-initializing DevLog
 * This class is automatically called when app starts
 */
public class DevLogInitializer implements Initializer<Void> {
    
    @NonNull
    @Override
    public Void create(@NonNull Context context) {
        // Auto-initialize DevLog when app starts
        LogCollector.initialize(context);
        Log.initialize(context);
        return null;
    }
    
    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        // No dependencies on other initializers
        return Collections.emptyList();
    }
}