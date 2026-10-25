package com.nayum.hindilivetv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.TrackSelectionDialogBuilder;
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
    private static final String ALL_CHANNELS_PLAYLIST = "https://iptv-org.github.io/iptv/index.m3u";
    private static final String INDIA_PLAYLIST = "https://iptv-org.github.io/iptv/countries/in.m3u";
    private static final String HINDI_PLAYLIST = "https://iptv-org.github.io/iptv/languages/hin.m3u";
    private static final int BG = Color.rgb(12, 18, 38);
    private static final int PANEL = Color.rgb(31, 38, 66);
    private static final int LIME = Color.rgb(255, 255, 255);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(170, 177, 195);

    private final List<Channel> channels = new ArrayList<>();
    private final Set<String> failedStreamUrls = new HashSet<>();
    private final Set<String> favorites = new HashSet<>();
    private android.content.SharedPreferences prefs;
    private boolean showingFavorites = false;
    private final List<String> categories = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService logoExecutor = Executors.newFixedThreadPool(3);
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
    private DefaultTrackSelector trackSelector;
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
        prefs = getSharedPreferences("hindi_tv_prefs", MODE_PRIVATE);
        favorites.addAll(prefs.getStringSet("favorites", new HashSet<>()));
        buildHome();
        loadCachedChannels();
        loadPlaylists();
    }

    private void buildHome() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(gradient(new int[]{Color.rgb(10, 29, 48), Color.rgb(18, 48, 67), Color.rgb(18, 61, 72)}, 0));
        root.setPadding(dp(28), dp(18), dp(28), dp(16));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        ImageView mark = new ImageView(this);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.setImageBitmap(loadBrandLogo());
        mark.setContentDescription("Nayum Ahmed logo");
        LinearLayout.LayoutParams markP = new LinearLayout.LayoutParams(dp(58), dp(58));
        markP.rightMargin = dp(12);
        header.addView(mark, markP);
        TextView logo = text("N LIVE TV", 27, WHITE, true);
        header.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = button("↻  Refresh", false);
        refresh.setTextColor(Color.rgb(15, 39, 54));
        refresh.setBackground(round(0xE6EAF6FA, 12, 0xFFFFFFFF, 1));
        refresh.setMinHeight(dp(44));
        refresh.setOnFocusChangeListener((v, focused) -> {
            refresh.setBackground(round(focused ? Color.WHITE : 0xE6EAF6FA, 12, 0xFFFFFFFF, focused ? 3 : 1));
            refresh.setTextColor(Color.rgb(15, 39, 54));
        });
        refresh.setOnClickListener(v -> loadPlaylists());
        header.addView(refresh);
        header.setPadding(dp(14), dp(10), dp(12), dp(10));
        header.setBackground(glassPanel(18));
        root.addView(header);

        TextView subtitle = text("Developed by Nayum Ahmed  ·  Live TV for Fire TV", 15, WHITE, true);
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
        search.setBackground(glassPanel(14));
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(50)));
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
        Collections.addAll(categories, "All Channels", "♥ My List", "Hindi News", "Entertainment", "Documentary", "Sports", "Kids", "Hindi Music", "Devotional", "Bhojpuri", "Other");
        for (String category : categories) {
            Button b = button(category, category.equals(selectedCategory));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(40));
            p.rightMargin = dp(8);
            categoryRow.addView(b, p);
            b.setOnClickListener(v -> {
                showingFavorites = category.equals("♥ My List");
                selectedCategory = showingFavorites ? "All Channels" : category;
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

    private void loadCachedChannels() {
        String saved = prefs.getString("channel_cache", "");
        if (!saved.isEmpty()) {
            List<Channel> cached = parsePlaylist(saved);
            if (!cached.isEmpty()) {
                channels.clear(); channels.addAll(limitAndDedupe(cached)); renderChannels();
                status.setText("Saved guide ready · refreshing quietly");
            }
        }
    }

    private List<Channel> limitAndDedupe(List<Channel> input) {
        Set<String> seen = new HashSet<>(); List<Channel> out = new ArrayList<>();
        for (Channel ch : input) {
            String key = ch.name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (!ch.url.isEmpty() && seen.add(key)) out.add(ch);
        }
        out.sort(Comparator.comparing((Channel ch) -> ch.category).thenComparing(ch -> ch.name.toLowerCase(Locale.ROOT)));
        return out.size() > 180 ? new ArrayList<>(out.subList(0, 180)) : out;
    }

    private void loadPlaylists() {
        status.setText("Updating guide in background…");
        executor.execute(() -> {
            List<Channel> loaded = new ArrayList<>();
            boolean ok = false;
            try { loaded.addAll(parsePlaylist(fetch(INDIA_PLAYLIST))); ok = !loaded.isEmpty(); } catch (Exception ignored) { }
            if (loaded.isEmpty()) {
                try { loaded.addAll(parsePlaylist(fetch(HINDI_PLAYLIST))); ok = !loaded.isEmpty(); } catch (Exception ignored) { }
            }
            loaded.addAll(fallbackChannels());
            List<Channel> unique = limitAndDedupe(loaded);
            StringBuilder saved = new StringBuilder();
            for (Channel ch : unique) saved.append("#EXTINF:-1 tvg-logo=\\\"").append(ch.logo.replace("\\\"","")).append("\\\",").append(ch.name.replace("\\n", " ")).append("\\n").append(ch.url).append("\\n");
            prefs.edit().putString("channel_cache", saved.toString()).apply();
            final boolean networkOk = ok; final List<Channel> result = unique;
            main.post(() -> {
                channels.clear(); channels.addAll(result); failedStreamUrls.clear(); renderChannels();
                status.setText(networkOk ? "Guide ready · " + result.size() + " channels" : "Using backup guide · check connection");
            });
        });
    }

    private String fetch(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(6500);
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
        String pendingName = null, pendingLogo = "";
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                pendingName = comma >= 0 ? line.substring(comma + 1).trim() : "Live channel";
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("tvg-logo=\\\"([^\\\"]*)\\\"").matcher(line);
                pendingLogo = m.find() ? m.group(1) : "";
            } else if (pendingName != null && (line.startsWith("https://") || line.startsWith("http://"))) {
                if (!pendingName.trim().isEmpty()) parsed.add(new Channel(pendingName, line, categoryFor(pendingName), pendingLogo));
                pendingName = null; pendingLogo = "";
            }
        }
        return parsed;
    }

    private boolean isRelevant(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        // Keep a wider selection of Indian channels, including entertainment and documentary channels.
        return n.matches(".*(india|indian|hindi|aaj tak|abp|ndtv|news18|tv9|zee news|republic|news nation|good news today|dd national|dd india|dd bharati|manoranjan|anjan tv|b4u|9xm|9x jalwa|9x tashan|aastha|sanskar|satsang|bhojpuri|bharat express|bhajan|sony sab|sony entertainment|sony yay|history tv18|history tv|colors|star plus|star bharat|zee tv|&tv|zee anmol|sun tv|vijay tv|gemini tv|maha movie|colors cineplex|discovery|animal planet|nat geo|national geographic|travelxp|foodxp|sports|cricket|cartoon|kids|music|public tv|raj news|pitaara|news).*");
    }

    private String categoryFor(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.matches(".*(aastha|sanskar|satsang|bhajan|adhyatm).*")) return "Devotional";
        if (n.contains("bhojpuri")) return "Bhojpuri";
        if (n.matches(".*(9x|music|jalwa|tashan).*")) return "Hindi Music";
        if (n.matches(".*(history|discovery|animal planet|nat geo|national geographic|documentary|travelxp|foodxp).*")) return "Documentary";
        if (n.matches(".*(cartoon|kids|sony yay|nick|hungama).*")) return "Kids";
        if (n.matches(".*(sports|cricket|ten sports|star sports).*")) return "Sports";
        if (n.matches(".*(news|aaj tak|abp|ndtv|bharatvarsh|india tv|republic).*")) return "Hindi News";
        if (n.matches(".*(sony sab|sony entertainment|colors|star plus|star bharat|zee tv|&tv|zee anmol|dd national|dd india|dd bharati|manoranjan|anjan|b4u movie|sun tv|vijay tv|gemini tv).*")) return "Entertainment";
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
        for (Channel ch : channels) {
            boolean favOk = !showingFavorites || favorites.contains(ch.url);
            boolean categoryOk = showingFavorites || selectedCategory.equals("All Channels") || ch.category.equals(selectedCategory);
            boolean searchOk = searchQuery.isEmpty() || (ch.name + " " + ch.category).toLowerCase(Locale.ROOT).contains(searchQuery);
            if (favOk && categoryOk && searchOk) visible.add(ch);
        }
        count.setText(visible.size() + " CHANNELS");
        if (visible.isEmpty()) {
            TextView empty = text(showingFavorites ? "Your list is empty. Tap + on any channel to save it here." : "No channels found. Try another category or refresh.", 17, MUTED, false);
            empty.setPadding(dp(16), dp(24), dp(16), dp(24)); channelList.addView(empty); return;
        }
        int columns = 4;
        for (int i=0;i<visible.size();i+=columns) {
            LinearLayout row = new LinearLayout(this);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1,-2); rp.bottomMargin=dp(12); channelList.addView(row,rp);
            for (int j=i;j<Math.min(i+columns,visible.size());j++) {
                Channel ch=visible.get(j);
                LinearLayout tile=new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(8),dp(8),dp(8),dp(8)); tile.setBackground(glassPanel(16)); tile.setFocusable(true); tile.setFocusableInTouchMode(false); tile.setClickable(true); tile.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                ImageView logo = new ImageView(this); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); logo.setBackground(round(Color.rgb(34,37,54),12,0,0));
                TextView fallback=text(iconFor(ch.category),28,WHITE,true); fallback.setGravity(Gravity.CENTER);
                logo.setBackground(gradient(new int[]{0xAA8BD7E8, 0xAA5D91B2, 0xAA9DC7D5}, 12));
                logo.setImageDrawable(round(Color.rgb(34,37,54),12,0,0));
                if (!ch.logo.isEmpty()) loadChannelLogo(ch.logo,logo);
                tile.addView(logo,new LinearLayout.LayoutParams(-1,dp(72)));
                TextView name=text(ch.name,14,WHITE,true); name.setGravity(Gravity.CENTER); name.setMaxLines(2);
                LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(40)); np.topMargin=dp(7); tile.addView(name,np);
                LinearLayout footer=new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL);
                footer.addView(text(ch.category,10,MUTED,true),new LinearLayout.LayoutParams(-1,dp(20)));
                tile.addView(footer);
                tile.setOnClickListener(v->startChannel(ch));
                tile.setOnFocusChangeListener((v,focused)->{ tile.setBackground(glassPanelFocused(focused)); name.setTextColor(focused ? Color.WHITE : WHITE); });
                LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1); tp.leftMargin=dp(4); tp.rightMargin=dp(4); row.addView(tile,tp);
            }
            for(int j=row.getChildCount();j<columns;j++) row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(210),1));
        }
    }

    private void loadChannelLogo(String address, ImageView target) {
        target.setTag(address);
        logoExecutor.execute(() -> {
            HttpURLConnection conn=null;
            try {
                conn=(HttpURLConnection)new URL(address).openConnection();
                conn.setConnectTimeout(2200); conn.setReadTimeout(2200);
                try(java.io.InputStream in=conn.getInputStream()) {
                    Bitmap bmp=BitmapFactory.decodeStream(in);
                    if(bmp!=null) main.post(()->{ if(address.equals(target.getTag())) target.setImageBitmap(bmp); });
                }
            } catch(Exception ignored) { } finally { if(conn!=null) conn.disconnect(); }
        });
    }

    private void startChannel(Channel channel) {
        int index = channels.indexOf(channel);
        if (index < 0) index = 0;
        currentIndex = index;
        showPlayer(channel);
    }

    private void showPlayer(Channel channel) {
        playerVisible = true;
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if (player != null) { player.release(); player = null; }

        // Larger live buffers help absorb short Wi-Fi/network fluctuations.
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 50000, 1500, 5000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        trackSelector = new DefaultTrackSelector(this);
        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setTrackSelector(trackSelector)
                .build();

        FrameLayout playerRoot = new FrameLayout(this);
        playerRoot.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(true);
        playerView.setControllerShowTimeoutMs(10000);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setKeepContentOnPlayerReset(true);
        playerView.setPlayer(player);
        playerRoot.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout settingsBar = new LinearLayout(this);
        settingsBar.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        settingsBar.setPadding(dp(8), dp(6), dp(12), dp(6));
        settingsBar.setBackgroundColor(0x66000000);

        Button favorite = button(favoriteButtonText(channel), false);
        favorite.setText(favorites.contains(channel.url) ? "♥ Saved" : "＋ My List");
        favorite.setTextSize(12);
        favorite.setMinHeight(dp(36));
        favorite.setPadding(dp(8), 0, dp(8), 0);
        favorite.setBackground(round(0xEE102D55, 10, 0xFF8ED8FF, 1));
        favorite.setOnFocusChangeListener((v, focused) -> {
            favorite.setBackground(round(focused ? 0xFF1C5480 : 0xEE102D55, 10,
                    focused ? Color.WHITE : 0xFF8ED8FF, focused ? 2 : 1));
            favorite.setTextColor(Color.WHITE);
        });
        favorite.setOnClickListener(v -> {
            boolean adding = !favorites.contains(channel.url);
            if (adding) favorites.add(channel.url); else favorites.remove(channel.url);
            prefs.edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
            favorite.setText(adding ? "♥ Saved" : "＋ My List");
            Toast.makeText(MainActivity.this,
                    adding ? channel.name + " added to My List" : channel.name + " removed from My List",
                    Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-2, dp(36));
        fp.rightMargin = dp(6);
        settingsBar.addView(favorite, fp);

        Button audio = button("♫ Audio", false);
        audio.setOnClickListener(v -> showTrackSettings(C.TRACK_TYPE_AUDIO, "Audio language"));
        Button quality = button("⚙ Quality", false);
        quality.setOnClickListener(v -> showTrackSettings(C.TRACK_TYPE_VIDEO, "Video quality"));
        Button subtitles = button("CC Subtitles", false);
        subtitles.setOnClickListener(v -> showTrackSettings(C.TRACK_TYPE_TEXT, "Subtitles"));
        for (Button b : new Button[]{audio, quality, subtitles}) {
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, dp(36));
            bp.leftMargin = dp(4);
            settingsBar.addView(b, bp);
        }
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.RIGHT);
        playerRoot.addView(settingsBar, settingsParams);
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) (visibility) -> {
            settingsBar.setVisibility(visibility == View.VISIBLE ? View.VISIBLE : View.GONE);
        });
        setContentView(playerRoot);

        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                // Don't jump to an unrelated channel automatically; tell the viewer this stream failed.
                Toast.makeText(MainActivity.this,
                        "This stream stalled or is unavailable. Try another channel or check your connection.",
                        Toast.LENGTH_LONG).show();
            }
        });
        player.setMediaItem(MediaItem.fromUri(channel.url));
        player.prepare();
        player.play();
        playerView.setFocusable(true);
        playerView.requestFocus();
    }

    private String favoriteButtonText(Channel channel) {
        return favorites.contains(channel.url) ? "♥  Remove from My Favorite List" : "＋  Add to My Favorite List";
    }

    private void showTrackSettings(int trackType, String title) {
        if (trackSelector == null || player == null) return;
        try {
            new TrackSelectionDialogBuilder(this, title, player, trackType)
                    .setShowDisableOption(trackType == C.TRACK_TYPE_TEXT)
                    .build().show();
        } catch (Exception e) {
            Toast.makeText(this, "This channel does not expose selectable " +
                    title.toLowerCase(Locale.ROOT) + " tracks.", Toast.LENGTH_LONG).show();
        }
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
                    Toast.makeText(this, "Press BACK again to exit N Live TV", Toast.LENGTH_SHORT).show();
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

    private GradientDrawable gradient(int[] colors, int radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private Bitmap loadBrandLogo() {
        try (java.io.InputStream in = getResources().openRawResource(R.raw.nayum_logo);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            byte[] image = android.util.Base64.decode(out.toString("UTF-8"), android.util.Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(image, 0, image.length);
        } catch (Exception e) {
            return null;
        }
    }

    private GradientDrawable glassPanel(int radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xC93D6478, 0xB52D5268, 0xC93D6977});
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), 0xA8D9F2FA);
        return d;
    }

    private GradientDrawable glassPanelFocused(boolean focused) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                focused ? new int[]{0xF05D9BB0, 0xF05B8FA7, 0xF077A9B4}
                        : new int[]{0xC93D6478, 0xB52D5268, 0xC93D6977});
        d.setCornerRadius(dp(16));
        d.setStroke(dp(focused ? 3 : 1), focused ? Color.WHITE : 0xA8D9F2FA);
        return d;
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
        logoExecutor.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private static final class Channel {
        final String name;
        final String url;
        final String category;
        final String logo;
        Channel(String name, String url, String category) { this(name, url, category, ""); }
        Channel(String name, String url, String category, String logo) {
            this.name = name == null || name.trim().isEmpty() ? "Live channel" : name.trim();
            this.url = url == null ? "" : url.trim();
            this.category = category == null ? "Other" : category;
            this.logo = logo == null ? "" : logo;
        }
    }
}
