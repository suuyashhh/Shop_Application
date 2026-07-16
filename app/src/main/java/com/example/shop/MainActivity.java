package com.example.shop;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    String websiteURL = "https://suyashpatil02034.netlify.app/shop/dashboard";

    private WebView webview;
    private boolean isWebPageScrolled = false;
    private SwipeRefreshLayout mySwipeRefreshLayout;

    private ValueCallback<Uri[]> filePathCallback;

    private String pendingDownloadUrl;
    private String pendingDownloadUserAgent;
    private String pendingDownloadContentDisposition;
    private String pendingDownloadMimeType;

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (filePathCallback == null) return;

                Uri[] results = null;

                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    if (result.getData().getClipData() != null) {
                        int count = result.getData().getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = result.getData().getClipData().getItemAt(i).getUri();
                        }
                    } else {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            results = new Uri[]{uri};
                        }
                    }
                }

                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            });

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (fineLocationGranted != null && fineLocationGranted) {
                    // Precision location access granted.
                } else if (coarseLocationGranted != null && coarseLocationGranted) {
                    // Only approximate location access granted.
                } else {
                    // No location access granted.
                    Toast.makeText(this, "Location permission denied. Map features may not work.", Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String> legacyWritePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Toast.makeText(this, "Storage permission denied. Can't download.", Toast.LENGTH_LONG).show();
                    clearPendingDownload();
                    return;
                }
                if (pendingDownloadUrl != null) {
                    startDownload(
                            pendingDownloadUrl,
                            pendingDownloadUserAgent,
                            pendingDownloadContentDisposition,
                            pendingDownloadMimeType
                    );
                }
                clearPendingDownload();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mySwipeRefreshLayout = findViewById(R.id.main);
        webview = findViewById(R.id.webView);

        // ✅ Only enable SwipeRefreshLayout when WebView is at the top to prevent scrolling conflicts
        mySwipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> {
            return isWebPageScrolled || (webview != null && webview.canScrollVertically(-1));
        });

        // ✅ Keep content below the phone status bar (safe area)
        final int initialPaddingLeft = mySwipeRefreshLayout.getPaddingLeft();
        final int initialPaddingTop = mySwipeRefreshLayout.getPaddingTop();
        final int initialPaddingRight = mySwipeRefreshLayout.getPaddingRight();
        final int initialPaddingBottom = mySwipeRefreshLayout.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(mySwipeRefreshLayout, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(initialPaddingLeft, initialPaddingTop + topInset, initialPaddingRight, initialPaddingBottom);
            return insets;
        });

        if (!isInternetAvailable()) {
            new AlertDialog.Builder(this)
                    .setTitle("No internet connection available")
                    .setMessage("Please check your Mobile data or Wifi network.")
                    .setPositiveButton("Ok", (dialog, which) -> finish())
                    .show();
        } else {

            // Enable Cookies
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.setAcceptThirdPartyCookies(webview, true);
            }

            webview.getSettings().setJavaScriptEnabled(true);
            webview.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void updateScrollPosition(int scrollTop) {
                    isWebPageScrolled = scrollTop > 0;
                }
            }, "AndroidInterface");

            webview.getSettings().setDomStorageEnabled(true);
            webview.getSettings().setAllowFileAccess(true);
            webview.getSettings().setAllowContentAccess(true);
            webview.getSettings().setGeolocationEnabled(true);
            webview.getSettings().setDatabaseEnabled(true);

            webview.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

            webview.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    isWebPageScrolled = false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    mySwipeRefreshLayout.setRefreshing(false);
                    // Force cookies to persist on disk
                    CookieManager.getInstance().flush();

                    // Inject JavaScript to track scroll events on any container (capture phase)
                    view.loadUrl("javascript:(function() { " +
                        "var scrollHandler = function(event) { " +
                        "    var target = event.target; " +
                        "    var scrollTop = 0; " +
                        "    if (target === document || target === window) { " +
                        "        scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop; " +
                        "    } else if (target && typeof target.scrollTop === 'number') { " +
                        "        scrollTop = target.scrollTop; " +
                        "    } " +
                        "    AndroidInterface.updateScrollPosition(scrollTop); " +
                        "}; " +
                        "document.removeEventListener('scroll', scrollHandler, true); " +
                        "document.addEventListener('scroll', scrollHandler, true); " +
                        "})()");
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    return handleUri(url);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return handleUri(url);
                }

                private boolean handleUri(String url) {
                    if (url.startsWith("tel:")) {
                        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } else if (url.startsWith("mailto:")) {
                        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } else if (url.startsWith("sms:")) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } else if (url.startsWith("whatsapp:")) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    }
                    return false;
                }
            });

            // ✅ ENABLE FILE UPLOAD (GALLERY PICKER)
            webview.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onShowFileChooser(
                        WebView webView,
                        ValueCallback<Uri[]> filePathCallback,
                        FileChooserParams fileChooserParams) {

                    MainActivity.this.filePathCallback = filePathCallback;

                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*"); // only images
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

                    filePickerLauncher.launch(Intent.createChooser(intent, "Select Images"));
                    return true;
                }

                @Override
                public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
                    // Request location permission if not granted
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        locationPermissionLauncher.launch(new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        });
                        // We can't easily wait for the result here to invoke callback.invoke(origin, true, false);
                        // Standard practice for simple WebView apps is to just allow it in the prompt
                        // and let the OS permission dialog handle the actual access.
                        callback.invoke(origin, true, false);
                    } else {
                        callback.invoke(origin, true, false);
                    }
                }
            });

            // ✅ ENABLE DOWNLOAD (FILES / IMAGES)
            webview.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                    handleDownloadRequest(url, userAgent, contentDisposition, mimeType)
            );

            webview.loadUrl(websiteURL);
        }

        mySwipeRefreshLayout.setOnRefreshListener(() -> {
            mySwipeRefreshLayout.setRefreshing(true);
            webview.reload();
        });
    }

    @Override
    public void onBackPressed() {
        if (webview != null && webview.canGoBack()) {
            webview.goBack();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("EXIT")
                    .setMessage("Are you sure you want to close this app?")
                    .setPositiveButton("Yes", (dialog, which) -> MainActivity.super.onBackPressed())
                    .setNegativeButton("No", null)
                    .show();
        }
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void handleDownloadRequest(String url, String userAgent, String contentDisposition, String mimeType) {
        if (url == null || url.trim().isEmpty()) return;

        // Android 9 and below need legacy storage permission for public Downloads.
        boolean needsLegacyWritePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P;

        if (needsLegacyWritePermission) {
            int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                pendingDownloadUrl = url;
                pendingDownloadUserAgent = userAgent;
                pendingDownloadContentDisposition = contentDisposition;
                pendingDownloadMimeType = mimeType;
                legacyWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return;
            }
        }

        startDownload(url, userAgent, contentDisposition, mimeType);
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            if (mimeType != null) request.setMimeType(mimeType);

            // Keep session cookies (important for authenticated downloads)
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);

            request.setTitle(fileName);
            request.setDescription("Downloading…");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.allowScanningByMediaScanner();
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm == null) {
                Toast.makeText(this, "Download manager not available.", Toast.LENGTH_LONG).show();
                return;
            }

            dm.enqueue(request);
            Toast.makeText(this, "Downloading: " + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    private void clearPendingDownload() {
        pendingDownloadUrl = null;
        pendingDownloadUserAgent = null;
        pendingDownloadContentDisposition = null;
        pendingDownloadMimeType = null;
    }
}