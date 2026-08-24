package com.example.shop;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

public class MainActivity extends AppCompatActivity {

    String websiteURL = "http://billingt.netlify.app/tejas";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // We will just use a simple layout for the launcher or launch immediately
        setContentView(R.layout.activity_main);
        
        View webView = findViewById(R.id.webView);
        if (webView != null) {
            webView.setVisibility(View.GONE);
        }

        if (!isInternetAvailable()) {
            new AlertDialog.Builder(this)
                    .setTitle("No internet connection available")
                    .setMessage("Please check your Mobile data or Wifi network.")
                    .setPositiveButton("Ok", (dialog, which) -> finish())
                    .show();
        } else {
            openCustomTab();
        }
    }

    private void openCustomTab() {
        try {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setShowTitle(true);
            
            CustomTabsIntent customTabsIntent = builder.build();
            // Try to force Chrome if installed (Chrome supports Web Bluetooth)
            customTabsIntent.intent.setPackage("com.android.chrome");
            // Add flags so it behaves like a separate task or top-level window
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            customTabsIntent.launchUrl(this, Uri.parse(websiteURL));
            
            // Finish MainActivity so the user doesn't return to an empty screen
            finish();
        } catch (Exception e) {
            // Fallback if Chrome is not installed
            try {
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                CustomTabsIntent customTabsIntent = builder.build();
                customTabsIntent.launchUrl(this, Uri.parse(websiteURL));
                finish();
            } catch (Exception ex) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(websiteURL));
                    startActivity(intent);
                    finish();
                } catch (Exception ex2) {
                    Toast.makeText(this, "Could not open browser. Please install Chrome.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }
}