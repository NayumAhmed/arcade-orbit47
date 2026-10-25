package com.nayum.hindilivetv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String INDIA_PLAYLIST = "https://iptv-org.github.io/iptv/streams/in.m3u";
    private static final String HINDI_PLAYLIST = "https://iptv-org.github.io/iptv/languages/hin.m3u";
    private static final int BG = Color.rgb(7, 17, 31);
    private static final int PANEL = Color.rgb(17, 39, 61);
    private static final int LIME = Color.rgb(185, 255, 92);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(190, 207, 224);

    private final List<Channel> channels = new ArrayList<>();
    private final Set<String> failedStreamUrls = new HashSet<>();
    private final List<String> categories = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private LinearLayout channelList;
    private TextView status;
    private TextView count;
    private EditText search;
    private String selectedCategory = "All Channels";
    private String searchQuery = "";
    private boolean playerVisible = false;
    private long lastBackAt = 0L;
    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout playerScreen;
    private int currentIndex = -1;
    private Button prevButton;
    private Button nextButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        buildHome();
        loadPlaylists();
    }

    private void buildHome() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(28), dp(18), dp(28), dp(16));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView logo = text("◉  HINDI LIVE TV", 26, LIME, true);
        header.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = button("↻  Refresh", true);
        refresh.setOnClickListener(v -> loadPlaylists());
        header.addView(refresh);
        root.addView(header);

        TextView subtitle = text("Indian news · entertainment · music · devotional", 15, MUTED, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(6);
        subParams.bottomMargin = dp(14);
        root.addView(subtitle, subParams);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(WHITE);
        search.setHintTextColor(MUTED);
        search.setHint("Search channels");
        search.setTextSize(17);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(15), dp(9), dp(15), dp(9));
        search.setBackground(round(PANEL, 12, 0xFF40576D, 1));
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(48)));
        search.setOnEditorActionListener((v, actionId, event) -> {
            searchQuery = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            renderChannels();
            return false;
        });
        search.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                searchQuery = search.getText().toString().trim().toLowerCase(Locale.ROOT);
                renderChannels();
            }
        });

        HorizontalScrollView categoryScroll = new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        categoryScroll.setVerticalScrollBarEnabled(false);
        categoryScroll.setVerticalScrollBarEnabled(false);
        categoryScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(56)));
        LinearLayout categoryRow = new LinearLayout(this);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        categoryRow.setGravity(Gravity.CENTER_VERTICAL);
        categoryScroll.addView(categoryRow);
        root.addView(categoryScroll);
        categories.clear();
        Collections.addAll(categories, "All Channels", "Hindi News", "Entertainment", "Hindi Music", "Devotional", "Bhojpuri", "Other");
        for (String category : categories) {
            Button b = button(category, category.equals(selectedCategory));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(40));
            p.rightMargin = dp(8);
            categoryRow.addView(b, p);
            b.setOnClickListener(v -> {
                selectedCategory = category;
                for (int i = 0; i < categoryRow.getChildCount(); i++) {
                    View child = categoryRow.getChildAt(i);
                    if (child instanceof Button) {
                        Button cb = (Button) child;
                        cb.setBackground(round(cb.getText().toString().equals(selectedCategory) ? LIME : PANEL, 10,
                                cb.getText().toString().equals(selectedCategory) ? LIME : 0xFF40576D, 1));
                        cb.setTextColor(cb.getText().toString().equals(selectedCategory) ? BG : WHITE);
                    }
                }
                renderChannels();
            });
        }

        LinearLayout shelf = new LinearLayout(this);
        shelf.setGravity(Gravity.CENTER_VERTICAL);
        shelf.setOrientation(LinearLayout.HORIZONTAL);
        TextView heading = text("Available channels", 20, WHITE, true);
        shelf.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        count = text("Loading…", 13, LIME, true);
        shelf.addView(count);
        LinearLayout.LayoutParams shelfP = new LinearLayout.LayoutParams(-1, -2);
        shelfP.topMargin = dp(8);
        shelfP.bottomMargin = dp(8);
        root.addView(shelf, shelfP);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        channelList = new LinearLayout(this);
        channelList.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(channelList);
        root.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));

        status = text("Loading public playlists…", 12, MUTED, false);
        LinearLayout.LayoutParams statusP = new LinearLayout.LayoutParams(-1, -2);
        statusP.topMargin = dp(7);
        root.addView(status, statusP);
        setContentView(root);
    }

    private void loadPlaylists() {
        status.setText("Refreshing channel list…");
        executor.execute(() -> {
            List<Channel> loaded = new ArrayList<>();
            String error = null;
            try {
                loaded.addAll(parsePlaylist(fetch(INDIA_PLAYLIST)));
                try { loaded.addAll(parsePlaylist(fetch(HINDI_PLAYLIST))); } catch (Exception ignored) { }
                // Keep backup streams available even when a playlist loads successfully.
                loaded.addAll(fallbackChannels());
            } catch (Exception e) {
                error = e.getMessage() == null ? "Network unavailable" : e.getMessage();
            }
            if (loaded.isEmpty()) loaded.addAll(fallbackChannels());
            Set<String> seen = new HashSet<>();
            List<Channel> unique = new ArrayList<>();
            for (Channel c : loaded) {
                String key = c.name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
                if (!c.url.isEmpty() && seen.add(key)) unique.add(c);
            }
            unique.sort(Comparator.comparing((Channel c) -> c.category).thenComparing(c -> c.name.toLowerCase(Locale.ROOT)));
            final List<Channel> result = unique;
            final String message = error;
            main.post(() -> {
                channels.clear();
                channels.addAll(result);
                failedStreamUrls.clear();
                renderChannels();
                status.setText((message == null ? "Playlist refreshed" : "Offline fallback loaded")
                        + " · Public streams may be unavailable or region-restricted");
            });
        });
    }

    private String fetch(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "HindiLiveTV/1.0 AndroidTV");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("Playlist HTTP " + code);
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line).append('\n');
            }
            return body.toString();
        } finally { connection.disconnect(); }
    }

    private List<Channel> parsePlaylist(String playlist) {
        List<Channel> parsed = new ArrayList<>();
        String[] lines = playlist.split("\\r?\\n");
        String pendingName = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                pendingName = comma >= 0 ? line.substring(comma + 1).trim() : "Live channel";
            } else if (pendingName != null && (line.startsWith("https://") || line.startsWith("http://"))) {
                if (isRelevant(pendingName)) parsed.add(new Channel(pendingName, line, categoryFor(pendingName)));
                pendingName = null;
            }
        }
        return parsed;
    }

    private boolean isRelevant(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.matches(".*(hindi|aaj tak|abp news|ndtv india|india tv|news18 india|tv9 bharatvarsh|zee news|republic bharat|news nation|good news today|dd national|manoranjan|anjan tv|b4u|9xm|9x jalwa|9x tashan|aastha|sanskar|satsang|bhojpuri|hindi khabar|bharat express|bhajan).*");
    }

    private String categoryFor(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.matches(".*(aastha|sanskar|satsang|bhajan|adhyatm).*")) return "Devotional";
        if (n.contains("bhojpuri")) return "Bhojpuri";
        if (n.matches(".*(9x|music|jalwa|tashan).*")) return "Hindi Music";
        if (n.matches(".*(news|aaj tak|abp|ndtv|bharatvarsh|india tv|republic).*")) return "Hindi News";
        if (n.matches(".*(dd national|manoranjan|anjan|b4u movie|&tv).*")) return "Entertainment";
        return "Other";
    }

    private List<Channel> fallbackChannels() {
        List<Channel> list = new ArrayList<>();
        list.add(new Channel("Aaj Tak", "https://feeds.intoday.in/aajtak/api/master.m3u8", "Hindi News"));
        list.add(new Channel("ABP News", "https://d2l4ar6y3mrs4k.cloudfront.net/live-streaming/abpnews-livetv/master.m3u8", "Hindi News"));
        list.add(new Channel("NDTV India", "https://ndtvindiaelemarchana.akamaized.net/hls/live/2003679/ndtvindia/master.m3u8", "Hindi News"));
        list.add(new Channel("India TV", "https://pl-indiatvnews.akamaized.net/out/v1/db79179b608641ceaa5a4d0dd0dca8da/index.m3u8", "Hindi News"));
        list.add(new Channel("TV9 Bharatvarsh", "https://dyjmyiv3bp2ez.cloudfront.net/pub-iotv9hinjzgtpe/liveabr/playlist.m3u8", "Hindi News"));
        list.add(new Channel("DD National HD", "https://streams.tangotv.in/DDNATIONALHD/ORIGIN/index.m3u8", "Entertainment"));
        list.add(new Channel("Manoranjan TV", "https://streams.tangotv.in/MANORANJANTV/ORIGIN/index.m3u8", "Entertainment"));
        list.add(new Channel("9XM", "https://9xjio.wiseplayout.com/9XM/master.m3u8", "Hindi Music"));
        list.add(new Channel("Aastha", "https://aasthaott.akamaized.net/110923/smil:aasthatv.smil/index.m3u8", "Devotional"));
        list.add(new Channel("Sanskar TV", "https://d26idhjf0y1p2g.cloudfront.net/out/v1/cd66dd25b9774cb29943bab54bbf3e2f/index.m3u8", "Devotional"));
        return list;
    }

    private void renderChannels() {
        if (channelList == null || count == null) return;
        channelList.removeAllViews();
        List<Channel> visible = new ArrayList<>();
        for (Channel c : channels) {
            boolean categoryMatch = selectedCategory.equals("All Channels") || c.category.equals(selectedCategory);
            boolean textMatch = searchQuery.isEmpty() || (c.name + " " + c.category).toLowerCase(Locale.ROOT).contains(searchQuery);
            if (categoryMatch && textMatch) visible.add(c);
        }
        count.setText(visible.size() + " CHANNELS");
        if (visible.isEmpty()) {
            TextView empty = text("No matching channels. Change category, search, or refresh.", 17, MUTED, false);
            empty.setPadding(dp(16), dp(20), dp(16), dp(20));
            channelList.addView(empty);
            return;
        }
        int columns = 4;
        for (int i = 0; i < visible.size(); i += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowP = new LinearLayout.LayoutParams(-1, -2);
            rowP.bottomMargin = dp(10);
            channelList.addView(row, rowP);
            for (int j = i; j < Math.min(i + columns, visible.size()); j++) {
                Channel channel = visible.get(j);
                LinearLayout tile = new LinearLayout(this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(10), dp(12), dp(10), dp(12));
                tile.setBackground(round(PANEL, 14, 0xFF40576D, 1));
                tile.setFocusable(true);
                tile.setFocusableInTouchMode(false);
                tile.setClickable(true);
                tile.setContentDescription("Play " + channel.name);
                TextView icon = text(iconFor(channel.category), 28, LIME, true);
                icon.setGravity(Gravity.CENTER);
                tile.addView(icon, new LinearLayout.LayoutParams(-1, dp(38)));
                TextView name = text(channel.name, 15, WHITE, true);
                name.setGravity(Gravity.CENTER);
                name.setMaxLines(2);
                LinearLayout.LayoutParams nameP = new LinearLayout.LayoutParams(-1, dp(42));
                nameP.topMargin = dp(5);
                tile.addView(name, nameP);
                TextView cat = text(channel.category.toUpperCase(Locale.ROOT), 10, LIME, true);
                cat.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams catP = new LinearLayout.LayoutParams(-1, dp(20));
                tile.addView(cat, catP);
                tile.setOnClickListener(v -> startChannel(channel));
                tile.setOnFocusChangeListener((v, focused) -> {
                    tile.setBackground(round(focused ? 0xFF234564 : PANEL, 14, focused ? LIME : 0xFF40576D, focused ? 3 : 1));
                });
                LinearLayout.LayoutParams tileP = new LinearLayout.LayoutParams(0, dp(132), 1);
                tileP.leftMargin = dp(4);
                tileP.rightMargin = dp(4);
                row.addView(tile, tileP);
            }
            for (int j = row.getChildCount(); j < columns; j++) {
                View spacer = new View(this);
                row.addView(spacer, new LinearLayout.LayoutParams(0, dp(132), 1));
            }
        }
    }

    private void startChannel(Channel channel) {
        int index = channels.indexOf(channel);
        if (index < 0) index = 0;
        currentIndex = index;
        showPlayer(channel);
    }

    private void showPlayer(Channel channel) {
        playerVisible = true;
        if (player != null) { player.release(); player = null; }
        playerScreen = new LinearLayout(this);
        playerScreen.setOrientation(LinearLayout.VERTICAL);
        playerScreen.setBackgroundColor(Color.BLACK);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(16), dp(8), dp(16), dp(8));
        controls.setBackgroundColor(0xFF102235);
        TextView title = text(channel.name, 19, WHITE, true);
        controls.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        prevButton = button("◀ Previous", false);
        nextButton = button("Next ▶", false);
        Button close = button("✕  Close / Back", false);
        controls.addView(prevButton);
        controls.addView(nextButton);
        controls.addView(close);
        playerScreen.addView(controls, new LinearLayout.LayoutParams(-1, dp(60)));
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setBackgroundColor(Color.BLACK);
        playerScreen.addView(playerView, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView help = text("Community playlist · some broadcasters may block external playback", 12, MUTED, false);
        help.setPadding(dp(16), dp(6), dp(16), dp(6));
        playerScreen.addView(help);
        setContentView(playerScreen);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                failedStreamUrls.add(channel.url);
                int failedIndex = channels.indexOf(channel);
                int candidateIndex = -1;
                for (int step = 1; step <= channels.size(); step++) {
                    int candidate = (Math.max(failedIndex, 0) + step) % channels.size();
                    Channel next = channels.get(candidate);
                    boolean categoryMatch = selectedCategory.equals("All Channels") || next.category.equals(selectedCategory);
                    if (categoryMatch && !failedStreamUrls.contains(next.url)) { candidateIndex = candidate; break; }
                }
                if (candidateIndex >= 0) {
                    final int nextIndex = candidateIndex;
                    Toast.makeText(MainActivity.this, "Stream unavailable. Trying " + channels.get(nextIndex).name + "…", Toast.LENGTH_SHORT).show();
                    main.postDelayed(() -> {
                        if (playerVisible && currentIndex == failedIndex) {
                            currentIndex = nextIndex;
                            showPlayer(channels.get(nextIndex));
                        }
                    }, 900);
                } else {
                    Toast.makeText(MainActivity.this, "No more untried streams in this category. Press Back and choose another channel or refresh.", Toast.LENGTH_LONG).show();
                }
            }
        });
        player.setMediaItem(MediaItem.fromUri(channel.url));
        player.prepare();
        player.play();
        prevButton.setOnClickListener(v -> playRelative(-1));
        nextButton.setOnClickListener(v -> playRelative(1));
        close.setOnClickListener(v -> closePlayer());
        playerView.setFocusable(true);
        playerView.requestFocus();
    }

    private void playRelative(int delta) {
        if (channels.isEmpty()) return;
        int next = currentIndex;
        for (int tries = 0; tries < channels.size(); tries++) {
            next = (next + delta + channels.size()) % channels.size();
            Channel candidate = channels.get(next);
            if (selectedCategory.equals("All Channels") || candidate.category.equals(selectedCategory)) {
                currentIndex = next;
                showPlayer(candidate);
                return;
            }
        }
    }

    private void closePlayer() {
        playerVisible = false;
        if (player != null) { player.stop(); player.release(); player = null; }
        playerView = null;
        playerScreen = null;
        setContentView(root);
        root.requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (playerVisible) {
                closePlayer();
            } else {
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastBackAt < 2000L) finish();
                else {
                    lastBackAt = now;
                    Toast.makeText(this, "Press BACK again to exit Hindi Live TV", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? BG : WHITE);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinHeight(dp(40));
        b.setBackground(round(primary ? LIME : PANEL, 10, primary ? LIME : 0xFF40576D, 1));
        b.setFocusable(true);
        b.setOnFocusChangeListener((v, focused) -> {
            b.setBackground(round(focused ? 0xFF234564 : (primary ? LIME : PANEL), 10, focused ? LIME : (primary ? LIME : 0xFF40576D), focused ? 3 : 1));
            b.setTextColor(focused && !primary ? LIME : (primary ? BG : WHITE));
        });
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String iconFor(String category) {
        switch (category) {
            case "Hindi News": return "📰";
            case "Hindi Music": return "🎵";
            case "Devotional": return "🙏";
            case "Bhojpuri": return "🎬";
            case "Entertainment": return "📺";
            default: return "▶";
        }
    }

    @Override protected void onDestroy() {
        if (player != null) { player.release(); player = null; }
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private static final class Channel {
        final String name;
        final String url;
        final String category;
        Channel(String name, String url, String category) {
            this.name = name == null || name.trim().isEmpty() ? "Live channel" : name.trim();
            this.url = url == null ? "" : url.trim();
            this.category = category == null ? "Other" : category;
        }
    }
}
