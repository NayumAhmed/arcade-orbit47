package com.nayum.arcadeorbit;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.webkit.JavascriptInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.content.pm.PackageManager;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;

public class MainActivity extends Activity {
    private WebView webView;
    private TextView errorView;
    private static final String HOME = "https://nayumahmed.github.io/arcade-orbit47/";
    private static final String LIVE_TV = "https://nayumahmed.github.io/arcade-orbit47/live-tv.html";
    private long backPressedAt = 0L;
    private static final long EXIT_BACK_WINDOW_MS = 2000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use immersive mode on TV devices; keep normal system bars on phones.
        boolean hasTouchscreen = getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
        if (!hasTouchscreen) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16, 19, 29));
        webView = new WebView(this);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.addJavascriptInterface(new OrientationBridge(), "ArcadeOrbit");
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                String host = request.getUrl().getHost();
                // Keep navigation inside the Arcade Orbit site. Broadcaster stream URLs load as media
                // inside live-tv.html; ordinary links to other sites are opened in the WebView only
                // when needed for the live player and browser navigation.
                return !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme));
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                errorView.setVisibility(View.GONE);
                webView.requestFocus();
                // Make keyboard/controller focus easier to see on TV pages.
                view.evaluateJavascript(
                    "(function(){var s=document.createElement('style');s.textContent=" +
                    "'a:focus,button:focus,input:focus,[tabindex]:focus{outline:3px solid #c5ff55!important;outline-offset:3px!important} body{scroll-behavior:auto!important}';document.head.appendChild(s);})();",
                    null);
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) errorView.setVisibility(View.VISIBLE);
            }
        });
        errorView = new TextView(this);
        errorView.setText("ARCADE ORBIT\n\nCould not load the website. Check your Wi-Fi and press OK to retry.");
        errorView.setTextColor(Color.WHITE);
        errorView.setTextSize(22);
        errorView.setGravity(android.view.Gravity.CENTER);
        errorView.setFocusable(true);
        errorView.setOnClickListener(v -> webView.reload());
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(errorView, new FrameLayout.LayoutParams(-1, -1));
        errorView.setVisibility(View.GONE);
        setContentView(root);
        webView.loadUrl(HOME);
    }

    private final class OrientationBridge {
        @JavascriptInterface
        public void setOrientation(String mode) {
            runOnUiThread(() -> {
                if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                    if ("landscape".equalsIgnoreCase(mode)) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                    }
                }
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Use Back for browser history first. At home, require a second
            // Back press within two seconds to exit, like other TV apps.
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                backPressedAt = 0L;
            } else {
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - backPressedAt < EXIT_BACK_WINDOW_MS) {
                    finish();
                } else {
                    backPressedAt = now;
                    Toast.makeText(this, "Press BACK again to exit Arcade Orbit", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
