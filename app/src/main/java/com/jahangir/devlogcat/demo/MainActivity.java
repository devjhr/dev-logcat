package com.jahangir.devlogcat.demo;

import android.os.Bundle;
//import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.TextView;
import com.jahangir.devlogcat.Log;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String boom = null;
        boom.length();
        setContentView(R.layout.activity_main);
        
        
        Log.d(TAG, "onCreate: Activity created");
        
        // Bundle check
        if (savedInstanceState != null) {
            Log.i(TAG, "onCreate: Restoring from saved state");
        } else {
            Log.i(TAG, "onCreate: Fresh start");
        }
        
        new android.os.Handler().postDelayed(() -> {
            String test = null;
            test.length(); //  NullPointerException
        }, 3000);
        
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: Activity becoming visible");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Activity ready for interaction");
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: Activity partially visible");
        super.onPause();
    }
    
    @Override
    protected void onStop() {
        Log.d(TAG, "onStop: Activity no longer visible");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: Activity being destroyed");
        super.onDestroy();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart: Activity restarting");
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState: Saving state");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(TAG, "onRestoreInstanceState: Restoring state");
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed: Back button pressed");
        super.onBackPressed();
    }
}
