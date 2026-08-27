package com.example.shop;

import android.net.Uri;
import android.os.Bundle;

import com.google.androidbrowserhelper.trusted.LauncherActivity;

/**
 * MainActivity extending android-browser-helper's LauncherActivity.
 * Opens the Angular website as a Trusted Web Activity (TWA) using Chrome's browser engine.
 * Renders the application in a standalone fullscreen view (no Chrome address bar, search bar, or browser toolbar)
 * while preserving full Web APIs, including Web Bluetooth support.
 */
public class MainActivity extends LauncherActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected Uri getLaunchingUrl() {
        // Reads configurable URL from res/values/strings.xml
        return Uri.parse(getString(R.string.launch_url));
    }
}