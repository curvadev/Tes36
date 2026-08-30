package com.curva.ppob;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.v4.content.FileProvider;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.journeyapps.barcodescanner.CaptureActivity;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private WebView webView;
    private RelativeLayout splash;
    private Handler splashHandler = new Handler();
    private Handler delayHandler = new Handler();

    private String fcmToken = "";
    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int REQUEST_CONTACT_PERMISSION = 1002;
    private static final int PICK_CONTACT_REQUEST = 1003;
    private static final int BARCODE_SCAN_REQUEST = 1004;
    private static final int REQUEST_BLUETOOTH_PERMISSION = 1005;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1006;
    private static final int REQUEST_APP_LOCK = 9999;

    private boolean isAppUnlocked = false;
    private boolean doubleBackToExitPressedOnce = false;

    // WEB VIEW MURNI KE WEBSITE
    private final String BASE_URL = "https://curva.web.id/ppob/";
    private final String HOME_URL = BASE_URL + "index.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStatusBarColor("#f5f5f5");

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(Color.parseColor("#1791f4"));

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                FrameLayout.LayoutParams.MATCH_PARENT));

        splash = new RelativeLayout(this);
        splash.setBackgroundColor(Color.parseColor("#f5f5f5"));
        splash.setClickable(true); 
        splash.setFocusable(true);

        LinearLayout centerWrap = new LinearLayout(this);
        centerWrap.setOrientation(LinearLayout.VERTICAL);
        centerWrap.setGravity(Gravity.CENTER);
        RelativeLayout.LayoutParams centerParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, 
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        centerParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        centerParams.bottomMargin = (int) (80 * getResources().getDisplayMetrics().density);
        splash.addView(centerWrap, centerParams);

        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("splash_logo", "drawable", getPackageName()));
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER); 
        logo.setAdjustViewBounds(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { 
            logo.setElevation(8f); 
        }

        int logoWidth = (int) (260 * getResources().getDisplayMetrics().density);
        centerWrap.addView(logo, new LinearLayout.LayoutParams(logoWidth, LinearLayout.LayoutParams.WRAP_CONTENT));

        String appVersion = "1.0.0";
        try { 
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0); 
            appVersion = pInfo.versionName; 
        } catch (Exception e) {}

        TextView version = new TextView(this);
        version.setText("Version " + appVersion); 
        version.setTextColor(Color.GRAY); 
        version.setTextSize(12);
        
        RelativeLayout.LayoutParams versionParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, 
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        versionParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); 
        versionParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        versionParams.bottomMargin = (int) (40 * getResources().getDisplayMetrics().density);
        splash.addView(version, versionParams);

        root.addView(splash, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        setupWebView();
        setupCookies();
        loadFcmToken();

        webView.setWebViewClient(new WebViewClient() {
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("intent://") || url.startsWith("market://") || 
                    url.startsWith("whatsapp://") || url.startsWith("tg://")) {
                    try { 
                        view.getContext().startActivity(Intent.parseUri(url, Intent.URI_INTENT_SCHEME)); 
                        return true; 
                    } catch (Exception e) {} 
                    return true;
                }
                return false;
            }

            @TargetApi(Build.VERSION_CODES.N)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return shouldOverrideUrlLoading(view, request.getUrl().toString());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                saveCookies(); 
                injectAndroidBridge(); 
                sendFcmTokenToWeb();
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showOfflineScreen(view);
            }

            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) { 
                    showOfflineScreen(view); 
                }
            }
        });

        // CEK SINYAL CACHE DARI SERVER
        checkRemoteCacheWipe();

        String initialUrl = handleDeepLink(getIntent());
        if (isNetworkAvailable()) {
            webView.loadUrl(initialUrl);
        } else {
            showOfflineScreen(webView);
        }

        splashHandler.postDelayed(new Runnable() {
            @Override
            public void run() { 
                triggerNativeAppLock(); 
            }
        }, 2500);
    }

    private void checkRemoteCacheWipe() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(BASE_URL + "api/cache_version.txt?t=" + System.currentTimeMillis());
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    
                    if (conn.getResponseCode() == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        final String serverVersion = br.readLine().trim();
                        br.close();

                        final String localVersion = getSharedPreferences("CurvaPrefs", MODE_PRIVATE).getString("native_cache_version", "");
                        
                        if (!serverVersion.isEmpty() && !serverVersion.equals(localVersion)) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // BERSiHKAN NATIVE CACHE WEBVIEW
                                    webView.clearCache(true);
                                    // BERSiHKAN LOCAL STORAGE BROWSER (UI)
                                    webView.evaluateJavascript("localStorage.clear(); sessionStorage.clear();", null);
                                    // UPDATE VERSI
                                    getSharedPreferences("CurvaPrefs", MODE_PRIVATE).edit().putString("native_cache_version", serverVersion).apply();
                                    // RELOAD
                                    webView.reload();
                                }
                            });
                        }
                    }
                } catch (Exception e) {}
            }
        }).start();
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            handleCustomBack(); 
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        handleCustomBack();
    }

    private void handleCustomBack() {
        if (webView != null) {
            webView.evaluateJavascript("javascript:(function() { var overlays = document.querySelectorAll('.show'); if (overlays.length > 0) { overlays.forEach(function(el) { el.classList.remove('show'); }); return 'true'; } return 'false'; })()", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    if (!"\"true\"".equals(value) && !"true".equals(value)) {
                        if (webView.canGoBack()) { 
                            webView.goBack(); 
                        } else { 
                            performDoubleBackToExit(); 
                        }
                    }
                }
            });
        } else { 
            performDoubleBackToExit(); 
        }
    }

    private void performDoubleBackToExit() {
        if (doubleBackToExitPressedOnce) { 
            finish(); 
            return; 
        }
        this.doubleBackToExitPressedOnce = true;
        showToast("Tekan sekali lagi untuk keluar aplikasi");
        new Handler().postDelayed(new Runnable() { 
            @Override 
            public void run() { 
                doubleBackToExitPressedOnce = false; 
            } 
        }, 2000);
    }

    private void triggerNativeAppLock() {
        if (isAppUnlocked) return;
        if (!getSharedPreferences("CurvaPrefs", MODE_PRIVATE).getBoolean("is_app_lock_enabled", false)) {
            unlockAppAndHideSplash(); 
            return;
        }
        android.app.KeyguardManager keyguardManager = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && keyguardManager != null && keyguardManager.isKeyguardSecure()) {
            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent("Keamanan Curva Payment", "Gunakan identitas Anda untuk masuk.");
            if (intent != null) { 
                startActivityForResult(intent, REQUEST_APP_LOCK); 
                return; 
            }
        }
        unlockAppAndHideSplash();
    }

    private void unlockAppAndHideSplash() {
        isAppUnlocked = true; 
        splash.animate().alpha(0f).setDuration(450);
        delayHandler.postDelayed(new Runnable() {
            @Override 
            public void run() { 
                setStatusBarColor("#1791f4"); 
                splash.setVisibility(View.GONE); 
                checkNotificationPermission(); 
                checkForAppUpdate(); 
            }
        }, 500);
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) { 
                notificationManager.createNotificationChannel(new android.app.NotificationChannel("curva_payment_notif", "Transaksi & Deposit", android.app.NotificationManager.IMPORTANCE_HIGH)); 
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) { 
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQUEST_NOTIFICATION_PERMISSION); 
        }
    }

    private boolean isVersionOlder(String currentVersion, String serverVersion) {
        if (currentVersion == null || serverVersion == null) return false;
        String[] currentParts = currentVersion.split("\\."); 
        String[] serverParts = serverVersion.split("\\.");
        int length = Math.max(currentParts.length, serverParts.length);
        
        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0; 
            int serverPart = i < serverParts.length ? Integer.parseInt(serverParts[i]) : 0;
            if (currentPart < serverPart) { return true; } 
            if (currentPart > serverPart) { return false; }
        } 
        return false;
    }

    private void checkForAppUpdate() {
        new Thread(new Runnable() {
            @Override 
            public void run() {
                try {
                    String bypassCacheUrl = BASE_URL + "api/check_version.php?timestamp=" + System.currentTimeMillis(); 
                    URL url = new URL(bypassCacheUrl); 
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection(); 
                    conn.setRequestMethod("GET"); 
                    conn.setUseCaches(false); 
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36"); 
                    conn.setRequestProperty("Accept", "application/json"); 
                    conn.setConnectTimeout(8000); 
                    conn.setReadTimeout(8000);
                    
                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream())); 
                        StringBuilder response = new StringBuilder(); 
                        String line;
                        while ((line = reader.readLine()) != null) { 
                            response.append(line); 
                        } 
                        reader.close();
                        
                        JSONObject json = new JSONObject(response.toString());
                        if (json.getBoolean("success")) {
                            final String serverVersion = json.has("latest_version_name") ? json.getString("latest_version_name") : "1.0.0"; 
                            final boolean forceUpdate = json.getBoolean("force_update"); 
                            final String updateUrl = json.getString("update_url"); 
                            final String releaseNotes = json.getString("release_notes"); 
                            final String currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                            
                            runOnUiThread(new Runnable() { 
                                @Override 
                                public void run() { 
                                    if (isVersionOlder(currentVersion, serverVersion) && !isFinishing()) { 
                                        showUpdateDialog(forceUpdate, updateUrl, releaseNotes); 
                                    } 
                                } 
                            });
                        }
                    }
                } catch (Exception e) {}
            }
        }).start();
    }

    private void showUpdateDialog(final boolean isForced, final String url, String notes) {
        final android.app.Dialog dialog = new android.app.Dialog(MainActivity.this); 
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); 
        dialog.setCancelable(!isForced);
        if (dialog.getWindow() != null) { 
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); 
        }
        
        LinearLayout root = new LinearLayout(this); 
        root.setOrientation(LinearLayout.VERTICAL); 
        root.setPadding(60, 60, 60, 60);
        
        android.graphics.drawable.GradientDrawable bgShape = new android.graphics.drawable.GradientDrawable(); 
        bgShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE); 
        bgShape.setCornerRadius(50); 
        bgShape.setColor(Color.WHITE); 
        root.setBackground(bgShape);
        
        TextView title = new TextView(this); 
        title.setText("Pembaruan Tersedia \uD83D\uDE80"); 
        title.setTextSize(22); 
        title.setTextColor(Color.parseColor("#1e293b")); 
        title.setTypeface(null, android.graphics.Typeface.BOLD); 
        title.setGravity(Gravity.CENTER); 
        root.addView(title);
        
        TextView message = new TextView(this); 
        message.setText(notes); 
        message.setTextSize(15); 
        message.setTextColor(Color.parseColor("#64748b")); 
        message.setGravity(Gravity.LEFT); 
        message.setLineSpacing(0, 1.2f);
        
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT); 
        msgParams.setMargins(0, 50, 0, 60); 
        root.addView(message, msgParams);
        
        TextView btnUpdate = new TextView(this); 
        btnUpdate.setText("Perbarui Sekarang"); 
        btnUpdate.setTextColor(Color.WHITE); 
        btnUpdate.setTextSize(16); 
        btnUpdate.setTypeface(null, android.graphics.Typeface.BOLD); 
        btnUpdate.setGravity(Gravity.CENTER); 
        btnUpdate.setPadding(0, 35, 0, 35);
        
        android.graphics.drawable.GradientDrawable btnShape = new android.graphics.drawable.GradientDrawable(); 
        btnShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE); 
        btnShape.setCornerRadius(50); 
        btnShape.setColor(Color.parseColor("#1791f4")); 
        btnUpdate.setBackground(btnShape);
        
        btnUpdate.setOnClickListener(new View.OnClickListener() { 
            @Override 
            public void onClick(View v) { 
                dialog.dismiss(); 
                try { 
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse((url != null && !url.isEmpty()) ? url : "https://curva.web.id/download.php"))); 
                } catch (Exception e) {} 
            } 
        }); 
        root.addView(btnUpdate);
        
        if (!isForced) {
            TextView btnLater = new TextView(this); 
            btnLater.setText("Nanti Saja"); 
            btnLater.setTextColor(Color.parseColor("#94a3b8")); 
            btnLater.setTextSize(15); 
            btnLater.setTypeface(null, android.graphics.Typeface.BOLD); 
            btnLater.setGravity(Gravity.CENTER); 
            btnLater.setPadding(0, 25, 0, 25);
            
            LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT); 
            laterParams.setMargins(0, 30, 0, 0);
            
            btnLater.setOnClickListener(new View.OnClickListener() { 
                @Override 
                public void onClick(View v) { 
                    dialog.dismiss(); 
                } 
            }); 
            root.addView(btnLater, laterParams);
        }
        
        dialog.setContentView(root); 
        if (dialog.getWindow() != null) { 
            dialog.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.85), android.view.ViewGroup.LayoutParams.WRAP_CONTENT); 
        } 
        dialog.show();
    }

    private void showOfflineScreen(WebView view) {
        String offlineHtml = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head><body style=\"display:flex;flex-direction:column;justify-content:center;align-items:center;height:100vh;margin:0;background-color:#f8fafc;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;text-align:center;padding:20px;\"><h2 style=\"color:#1e293b;margin:0 0 10px;font-size:22px;font-weight:800;\">Koneksi Terputus</h2><p style=\"color:#64748b;font-size:15px;margin-bottom:24px;\">Silakan periksa jaringan internet Anda.</p><button onclick=\"prompt('AndroidBridge:retryConnection', '')\" style=\"background:#1791f4;color:#fff;border:none;padding:14px 32px;border-radius:16px;font-size:16px;font-weight:bold;cursor:pointer;width:100%;max-width:250px;\">Coba Lagi</button></body></html>";
        view.loadDataWithBaseURL(null, offlineHtml, "text/html", "UTF-8", null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); 
        setIntent(intent);
        if (intent != null && intent.getExtras() != null) {
            String newUrl = handleDeepLink(intent);
            if (!newUrl.equals(HOME_URL)) { 
                if (isNetworkAvailable()) { 
                    webView.loadUrl(newUrl); 
                } else { 
                    showOfflineScreen(webView); 
                } 
            }
        }
    }

    private String handleDeepLink(Intent intent) {
        String urlToLoad = HOME_URL; 
        if (intent != null && intent.getExtras() != null) {
            String type = intent.getStringExtra("type");
            if ("deposit_success".equals(type)) {
                String notifId = intent.getStringExtra("notification_id"); 
                urlToLoad = BASE_URL + (notifId != null ? "user/notifications.php?show_id=" + notifId : "user/notifications.php");
            } else if ("transaction_success".equals(type) || "transaction_failed".equals(type)) {
                String trxId = intent.getStringExtra("transaction_id"); 
                if (trxId != null) { 
                    urlToLoad = BASE_URL + "user/transaction_detail.php?id=" + trxId; 
                }
            } else if (intent.hasExtra("target_url")) {
                String target = intent.getStringExtra("target_url"); 
                if (target != null && !target.isEmpty()) { 
                    urlToLoad = target.startsWith("http") ? target : BASE_URL + (target.startsWith("/") ? target.substring(1) : target); 
                }
            }
        }
        return urlToLoad;
    }

    private void injectAndroidBridge() {
        String js = "javascript:(function(){ if (typeof window.Android === 'undefined') { window.Android = {" +
                "getContacts: function() { return prompt('AndroidBridge:getContacts', ''); }," +
                "requestContactPermission: function() { prompt('AndroidBridge:requestContactPermission', ''); }," +
                "showToast: function(message) { prompt('AndroidBridge:showToast', message); }," +
                "openContactPicker: function() { prompt('AndroidBridge:openContactPicker', ''); }," +
                "scanBarcode: function() { prompt('AndroidBridge:scanBarcode', ''); }," +
                "printReceipt: function(data, base64) { if(base64 === undefined) base64 = ''; prompt('AndroidBridge:printReceipt', data + '|||SPLIT|||' + base64); }," +
                "shareImage: function(base64) { prompt('AndroidBridge:shareImage', base64); }," +
                "shareReceiptText: function(base64, caption) { if(caption === undefined) caption = ''; prompt('AndroidBridge:shareReceiptText', base64 + '|||SPLIT|||' + caption); }," +
                "saveImage: function(base64, filename) { prompt('AndroidBridge:saveImage', base64 + '|||SPLIT|||' + filename); }," +
                "openApp: function(url) { prompt('AndroidBridge:openApp', url); }," +
                "updateFCMToken: function() { prompt('AndroidBridge:updateFCMToken', ''); }," +
                "setAppLock: function(status) { prompt('AndroidBridge:setAppLock', status); }," +
                "getPrinters: function() { return prompt('AndroidBridge:getPrinters', ''); }," +
                "printReceiptMAC: function(data) { prompt('AndroidBridge:printReceiptMAC', data); }" +
                "}; } })()";
        webView.evaluateJavascript(js, null);
    }

    private boolean handleAndroidBridge(String message, String defaultValue, JsPromptResult result) {
        if (message != null && message.startsWith("AndroidBridge:")) {
            String action = message.replace("AndroidBridge:", "");
            
            if (action.equals("getContacts")) { 
                result.confirm(getContactsFromDevice()); 
                return true; 
            } else if (action.equals("requestContactPermission")) { 
                requestContactPermission(); 
                result.confirm(""); 
                return true; 
            } else if (action.equals("showToast")) { 
                showToast(defaultValue); 
                result.confirm(""); 
                return true; 
            } else if (action.equals("openContactPicker")) { 
                openContactPicker(); 
                result.confirm(""); 
                return true; 
            } else if (action.equals("scanBarcode")) { 
                scanBarcode(); 
                result.confirm(""); 
                return true; 
            } else if (action.equals("shareImage")) { 
                shareImage(defaultValue); 
                result.confirm(""); 
                return true; 
            } else if (action.equals("openApp")) { 
                openAppNative(defaultValue); 
                result.confirm(""); 
                return true; 
            } else if (action.equals("updateFCMToken")) { 
                runOnUiThread(new Runnable() { 
                    @Override 
                    public void run() { 
                        sendFcmTokenToWeb(); 
                    } 
                }); 
                result.confirm(""); 
                return true; 
            } else if (action.equals("setAppLock")) { 
                getSharedPreferences("CurvaPrefs", MODE_PRIVATE).edit().putBoolean("is_app_lock_enabled", defaultValue.equals("true")).apply(); 
                result.confirm(""); 
                return true; 
            } else if (action.equals("retryConnection")) { 
                runOnUiThread(new Runnable() { 
                    @Override 
                    public void run() { 
                        if (isNetworkAvailable()) { 
                            webView.loadUrl(HOME_URL); 
                        } else { 
                            showToast("Koneksi internet masih terputus."); 
                        } 
                    } 
                }); 
                result.confirm(""); 
                return true; 
            } else if (action.equals("shareReceiptText")) { 
                String base64Str = defaultValue; 
                String captionStr = "Berikut adalah bukti pembayaran."; 
                if (defaultValue != null && defaultValue.contains("|||SPLIT|||")) { 
                    String[] parts = defaultValue.split("\\|\\|\\|SPLIT\\|\\|\\|"); 
                    base64Str = parts.length > 0 ? parts[0] : ""; 
                    captionStr = parts.length > 1 ? parts[1] : ""; 
                } 
                shareReceiptText(base64Str, captionStr); 
                result.confirm(""); 
                return true; 
            } else if (action.equals("saveImage")) { 
                String base64Str = defaultValue; 
                String fileNameStr = "QRIS_Deposit.png"; 
                if (defaultValue != null && defaultValue.contains("|||SPLIT|||")) { 
                    String[] parts = defaultValue.split("\\|\\|\\|SPLIT\\|\\|\\|"); 
                    base64Str = parts.length > 0 ? parts[0] : ""; 
                    fileNameStr = parts.length > 1 ? parts[1] : "QRIS_Deposit.png"; 
                } 
                saveImage(base64Str, fileNameStr); 
                result.confirm(""); 
                return true; 
            } else if (action.equals("printReceipt")) { 
                String textToPrint = defaultValue; 
                String logoBase64 = ""; 
                if (defaultValue != null && defaultValue.contains("|||SPLIT|||")) { 
                    String[] parts = defaultValue.split("\\|\\|\\|SPLIT\\|\\|\\|"); 
                    textToPrint = parts.length > 0 ? parts[0] : ""; 
                    logoBase64 = parts.length > 1 ? parts[1] : ""; 
                } 
                autoPrintReceipt(textToPrint, logoBase64); 
                result.confirm(""); 
                return true; 
            } else if (action.equals("getPrinters")) { 
                result.confirm(getPairedPrintersList()); 
                return true; 
            } else if (action.equals("printReceiptMAC")) { 
                String textToPrint = ""; 
                String logoBase64 = ""; 
                String macAddress = ""; 
                if (defaultValue != null && defaultValue.contains("|||SPLIT|||")) { 
                    String[] parts = defaultValue.split("\\|\\|\\|SPLIT\\|\\|\\|"); 
                    textToPrint = parts.length > 0 ? parts[0] : ""; 
                    logoBase64 = parts.length > 1 ? parts[1] : ""; 
                    macAddress = parts.length > 2 ? parts[2] : ""; 
                } 
                printToSpecificPrinter(textToPrint, logoBase64, macAddress); 
                result.confirm(""); 
                return true; 
            }
        }
        return false;
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        settings.setAppCacheEnabled(true);
        settings.setAppCachePath(getApplicationContext().getCacheDir().getAbsolutePath());
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setUserAgentString(settings.getUserAgentString() + " CurvaApp");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                String[] acceptTypes = fileChooserParams.getAcceptTypes();
                Intent intent;
                String chooserTitle;

                if (acceptTypes != null && acceptTypes.length > 0 && acceptTypes[0].contains("image")) {
                    intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    intent.setType("image/*");
                    chooserTitle = "Pilih Aplikasi Foto";
                } else {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    chooserTitle = "Pilih Aplikasi File Manager";
                }

                Intent chooserIntent = Intent.createChooser(intent, chooserTitle);
                try {
                    startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                if (MainActivity.this.handleAndroidBridge(message, defaultValue, result)) {
                    return true;
                }
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }
        });
    }

    private String getPairedPrintersList() {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) { 
            return "NOT_SUPPORTED"; 
        }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) { 
            requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, REQUEST_BLUETOOTH_PERMISSION); 
            return "[]"; 
        }
        
        JSONArray arr = new JSONArray(); 
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        
        for (BluetoothDevice device : pairedDevices) {
            try { 
                String name = device.getName() != null ? device.getName() : "Printer"; 
                int majorClass = device.getBluetoothClass() != null ? device.getBluetoothClass().getMajorDeviceClass() : -1; 
                if (majorClass == BluetoothClass.Device.Major.IMAGING || name.toLowerCase().contains("print") || name.toLowerCase().contains("mtp") || name.toLowerCase().contains("zj") || name.toLowerCase().contains("58") || name.toLowerCase().contains("80") || name.toLowerCase().contains("pt-") || name.toLowerCase().contains("blue")) { 
                    JSONObject obj = new JSONObject(); 
                    obj.put("name", name); 
                    obj.put("mac", device.getAddress()); 
                    arr.put(obj); 
                } 
            } catch (Exception e) {}
        }
        return arr.toString();
    }

    private void printToSpecificPrinter(final String textToPrint, final String logoBase64, final String macAddress) {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled() || macAddress == null || macAddress.isEmpty()) { 
            notifyWebPrintFailed("Gagal menyambung. Bluetooth tidak aktif atau MAC salah."); 
            return; 
        }
        
        new Thread(new Runnable() {
            @Override 
            public void run() {
                try {
                    BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress); 
                    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); 
                    byte[] imageBytes = null;
                    
                    if (logoBase64 != null && logoBase64.startsWith("data:image")) { 
                        try { 
                            String pureBase64 = logoBase64.substring(logoBase64.indexOf(",") + 1); 
                            byte[] decodedString = Base64.decode(pureBase64, Base64.DEFAULT); 
                            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length); 
                            if(bitmap != null){ 
                                int targetWidth = 360; 
                                targetWidth = (targetWidth / 8) * 8; 
                                int targetHeight = (int) (bitmap.getHeight() * ((float) targetWidth / bitmap.getWidth())); 
                                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true); 
                                imageBytes = decodeBitmapToEscPos(scaledBitmap); 
                            } 
                        } catch (Exception e) {} 
                    }
                    
                    BluetoothSocket socket = device.createRfcommSocketToServiceRecord(uuid); 
                    socket.connect(); 
                    OutputStream outputStream = socket.getOutputStream(); 
                    outputStream.write(new byte[]{27, 64}); 
                    
                    if (imageBytes != null) { 
                        outputStream.write(new byte[]{27, 97, 1}); 
                        outputStream.flush(); 
                        int chunkSize = 256; 
                        for (int i = 0; i < imageBytes.length; i += chunkSize) { 
                            int length = Math.min(chunkSize, imageBytes.length - i); 
                            outputStream.write(imageBytes, i, length); 
                            outputStream.flush(); 
                            Thread.sleep(15); 
                        } 
                        outputStream.write("\n\n".getBytes()); 
                        outputStream.flush(); 
                        Thread.sleep(400); 
                    }
                    
                    outputStream.write(textToPrint.getBytes("UTF-8")); 
                    outputStream.write("\n\n\n".getBytes()); 
                    outputStream.flush(); 
                    socket.close(); 
                    
                    final String pName = device.getName(); 
                    runOnUiThread(new Runnable() { 
                        @Override 
                        public void run() { 
                            webView.evaluateJavascript("javascript:if(typeof onPrintResult === 'function'){onPrintResult(true, 'Berhasil mencetak di printer: " + pName + "');}", null); 
                        } 
                    });
                } catch (Exception e) { 
                    notifyWebPrintFailed("Gagal terhubung ke printer. Pastikan printer menyala."); 
                }
            }
        }).start();
    }

    private void notifyWebPrintFailed(final String msg) { 
        runOnUiThread(new Runnable() { 
            @Override 
            public void run() { 
                webView.evaluateJavascript("javascript:if(typeof onPrintResult === 'function'){onPrintResult(false, '" + msg + "');}", null); 
            } 
        }); 
    }

    private void autoPrintReceipt(final String textToPrint, final String logoBase64) {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) { 
            notifyWebPrintFailed("Perangkat tidak mendukung Bluetooth"); 
            return; 
        }
        if (!bluetoothAdapter.isEnabled()) { 
            notifyWebPrintFailed("Silakan aktifkan Bluetooth HP Anda"); 
            return; 
        }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) { 
            requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, REQUEST_BLUETOOTH_PERMISSION); 
            notifyWebPrintFailed("Izin Bluetooth belum diberikan"); 
            return; 
        }
        
        final Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices(); 
        if (pairedDevices.size() == 0) { 
            notifyWebPrintFailed("Belum ada printer yang di-pairing"); 
            return; 
        }
        
        final ArrayList<BluetoothDevice> likelyPrinters = new ArrayList<>(); 
        final ArrayList<BluetoothDevice> otherDevices = new ArrayList<>();
        
        for (BluetoothDevice device : pairedDevices) { 
            String name = device.getName() != null ? device.getName().toLowerCase() : ""; 
            int majorClass = device.getBluetoothClass() != null ? device.getBluetoothClass().getMajorDeviceClass() : -1; 
            if (majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO || majorClass == BluetoothClass.Device.Major.PHONE || majorClass == BluetoothClass.Device.Major.COMPUTER) { 
                continue; 
            } 
            if (majorClass == BluetoothClass.Device.Major.IMAGING || name.contains("print") || name.contains("mtp") || name.contains("zj") || name.contains("58") || name.contains("80") || name.contains("pt-") || name.contains("blue")) { 
                likelyPrinters.add(device); 
            } else { 
                otherDevices.add(device); 
            } 
        }
        
        final ArrayList<BluetoothDevice> devicesToTry = new ArrayList<>(); 
        devicesToTry.addAll(likelyPrinters); 
        devicesToTry.addAll(otherDevices);
        
        if (devicesToTry.isEmpty()) { 
            notifyWebPrintFailed("Tidak ada perangkat tipe printer"); 
            return; 
        }

        new Thread(new Runnable() {
            @Override 
            public void run() {
                boolean isPrinted = false; 
                String printerName = ""; 
                UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); 
                byte[] imageBytes = null;
                
                if (logoBase64 != null && logoBase64.startsWith("data:image")) { 
                    try { 
                        String pureBase64 = logoBase64.substring(logoBase64.indexOf(",") + 1); 
                        byte[] decodedString = Base64.decode(pureBase64, Base64.DEFAULT); 
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length); 
                        if(bitmap != null){ 
                            int targetWidth = 360; 
                            targetWidth = (targetWidth / 8) * 8; 
                            int targetHeight = (int) (bitmap.getHeight() * ((float) targetWidth / bitmap.getWidth())); 
                            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true); 
                            imageBytes = decodeBitmapToEscPos(scaledBitmap); 
                        } 
                    } catch (Exception e) {} 
                }
                
                for (BluetoothDevice device : devicesToTry) {
                    try { 
                        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(uuid); 
                        socket.connect(); 
                        OutputStream outputStream = socket.getOutputStream(); 
                        outputStream.write(new byte[]{27, 64}); 
                        
                        if (imageBytes != null) { 
                            outputStream.write(new byte[]{27, 97, 1}); 
                            outputStream.flush(); 
                            int chunkSize = 256; 
                            for (int i = 0; i < imageBytes.length; i += chunkSize) { 
                                int length = Math.min(chunkSize, imageBytes.length - i); 
                                outputStream.write(imageBytes, i, length); 
                                outputStream.flush(); 
                                Thread.sleep(15); 
                            } 
                            outputStream.write("\n\n".getBytes()); 
                            outputStream.flush(); 
                            Thread.sleep(400); 
                        }
                        
                        outputStream.write(textToPrint.getBytes("UTF-8")); 
                        outputStream.write("\n\n\n".getBytes()); 
                        outputStream.flush(); 
                        socket.close(); 
                        isPrinted = true; 
                        printerName = device.getName(); 
                        break; 
                    } catch (Exception e) { 
                        continue; 
                    }
                }
                
                if (isPrinted) { 
                    final String pN = printerName; 
                    runOnUiThread(new Runnable() { 
                        @Override 
                        public void run() { 
                            webView.evaluateJavascript("javascript:if(typeof onPrintResult === 'function'){onPrintResult(true, 'Berhasil mencetak di printer: " + pN + "');}", null); 
                        } 
                    }); 
                } else { 
                    notifyWebPrintFailed("Gagal terhubung ke seluruh printer paired."); 
                }
            }
        }).start();
    }

    private byte[] decodeBitmapToEscPos(Bitmap bmp) {
        int width = bmp.getWidth(); 
        int height = bmp.getHeight(); 
        int xBytes = (width + 7) / 8; 
        byte[] data = new byte[8 + (xBytes * height)];
        data[0] = 0x1D; data[1] = 0x76; data[2] = 0x30; data[3] = 0x00; 
        data[4] = (byte) (xBytes % 256); data[5] = (byte) (xBytes / 256); 
        data[6] = (byte) (height % 256); data[7] = (byte) (height / 256); 
        int idx = 8;
        
        for (int y = 0; y < height; y++) { 
            for (int x = 0; x < xBytes * 8; x += 8) { 
                byte b = 0; 
                for (int k = 0; k < 8; k++) { 
                    if (x + k < width) { 
                        int pixel = bmp.getPixel(x + k, y); 
                        if (Color.alpha(pixel) < 128) continue; 
                        int luminance = (int) (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114); 
                        if (luminance < 235) { 
                            b |= (1 << (7 - k)); 
                        } 
                    } 
                } 
                data[idx++] = b; 
            } 
        } 
        return data;
    }

    private void shareImage(String base64Data) {
        try { 
            byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT); 
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length); 
            File cachePath = new File(getCacheDir(), "images"); 
            cachePath.mkdirs(); 
            File file = new File(cachePath, "struk_curva_payment.png"); 
            FileOutputStream stream = new FileOutputStream(file); 
            decodedByte.compress(Bitmap.CompressFormat.PNG, 100, stream); 
            stream.close(); 
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            
            if (contentUri != null) { 
                Intent shareIntent = new Intent(); 
                shareIntent.setAction(Intent.ACTION_SEND); 
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); 
                shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri)); 
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri); 
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Berikut adalah bukti pembayaran dari Curva Payment."); 
                startActivity(Intent.createChooser(shareIntent, "Bagikan Struk Melalui...")); 
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    @android.webkit.JavascriptInterface
    private void shareReceiptText(String base64Data, String customCaption) {
        try { 
            byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT); 
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length); 
            File cachePath = new File(getCacheDir(), "images"); 
            cachePath.mkdirs(); 
            File file = new File(cachePath, "struk_pembayaran.png"); 
            FileOutputStream stream = new FileOutputStream(file); 
            decodedByte.compress(Bitmap.CompressFormat.PNG, 100, stream); 
            stream.close(); 
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            
            if (contentUri != null) { 
                Intent shareIntent = new Intent(); 
                shareIntent.setAction(Intent.ACTION_SEND); 
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); 
                shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri)); 
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri); 
                shareIntent.putExtra(Intent.EXTRA_TEXT, customCaption); 
                startActivity(Intent.createChooser(shareIntent, "Bagikan Struk Melalui...")); 
            }
        } catch (IOException e) { 
            e.printStackTrace(); 
            showToast("Gagal memproses gambar struk"); 
        }
    }

    @android.webkit.JavascriptInterface
    public void saveImage(String base64String, String fileName) {
        try { 
            byte[] decodedString = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT); 
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length); 
            android.content.ContentValues values = new android.content.ContentValues(); 
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName); 
            values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png"); 
            if (android.os.Build.VERSION.SDK_INT >= 29) { 
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1); 
            } 
            
            android.content.ContentResolver resolver = MainActivity.this.getContentResolver(); 
            android.net.Uri uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            
            if (uri != null) { 
                java.io.OutputStream out = resolver.openOutputStream(uri); 
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); 
                out.flush(); 
                out.close(); 
                if (android.os.Build.VERSION.SDK_INT >= 29) { 
                    values.clear(); 
                    values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0); 
                    resolver.update(uri, values, null, null); 
                } 
                MainActivity.this.runOnUiThread(new Runnable() { 
                    public void run() { 
                        android.widget.Toast.makeText(MainActivity.this, "Gambar berhasil disimpan ke Galeri", android.widget.Toast.LENGTH_SHORT).show(); 
                    } 
                }); 
            } else { 
                throw new Exception("URI is null"); 
            }
        } catch (Exception e) { 
            e.printStackTrace(); 
            MainActivity.this.runOnUiThread(new Runnable() { 
                public void run() { 
                    android.widget.Toast.makeText(MainActivity.this, "Gagal menyimpan gambar", android.widget.Toast.LENGTH_SHORT).show(); 
                } 
            }); 
        }
    }

    private void scanBarcode() {
        try { 
            Intent intent = new Intent(this, CaptureActivity.class); 
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE,PRODUCT_MODE"); 
            intent.putExtra("ORIENTATION_LOCK", true); 
            startActivityForResult(intent, BARCODE_SCAN_REQUEST); 
        } catch (Exception e) { 
            try { 
                Intent intent = new Intent("com.google.zxing.client.android.SCAN"); 
                intent.putExtra("SCAN_MODE", "QR_CODE_MODE,PRODUCT_MODE"); 
                startActivityForResult(intent, BARCODE_SCAN_REQUEST); 
            } catch (Exception ex) {} 
        }
    }

    private void openContactPicker() {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) { 
            requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, REQUEST_CONTACT_PERMISSION); 
            return; 
        }
        Intent intent = new Intent(Intent.ACTION_PICK); 
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE); 
        startActivityForResult(intent, PICK_CONTACT_REQUEST);
    }

    private String getContactsFromDevice() {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) { 
            return "[]"; 
        }
        JSONArray contactsArray = new JSONArray(); 
        ContentResolver cr = getContentResolver();
        
        try {
            Cursor cursor = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
            if (cursor != null && cursor.getCount() > 0) { 
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME); 
                int phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                while (cursor.moveToNext()) { 
                    String name = cursor.getString(nameIndex); 
                    String phone = cursor.getString(phoneIndex); 
                    if (phone != null) { 
                        phone = phone.replaceAll("[^0-9]", ""); 
                        if (phone.startsWith("62")) { phone = "0" + phone.substring(2); } 
                        if (phone.length() > 13) { phone = phone.substring(phone.length() - 13); } 
                    }
                    if (name != null && phone != null && phone.length() >= 10) { 
                        JSONObject contact = new JSONObject(); 
                        contact.put("name", name); 
                        contact.put("tel", phone); 
                        contactsArray.put(contact); 
                    }
                } 
                cursor.close();
            }
        } catch (Exception e) {} 
        
        return contactsArray.toString();
    }

    private void requestContactPermission() { 
        requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, REQUEST_CONTACT_PERMISSION); 
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CONTACT_PERMISSION) { 
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) { 
                openContactPicker(); 
            } else { 
                webView.evaluateJavascript("javascript:onAndroidContactsError('Izin ditolak')", null); 
            } 
        } 
    }

    private void setStatusBarColor(String color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { 
            Window window = getWindow(); 
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS); 
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS); 
            window.setStatusBarColor(Color.parseColor(color)); 
            window.getDecorView().setSystemUiVisibility(0); 
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) { 
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo(); 
            return activeNetworkInfo != null && activeNetworkInfo.isConnected(); 
        } 
        return false;
    }

    private void setupCookies() { 
        CookieManager cookieManager = CookieManager.getInstance(); 
        cookieManager.setAcceptCookie(true); 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { 
            cookieManager.setAcceptThirdPartyCookies(webView, true); 
        } 
    }
    
    private void saveCookies() { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { 
            CookieManager.getInstance().flush(); 
        } 
    }

    private void loadFcmToken() {
        try { 
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new com.google.android.gms.tasks.OnCompleteListener<String>() { 
                @Override 
                public void onComplete(com.google.android.gms.tasks.Task<String> task) { 
                    if (!task.isSuccessful()) { fcmToken = ""; return; } 
                    fcmToken = task.getResult(); 
                    sendFcmTokenToWeb(); 
                } 
            }); 
        } catch (Exception e) { 
            fcmToken = ""; 
        }
    }

    public void sendFcmTokenToWeb() {
        if (fcmToken == null || fcmToken.equals("")) { loadFcmToken(); } 
        if (fcmToken == null || fcmToken.equals("")) { return; }
        
        try { 
            String token = java.net.URLEncoder.encode(fcmToken, "UTF-8"); 
            String device = java.net.URLEncoder.encode("Android WebView", "UTF-8"); 
            final String js = "javascript:(function(){ try{var xhr=new XMLHttpRequest(); xhr.open('POST','" + BASE_URL + "api/save_fcm_token.php',true); xhr.setRequestHeader('Content-Type','application/x-www-form-urlencoded'); xhr.send('token=" + token + "&device_name=" + device + "');}catch(e){} })()";
            
            webView.post(new Runnable() { 
                @Override 
                public void run() { 
                    webView.evaluateJavascript(js, null); 
                } 
            });
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }

    public void showToast(final String message) { 
        runOnUiThread(new Runnable() { 
            @Override 
            public void run() { 
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show(); 
            } 
        }); 
    }

    private void openAppNative(String url) {
        try { 
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); 
        } catch (Exception e) { 
            try { 
                if (url.startsWith("whatsapp://")) { 
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url.replace("whatsapp://send?phone=", "https://wa.me/")))); 
                } else if (url.startsWith("tg://")) { 
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url.replace("tg://resolve?domain=", "https://t.me/")))); 
                } 
            } catch (Exception ex) {} 
        }
    }
}
