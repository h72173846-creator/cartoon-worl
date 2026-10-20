package com.cartoonworld.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.webkit.WebViewAssetLoader;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int FILE_PICKER = 4201;
    private static final int SAVE_BACKUP = 4202;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingBackup;
    private static final String APP_URL = "https://appassets.androidplatform.net/assets/index.html";
    private WebViewAssetLoader assetLoader;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private String pendingBackupName = "cartoon-world-backup.json";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF120D22);
        getWindow().setNavigationBarColor(0xFF120D22);
        root = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF100B1D);
        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                customViewCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.setVisibility(View.GONE);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            @Override public void onHideCustomView() { exitFullscreenVideo(); }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, FILE_PICKER);
                } catch (Exception e) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "تعذر فتح مدير الملفات", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return true;
            }
        });
        webView.addJavascriptInterface(new NativeBridge(), "CartoonWorldNative");
        if (state == null || webView.restoreState(state) == null) webView.loadUrl(APP_URL);
    }

    private void exitFullscreenVideo() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        webView.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if (customViewCallback != null) { customViewCallback.onCustomViewHidden(); customViewCallback = null; }
    }

    private final class NativeBridge {
        @JavascriptInterface public void saveBackup(String encoded, String fileName) {
            try {
                pendingBackup = Base64.decode(encoded, Base64.DEFAULT);
                pendingBackupName = (fileName == null || fileName.trim().isEmpty())
                    ? "cartoon-world-backup.json" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                runOnUiThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, pendingBackupName);
                    try { startActivityForResult(intent, SAVE_BACKUP); }
                    catch (Exception e) { pendingBackup = null; Toast.makeText(MainActivity.this, "تعذر فتح نافذة حفظ الملف", Toast.LENGTH_LONG).show(); }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تعذر تجهيز النسخة الاحتياطية", Toast.LENGTH_LONG).show());
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER && fileCallback != null) {
            Uri[] result = resultCode == RESULT_OK && data != null
                ? WebChromeClient.FileChooserParams.parseResult(resultCode, data) : null;
            fileCallback.onReceiveValue(result);
            fileCallback = null;
            return;
        }
        if (requestCode == SAVE_BACKUP) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingBackup != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new IllegalStateException("No output stream");
                    out.write(pendingBackup);
                    out.flush();
                    Toast.makeText(this, "تم حفظ النسخة الاحتياطية", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "فشل حفظ النسخة الاحتياطية", Toast.LENGTH_LONG).show();
                }
            }
            pendingBackup = null;
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override public void onBackPressed() {
        if (customView != null) { exitFullscreenVideo(); return; }
        if (webView == null) { super.onBackPressed(); return; }
        webView.evaluateJavascript("(function(){if(document.getElementById('player').classList.contains('show')){closePlayer();return true;}if(document.getElementById('modal').classList.contains('show')){closeModal();return true;}if(document.getElementById('homePage').hidden){goPage('home');return true;}return false;})()", value -> {
            if ("false".equals(value) || value == null) MainActivity.super.onBackPressed();
        });
    }

    @Override protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override protected void onDestroy() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (webView != null) { webView.removeJavascriptInterface("CartoonWorldNative"); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
