package com.cartoonworld.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int FILE_PICKER = 4201;
    private static final int SAVE_BACKUP = 4202;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingBackup;
    private String pendingBackupName = "cartoon-world-backup.json";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF0B0814);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
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

        if (state == null || webView.restoreState(state) == null) {
            webView.loadUrl("file:///android_asset/index.html");
        }
    }

    private final class NativeBridge {
        @JavascriptInterface
        public void saveBackup(String encoded, String fileName) {
            try {
                pendingBackup = Base64.decode(encoded, Base64.DEFAULT);
                pendingBackupName = (fileName == null || fileName.trim().isEmpty())
                        ? "cartoon-world-backup.json"
                        : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                runOnUiThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, pendingBackupName);
                    try {
                        startActivityForResult(intent, SAVE_BACKUP);
                    } catch (Exception e) {
                        pendingBackup = null;
                        Toast.makeText(MainActivity.this, "تعذر فتح نافذة حفظ الملف", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تعذر تجهيز النسخة الاحتياطية", Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface
        public void setKeepScreenOn(boolean keepOn) {
            runOnUiThread(() -> {
                if (keepOn) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        @JavascriptInterface
        public void shareText(String title, String text) {
            runOnUiThread(() -> {
                try {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_SUBJECT, title != null ? title : "عالم الكرتون");
                    send.putExtra(Intent.EXTRA_TEXT, text != null ? text : "");
                    startActivity(Intent.createChooser(send, "مشاركة"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذرت المشاركة", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void showToast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message != null ? message : "", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript(
                "(function(){" +
                "if(document.getElementById('player')&&document.getElementById('player').classList.contains('show')){closePlayer(true);return true;}" +
                "if(document.getElementById('miniPlayer')&&document.getElementById('miniPlayer').classList.contains('show')){closeMiniPlayer();return true;}" +
                "if(document.getElementById('modal')&&document.getElementById('modal').classList.contains('show')){closeModal();return true;}" +
                "if(window.currentPage&&window.currentPage!=='home'){goPage('home');return true;}" +
                "return false;})()",
                value -> {
                    if ("false".equals(value) || value == null) {
                        MainActivity.super.onBackPressed();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("CartoonWorldNative");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
