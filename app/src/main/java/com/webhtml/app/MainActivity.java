package com.webhtml.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> uploadMessage;
    private final static int FILE_CHOOSER_RESULT_CODE = 1;

    // Dinamički User-Agent koji može da se menja u letu
    private String currentDesktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @SuppressLint({"SetJavaScriptEnabled", "QueryPermissionsNeeded", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        webView = new WebView(this);
        setContentView(webView);

        WebSettings webSettings = webView.getSettings();

        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setUserAgentString(currentDesktopUA);

        // AndroidBridge za promenu UA u letu iz JavaScript-a
        webView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void setUserAgent(String ua) {
                currentDesktopUA = ua;
                runOnUiThread(() -> {
                    webView.getSettings().setUserAgentString(ua);
                });
            }
        }, "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String urlStr = request.getUrl().toString();
                
                // Ako URL sadrži UA parametar (bilo kroz hash ili query), izvlačimo ga u hodu
                String targetUA = currentDesktopUA;
                String cleanUrlStr = urlStr;

                if (urlStr.contains("#ua=")) {
                    try {
                        String[] parts = urlStr.split("#ua=");
                        cleanUrlStr = parts[0];
                        targetUA = URLDecoder.decode(parts[1].split("&")[0], "UTF-8");
                        // Automatski ažuriramo globalni UA
                        currentDesktopUA = targetUA;
                    } catch (Exception ignored) {}
                }

                if (cleanUrlStr.contains("youtube.com/results")) {
                    try {
                        URL url = new URL(cleanUrlStr);
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                        connection.setRequestProperty("User-Agent", targetUA);
                        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                        connection.setConnectTimeout(10000);
                        connection.setReadTimeout(10000);

                        InputStream inputStream = connection.getInputStream();
                        String mimeType = connection.getContentType();
                        if (mimeType == null) mimeType = "text/html; charset=UTF-8";
                        
                        String encoding = "UTF-8";
                        if (mimeType.contains("charset=")) {
                            try {
                                encoding = mimeType.split("charset=")[1].split(";")[0].trim();
                            } catch (Exception ignored) {}
                        }

                        return new WebResourceResponse(mimeType.split(";")[0].trim(), encoding, inputStream);
                    } catch (Exception e) {}
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }
                uploadMessage = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE);
                } catch (Exception e) {
                    uploadMessage = null;
                    return false;
                }
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (uploadMessage == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && intent != null) {
                String dataString = intent.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
            uploadMessage.onReceiveValue(results);
            uploadMessage = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
