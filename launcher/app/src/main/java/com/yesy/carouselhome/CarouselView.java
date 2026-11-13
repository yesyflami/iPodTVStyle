package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Lightweight, allocation-free Cover Flow renderer for low-memory Android TVs. */
public final class CarouselView extends View {
    private static final int CARD = 384;
    private static final int TEXTURE_PAD = 46;
    private static final int TEXTURE = CARD + TEXTURE_PAD * 2;
    private static final int REFL_W = CARD / 2;
    private static final int REFL_H = 68;
    private static final float REFL_SCALE = (float) CARD / REFL_W;
    /** Light lost by the glossy floor: keeps near-white tiles readable as a reflection. */
    private static final float FLOOR_LOSS = .86f;
    private static final float DESIGN_W = 1920f;
    private static final float DESIGN_H = 1080f;
    private static final float HTML_CARD = 442f;
    private static final float RAIL_Y = DESIGN_H * 0.43f;
    private static final float PERSPECTIVE = 1180f;
    private static final long ANIMATION_MS = 420L;
    private static final float[] X_FRACTIONS = {0f, .16f, .242f, .312f, .372f, .418f};
    private static final float[] Y_OFFSETS = {0f, 2f, 4f, 7f, 7f, 8f};
    private static final float[] Z_OFFSETS = {-155f, -47f, -36f, -25f, -14f, -8f};
    private static final float[] ROTATIONS = {0f, 58f, 66f, 72f, 72f, 72f};
    private static final float[] SCALES = {1.03f, .93f, .83f, .73f, .65f, .58f};
    /** Beyond this the slot is empty; the wrap happens while the tile is already invisible. */
    private static final float WRAP_DISTANCE = 5f;

    /** Tiles baked before the first frame; the rest are warmed up after it, in small chunks. */
    private static final int FIRST_FRAME_TILES = 5;
    private static final int WARMUP_CHUNK = 4;
    private static final long WARMUP_DELAY_MS = 300L;
    private static final float LOGO_WIDTH = .54f;

    /**
     * Packages kept out of the ring. This list is empty on purpose: the default is whatever the
     * TV reports. A owner who wants a tile gone writes the package name into
     * <code>&lt;app files dir&gt;/hidden.txt</code> (one per line, <code>#</code> comments), which
     * needs no rebuild - see docs/ADAPT.md §4.
     */
    private static final String[] HIDDEN_PACKAGES = {};
    private static final String HIDDEN_FILE = "hidden.txt";

    /**
     * One tile of the ring. Nothing here is authored by hand: the label, the package and the
     * artwork are all read off the TV the launcher is running on.
     */
    private static final class AppItem {
        final String name;
        final String packageName;
        /**
         * Skyworth source index for HDMI tiles, 0-based (0 = HDMI 1, 1 = HDMI 2, …).
         * -1 means "an ordinary app": launch it by package name instead.
         */
        final int sourceIndex;
        /** Resolved the first time the tile becomes the selected one: hasGlyph is not free. */
        Typeface nameFace;
        Bitmap card;
        Bitmap reflection;
        Bitmap reflectionDark;

        AppItem(String name, String packageName, int sourceIndex) {
            this.name = name;
            this.packageName = packageName;
            this.sourceIndex = sourceIndex;
        }
    }

    private AppItem[] apps;
    private int[] renderOrder;
    private float[] renderPositions;
    private String installedSignature;

    /**
     * Ask the TV which apps it has. LEANBACK entries come first (real TV apps), plain LAUNCHER
     * ones after them, so a sideloaded phone app can never push a TV app off the ring. When a
     * package exposes both, the leanback entry wins. Input/source trampolines go last, so four
     * HDMI tiles cannot interleave with the apps between them.
     */
    private AppItem[] discoverApps() {
        PackageManager manager = getContext().getPackageManager();
        String self = getContext().getPackageName();
        HashSet<String> hidden = hiddenPackages();
        ArrayList<AppItem> tvApps = new ArrayList<>();
        ArrayList<AppItem> otherApps = new ArrayList<>();
        collectApps(manager, self, Intent.CATEGORY_LEANBACK_LAUNCHER, hidden, true, tvApps);
        collectApps(manager, self, Intent.CATEGORY_LAUNCHER, hidden, false, otherApps);

        ArrayList<AppItem> merged = new ArrayList<>(tvApps.size() + otherApps.size());
        ArrayList<AppItem> inputs = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (AppItem app : tvApps) {
            if (seen.add(app.packageName)) (app.sourceIndex >= 0 ? inputs : merged).add(app);
        }
        for (AppItem app : otherApps) {
            if (seen.add(app.packageName)) (app.sourceIndex >= 0 ? inputs : merged).add(app);
        }
        merged.addAll(inputs);
        if (merged.isEmpty()) {
            // Nothing queryable: keep the HOME button useful instead of an empty room.
            merged.add(new AppItem(settingsLabel(manager), "com.android.settings", -1));
        }
        return merged.toArray(new AppItem[0]);
    }

    private void collectApps(PackageManager manager, String self, String category,
                             HashSet<String> hidden, boolean leanback, ArrayList<AppItem> sink) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> resolved;
        try {
            resolved = manager.queryIntentActivities(intent, 0);
        } catch (RuntimeException ignored) {
            return;
        }
        if (resolved == null) return;
        for (ResolveInfo entry : resolved) {
            ActivityInfo activity = entry.activityInfo;
            if (activity == null || activity.packageName == null) continue;
            if (activity.packageName.equals(self)) continue; // the launcher is not in its own ring
            if (hidden.contains(activity.packageName)) continue;
            // A phone-style LAUNCHER entry inside a system namespace is a settings pane or a
            // provisioning app, not something anyone opens from a home screen. A proper TV entry
            // for the same package still gets in, because that pass is not filtered.
            if (!leanback && isSystemPane(activity.packageName)) continue;
            if (!activity.enabled) continue;
            CharSequence raw = entry.loadLabel(manager);
            String label = raw == null ? "" : raw.toString().trim();
            if (label.length() == 0) label = activity.packageName;
            sink.add(new AppItem(label, activity.packageName,
                    hdmiSourceIndex(activity.packageName)));
        }
        final Collator collator = Collator.getInstance(Locale.getDefault());
        sink.sort((first, second) -> collator.compare(first.name, second.name));
    }

    private static boolean isSystemPane(String packageName) {
        return packageName.equals("android")
                || packageName.startsWith("com.android.")
                || packageName.startsWith("com.google.android.");
    }

    /**
     * SkyOS exposes each HDMI input as a trampoline package, <code>com.coocaa.hdmi1</code> …
     * <code>com.coocaa.hdmi4</code>. The provider takes a 0-based index, so the number in the
     * package name is the 1-based input: hdmi2 → index 1. Other TVs return -1 and the tile is
     * launched like any other app.
     */
    private static int hdmiSourceIndex(String packageName) {
        String prefix = "com.coocaa.hdmi";
        if (!packageName.startsWith(prefix)) return -1;
        String tail = packageName.substring(prefix.length());
        if (tail.length() == 0 || tail.length() > 2) return -1;
        for (int index = 0; index < tail.length(); index++) {
            if (!Character.isDigit(tail.charAt(index))) return -1;
        }
        return Integer.parseInt(tail) - 1;
    }

    /** The built-in list plus whatever the owner wrote into &lt;files dir&gt;/hidden.txt. */
    private HashSet<String> hiddenPackages() {
        HashSet<String> hidden = new HashSet<>();
        for (String packageName : HIDDEN_PACKAGES) hidden.add(packageName);
        File file = new File(getContext().getFilesDir(), HIDDEN_FILE);
        if (!file.isFile()) return hidden;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.length() == 0 || trimmed.startsWith("#")) continue;
                hidden.add(trimmed);
            }
        } catch (IOException ignored) {
            // Unreadable list: the built-in one still applies.
        }
        return hidden;
    }

    /** So an edit to hidden.txt is noticed on the next visit to Home, not on the next reboot. */
    private String hiddenFileStamp() {
        File file = new File(getContext().getFilesDir(), HIDDEN_FILE);
        return file.isFile() ? file.lastModified() + ":" + file.length() : "-";
    }

    /** The face for the selected label, resolved once per app and then cached on the tile. */
    private Typeface selectedFace() {
        AppItem app = apps[selected];
        if (app.nameFace == null) app.nameFace = nameFaceFor(app.name);
        return app.nameFace;
    }

    private Typeface nameFaceFor(String label) {
        if (covers(nameSerif, label)) return nameSerif;
        // The bundled subset only carries the reference TV's glyphs: anything else falls back to
        // the system face, so a Chinese app name never comes out as tofu boxes.
        return Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    }

    private static boolean covers(Typeface face, String text) {
        if (face == null || text == null) return false;
        Paint probe = new Paint();
        probe.setTypeface(face);
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint <= 0x20) continue;
            // Paint.hasGlyph only takes a String -- there is no int overload.
            if (!probe.hasGlyph(new String(Character.toChars(codePoint)))) return false;
        }
        return true;
    }

    private static String settingsLabel(PackageManager manager) {
        try {
            ApplicationInfo info = manager.getApplicationInfo("com.android.settings", 0);
            CharSequence label = manager.getApplicationLabel(info);
            if (label != null && label.length() > 0) return label.toString().trim();
        } catch (Exception ignored) {
            // Settings is not exposed on this firmware: fall through to the plain word.
        }
        return "Settings";
    }

    private static String signatureOf(AppItem[] items) {
        StringBuilder builder = new StringBuilder();
        for (AppItem app : items) {
            builder.append(app.packageName).append('\u0001').append(app.name).append('\n');
        }
        return builder.toString();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private static final Path GEAR_PATH = buildGearPath();
    private static final int GEAR_SIZE = 40;
    private static final int GEAR_SUPERSAMPLE = 4;
    private final RectF scratchRect = new RectF();
    private Bitmap gearLight;
    private Bitmap gearDark;
    private final float[] source = {0, 0, TEXTURE, 0, TEXTURE, TEXTURE, 0, TEXTURE};
    private final float[] reflectionSource = {0, 0, REFL_W, 0, REFL_W, REFL_H, 0, REFL_H};
    private final float[] destination = new float[8];
    private final float[] occluder = new float[8];
    private final Locale locale = Locale.getDefault();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    /** Localized order and script: "SUNDAY, NOV 8, 2026" in English, "2026年11月8日星期日" in Chinese. */
    private final SimpleDateFormat dateFormat = new SimpleDateFormat(
            DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMMdEEEE"),
            Locale.getDefault());
    private final SharedPreferences preferences;
    private final Typeface clockFace = loadFace("/system/fonts/Roboto-Black.ttf", "sans-serif-black");
    private final Typeface dateFace = loadFace("/system/fonts/Roboto-Medium.ttf", "sans-serif-medium");
    private Typeface nameSerif;

    private int selected = 0;
    private int headerFocus = -1;
    private boolean dark;
    private float motion;
    private int pendingDirection;
    private ValueAnimator animator;
    private long lastAnimationDrawNanos;
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            invalidate(80, 0, 620, 130);
            postDelayed(this, 30_000L);
        }
    };

    public CarouselView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);
        preferences = context.getSharedPreferences("carousel-home", Context.MODE_PRIVATE);
        dark = preferences.getBoolean("dark", false);
        // Loaded here rather than in a field initialiser: getContext() is still null at that
        // point, which is why the bundled serif face used to silently fall back.
        nameSerif = loadNameFace();
        apps = discoverApps();
        installedSignature = currentSignature(apps);
        renderOrder = new int[apps.length];
        renderPositions = new float[apps.length];
        prepareCards();
        bakeGear();
    }

    /**
     * Bake only what the first frame can show. Baking every tile in the constructor was up to a
     * one-to-two-second white window on a 1 GB television: the ring is already usable with the
     * five tiles around the selection, and everything else is warmed up after that frame.
     */
    private void prepareCards() {
        int half = FIRST_FRAME_TILES / 2;
        for (int offset = -half; offset <= half; offset++) {
            ensureCard(apps[wrap(selected + offset)]);
        }
    }

    /** Bakes the remaining tiles a few at a time, so no single frame pays for the whole ring. */
    private final Runnable warmUp = new Runnable() {
        @Override public void run() {
            int baked = 0;
            for (AppItem app : apps) {
                if (app.card != null) continue;
                ensureCard(app);
                if (++baked >= WARMUP_CHUNK) {
                    postDelayed(this, 16L);
                    return;
                }
            }
        }
    };

    private void ensureCard(AppItem app) {
        if (app.card != null) return;
        PackageManager manager = getContext().getPackageManager();
        Bitmap icon = renderIcon(manager, app.packageName);
        IconStats stats = analyze(icon);
        bakeCard(app, icon, stats);
    }

    /** Rasterise the app's own icon at card resolution, keeping its proportions. */
    private static Bitmap renderIcon(PackageManager manager, String packageName) {
        Bitmap bitmap = Bitmap.createBitmap(CARD, CARD, Bitmap.Config.ARGB_8888);
        try {
            Drawable drawable = manager.getApplicationIcon(packageName);
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();
            if (width <= 0 || height <= 0) {
                width = CARD;
                height = CARD;
            }
            // Stretching a logo reads as a bug; leaving the plate visible reads as a design.
            float scale = Math.min(CARD / (float) width, CARD / (float) height);
            int drawnWidth = Math.max(1, Math.round(width * scale));
            int drawnHeight = Math.max(1, Math.round(height * scale));
            int left = (CARD - drawnWidth) / 2;
            int top = (CARD - drawnHeight) / 2;
            drawable.setBounds(left, top, left + drawnWidth, top + drawnHeight);
            drawable.draw(new Canvas(bitmap));
            return bitmap;
        } catch (Exception ignored) {
            // Unreadable icon (broken package, missing resource): the label stands in for it.
            bitmap.recycle();
            return null;
        }
    }

    /** What one bake needs to know about an icon, measured in a single pass over its pixels. */
    private static final class IconStats {
        int color;          // plate colour
        Bitmap logo;        // the icon with its own background knocked out, or null
        boolean logoIsGlyph; // true when the background was removed and a glyph is left
    }

    /**
     * Plate colour and icon shape, both read from the app's own artwork instead of the
     * reference screenshot's. A mostly-white icon, a dark glyph or a logo with transparent
     * margins all land on a plate that keeps the logo visible.
     */
    private static IconStats analyze(Bitmap icon) {
        IconStats stats = new IconStats();
        stats.color = 0xfff4f2ee;
        if (icon == null) {
            stats.color = 0xff111111; // dark plate so the fallback label reads
            return stats;
        }

        int[] pixels = new int[CARD * CARD];
        icon.getPixels(pixels, 0, CARD, 0, 0, CARD, CARD);

        // 1 · What colour is the icon's own backdrop? Sample the border ring, which is background
        //     on every well-formed launcher icon.
        int[] ring = borderRing(pixels);
        int backdrop = median(ring);
        int backdropHits = 0;
        int opaque = 0;
        for (int argb : pixels) {
            if ((argb >>> 24) < 24) continue;
            opaque++;
            if (near(argb, backdrop, 46)) backdropHits++;
        }
        float coverage = opaque == 0 ? 0f : backdropHits / (float) opaque;

        // 2 · When the backdrop dominates, the icon is a logo printed on a plate -- exactly the
        //     material the reference ring is made of. Knock it out and keep the plate colour.
        if (coverage >= .38f && luma(backdrop) >= 0) {
            Bitmap glyph = knockOut(pixels, backdrop, 52);
            if (glyph != null) {
                stats.logo = glyph;
                stats.logoIsGlyph = true;
                stats.color = opaquePlate(backdrop, glyph);
                return stats;
            }
        }

        // 3 · No single backdrop: the artwork is the brand. Plate takes the most vivid frequent
        //     colour, not the most frequent one -- a mostly-white icon is common and white is not
        //     a plate you can see.
        int vivid = vividColour(pixels);
        int plate = vivid == 0 ? 0xfff4f2ee : (0xff000000 | vivid);

        // Keep the artwork from disappearing into its own plate.
        long sum = 0;
        int counted = 0;
        for (int argb : pixels) {
            if ((argb >>> 24) < 200) continue;
            sum += luma(argb);
            counted++;
        }
        if (counted > 0) {
            int glyphLuma = (int) (sum / counted);
            if (Math.abs(luma(plate) - glyphLuma) < 60) {
                plate = glyphLuma > 140 ? 0xff1c1c1e : 0xfff4f2ee;
            }
        }
        stats.color = plate;
        return stats;
    }

    /** The four edges of the card, used to guess an icon's own background colour. */
    private static int[] borderRing(int[] pixels) {
        final int edge = Math.max(2, CARD / 24);
        int[] ring = new int[edge * 4 * CARD];
        int at = 0;
        for (int y = 0; y < CARD; y++) {
            for (int d = 0; d < edge; d++) {
                ring[at++] = pixels[y * CARD + d];
                ring[at++] = pixels[y * CARD + (CARD - 1 - d)];
            }
        }
        for (int x = 0; x < CARD; x++) {
            for (int d = 0; d < edge; d++) {
                ring[at++] = pixels[d * CARD + x];
                ring[at++] = pixels[(CARD - 1 - d) * CARD + x];
            }
        }
        return ring;
    }

    private static boolean near(int a, int b, int tolerance) {
        return Math.abs(((a >> 16) & 0xff) - ((b >> 16) & 0xff)) <= tolerance
                && Math.abs(((a >> 8) & 0xff) - ((b >> 8) & 0xff)) <= tolerance
                && Math.abs((a & 0xff) - (b & 0xff)) <= tolerance;
    }

    /** Median of the ring's solid pixels, so a stray antialiased corner cannot set the colour. */
    private static int median(int[] ring) {
        int[] reds = new int[ring.length];
        int[] greens = new int[ring.length];
        int[] blues = new int[ring.length];
        int n = 0;
        for (int argb : ring) {
            if ((argb >>> 24) < 200) continue;
            reds[n] = (argb >> 16) & 0xff;
            greens[n] = (argb >> 8) & 0xff;
            blues[n] = argb & 0xff;
            n++;
        }
        if (n == 0) return 0xffffffff;
        java.util.Arrays.sort(reds, 0, n);
        java.util.Arrays.sort(greens, 0, n);
        java.util.Arrays.sort(blues, 0, n);
        return 0xff000000 | (reds[n / 2] << 16) | (greens[n / 2] << 8) | blues[n / 2];
    }

    /** Everything close to {@code backdrop} becomes transparent; whatever is left is the logo. */
    private static Bitmap knockOut(int[] pixels, int backdrop, int tolerance) {
        int[] out = new int[pixels.length];
        int kept = 0;
        int minX = CARD, minY = CARD, maxX = -1, maxY = -1;
        for (int y = 0; y < CARD; y++) {
            int row = y * CARD;
            for (int x = 0; x < CARD; x++) {
                int argb = pixels[row + x];
                if ((argb >>> 24) < 24 || near(argb, backdrop, tolerance)) {
                    out[row + x] = 0;
                    continue;
                }
                out[row + x] = argb;
                kept++;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        // Too little left (a solid swatch) or too much (the knockout missed) means we could not
        // separate a logo; the caller falls back to using the artwork whole.
        int keptFraction = kept * 100 / (CARD * CARD);
        if (keptFraction < 4 || keptFraction > 62 || maxX < 0) return null;

        keepLargestBlob(out);
        Bitmap glyph = Bitmap.createBitmap(CARD, CARD, Bitmap.Config.ARGB_8888);
        glyph.setPixels(out, 0, CARD, 0, 0, CARD, CARD);
        return glyph;
    }

    /**
     * Drop every blob but the biggest one. Knocking out a background colour leaves crumbs wherever
     * the artwork happens to share it -- a stray wedge of a neighbouring shape, the corner of a
     * rounded plate -- and a single crumb is enough to make a card look broken.
     */
    private static void keepLargestBlob(int[] pixels) {
        final int n = pixels.length;
        int[] label = new int[n];
        java.util.Arrays.fill(label, -1);
        int[] queue = new int[n];
        int bestLabel = -1;
        int bestSize = 0;
        int next = 0;
        for (int start = 0; start < n; start++) {
            if (label[start] != -1 || (pixels[start] >>> 24) == 0) continue;
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            label[start] = next;
            int size = 0;
            while (head < tail) {
                int at = queue[head++];
                size++;
                int x = at % CARD;
                int y = at / CARD;
                if (x > 0) tail = visit(pixels, label, queue, tail, at - 1, next);
                if (x < CARD - 1) tail = visit(pixels, label, queue, tail, at + 1, next);
                if (y > 0) tail = visit(pixels, label, queue, tail, at - CARD, next);
                if (y < CARD - 1) tail = visit(pixels, label, queue, tail, at + CARD, next);
            }
            if (size > bestSize) { bestSize = size; bestLabel = next; }
            next++;
        }
        if (bestLabel < 0) return;
        for (int i = 0; i < n; i++) {
            if ((pixels[i] >>> 24) != 0 && label[i] != bestLabel) pixels[i] = 0;
        }
    }

    private static int visit(int[] pixels, int[] label, int[] queue, int tail, int at, int tag) {
        if ((pixels[at] >>> 24) == 0 || label[at] != -1) return tail;
        label[at] = tag;
        queue[tail] = at;
        return tail + 1;
    }

    /** A plate the knocked-out logo can sit on: the backdrop, pushed away from the logo's luma. */
    private static int opaquePlate(int backdrop, Bitmap glyph) {
        int[] pixels = new int[CARD * CARD];
        glyph.getPixels(pixels, 0, CARD, 0, 0, CARD, CARD);
        long sum = 0;
        int counted = 0;
        for (int argb : pixels) {
            if ((argb >>> 24) < 200) continue;
            sum += luma(argb);
            counted++;
        }
        if (counted == 0) return 0xfff4f2ee;
        int glyphLuma = (int) (sum / counted);
        if (Math.abs(luma(backdrop) - glyphLuma) >= 55) return backdrop;
        // Same brightness: a dark logo gets a light plate and vice versa.
        return glyphLuma > 140 ? 0xfff4f2ee : 0xff1c1c1e;
    }

    /** The most vivid colour that still covers a meaningful share of the artwork. */
    private static int vividColour(int[] pixels) {
        int[] counts = new int[4096];
        int[] reds = new int[4096];
        int[] greens = new int[4096];
        int[] blues = new int[4096];
        for (int argb : pixels) {
            if ((argb >>> 24) < 200) continue;
            int red = (argb >> 16) & 0xff;
            int green = (argb >> 8) & 0xff;
            int blue = argb & 0xff;
            int bucket = ((red >> 4) << 8) | ((green >> 4) << 4) | (blue >> 4);
            counts[bucket]++;
            reds[bucket] += red;
            greens[bucket] += green;
            blues[bucket] += blue;
        }
        int best = 0;
        long bestScore = 0;
        for (int bucket = 0; bucket < 4096; bucket++) {
            int count = counts[bucket];
            if (count < 24) continue;
            int red = reds[bucket] / count;
            int green = greens[bucket] / count;
            int blue = blues[bucket] / count;
            int max = Math.max(red, Math.max(green, blue));
            int min = Math.min(red, Math.min(green, blue));
            int chroma = max - min;
            // A neutral colour is only a last resort: weight it down hard so any tint that is
            // present, even in a small area, wins.
            long score = chroma < 26 ? count / 12L : (long) count * (chroma + 30L);
            if (score > bestScore) {
                bestScore = score;
                best = 0xff000000 | (red << 16) | (green << 8) | blue;
            }
        }
        return best;
    }

    private static int luma(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return (red * 299 + green * 587 + blue * 114) / 1000;
    }

    /** Bake one tile: plate, logo, gloss, shadow, and the two floor reflections. */
    private void bakeCard(AppItem app, Bitmap icon, IconStats stats) {
        int color = stats.color;
        Bitmap face = Bitmap.createBitmap(CARD, CARD, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(face);
        Path roundedCard = new Path();
        roundedCard.addRoundRect(new RectF(0f, 0f, CARD, CARD), 7f, 7f, Path.Direction.CW);
        canvas.clipPath(roundedCard);
        canvas.drawColor(color);
        Paint bakePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Bitmap art = stats != null && stats.logo != null ? stats.logo : icon;
        if (art == null) {
            drawFallbackLabel(canvas, app);
        } else {
            // Album-cover proportions, as in the original design: the logo sits inside the plate
            // rather than replacing it. A knocked-out logo gets a little more room than a full
            // icon, because most of its canvas is now empty.
            float maxWidth = CARD * (stats != null && stats.logoIsGlyph ? LOGO_WIDTH + .26f : LOGO_WIDTH);
            float maxHeight = CARD * .62f;
            float scale = Math.min(maxWidth / art.getWidth(), maxHeight / art.getHeight());
            float width = art.getWidth() * scale;
            float height = art.getHeight() * scale;
            RectF target = new RectF((CARD - width) / 2f, (CARD - height) / 2f,
                    (CARD + width) / 2f, (CARD + height) / 2f);
            canvas.drawBitmap(art, null, target, bakePaint);
        }

        bakePaint.setShader(new LinearGradient(0f, 0f, CARD, CARD,
                new int[] {0x22ffffff, 0x00ffffff, 0x14000000},
                new float[] {0f, .52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, CARD, CARD, bakePaint);
        bakePaint.setShader(null);
        bakePaint.setStyle(Paint.Style.STROKE);
        bakePaint.setStrokeWidth(1f);
        bakePaint.setColor(0x18000000);
        canvas.drawRoundRect(new RectF(.5f, .5f, CARD - .5f, CARD - .5f), 7f, 7f, bakePaint);
        bakePaint.setStyle(Paint.Style.FILL);

        Bitmap card = Bitmap.createBitmap(TEXTURE, TEXTURE, Bitmap.Config.ARGB_8888);
        Canvas textureCanvas = new Canvas(card);
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(color);
        shadow.setShadowLayer(30f, 0f, 15f, 0x66000000);
        RectF faceBounds = new RectF(TEXTURE_PAD, TEXTURE_PAD,
                TEXTURE_PAD + CARD, TEXTURE_PAD + CARD);
        textureCanvas.drawRoundRect(faceBounds, 7f, 7f, shadow);
        shadow.clearShadowLayer();
        bakePaint.setAlpha(255);
        textureCanvas.drawBitmap(face, TEXTURE_PAD, TEXTURE_PAD, bakePaint);

        // The floor patch is baked into the texture: an opaque background inside the quad,
        // so painting the reflection also erases whatever tile was painted before it.
        app.reflection = bakeReflection(face, 0xfffdfdfd);
        app.reflectionDark = bakeReflection(face, Color.BLACK);
        app.card = card;
        // The face only existed to seed the textures: dropping it keeps memory flat, which
        // matters once the ring is as long as the TV's app list.
        face.recycle();
        if (stats != null && stats.logo != null && stats.logo != icon) stats.logo.recycle();
        if (icon != null) icon.recycle();
        paint.setAlpha(255);
        paint.setShader(null);
    }

    /** No readable icon: the label still gives the tile something to say. */
    private void drawFallbackLabel(Canvas canvas, AppItem app) {
        if (app.nameFace == null) app.nameFace = nameFaceFor(app.name);
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(0xfffdfdfd);
        label.setTypeface(app.nameFace);
        label.setTextAlign(Paint.Align.CENTER);
        float size = 56f;
        label.setTextSize(size);
        while (size > 20f && label.measureText(app.name) > CARD * .78f) {
            size -= 2f;
            label.setTextSize(size);
        }
        canvas.drawText(app.name, CARD / 2f, CARD / 2f + size * .35f, label);
    }

    private void recycleCards() {
        for (AppItem app : apps) {
            if (app.card != null) app.card.recycle();
            if (app.reflection != null) app.reflection.recycle();
            if (app.reflectionDark != null) app.reflectionDark.recycle();
            app.card = null;
            app.reflection = null;
            app.reflectionDark = null;
        }
    }

    /** Apps can be installed or removed while we are off screen: rebuild only when they were. */
    public void refreshAppsIfChanged() {
        AppItem[] found = discoverApps();
        String signature = currentSignature(found);
        if (signature.equals(installedSignature)) return;
        recycleCards();
        apps = found;
        installedSignature = signature;
        renderOrder = new int[apps.length];
        renderPositions = new float[apps.length];
        selected = 0;
        motion = 0f;
        pendingDirection = 0;
        prepareCards();
        invalidate();
        removeCallbacks(warmUp);
        postDelayed(warmUp, WARMUP_DELAY_MS);
    }

    /** The installed list plus the hidden.txt stamp, so editing that file also triggers a rebuild. */
    private String currentSignature(AppItem[] items) {
        return signatureOf(items) + '|' + hiddenFileStamp();
    }

    private Bitmap bakeReflection(Bitmap face, int background) {
        Bitmap reflection = Bitmap.createBitmap(REFL_W, REFL_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(reflection);
        matrix.reset();
        matrix.setScale(1f / REFL_SCALE, -1f / REFL_SCALE);
        matrix.postTranslate(0f, CARD / REFL_SCALE);
        paint.setAlpha(255);
        paint.setShader(null);
        paint.setXfermode(null);
        canvas.drawBitmap(face, matrix, paint);
        // Glossy floor: soften the mirror image and let it fade towards the floor colour.
        boxBlur(reflection, 3, 3);
        finishReflection(reflection, background);
        return reflection;
    }

    /** Separable box blur (3 passes approximate a gaussian) on a premultiplied ARGB copy. */
    private static void boxBlur(Bitmap bitmap, int radius, int passes) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int count = width * height;
        int[] pixels = new int[count];
        int[] scratch = new int[count];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < count; i++) {
            int argb = pixels[i];
            int alpha = (argb >>> 24) & 0xff;
            int red = ((argb >> 16) & 0xff) * alpha / 255;
            int green = ((argb >> 8) & 0xff) * alpha / 255;
            int blue = (argb & 0xff) * alpha / 255;
            pixels[i] = (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
        for (int pass = 0; pass < passes; pass++) {
            blurPass(pixels, scratch, width, height, radius, true);
            blurPass(scratch, pixels, width, height, radius, false);
        }
        for (int i = 0; i < count; i++) {
            int argb = pixels[i];
            int alpha = (argb >>> 24) & 0xff;
            if (alpha == 0) {
                pixels[i] = 0;
                continue;
            }
            int red = Math.min(255, ((argb >> 16) & 0xff) * 255 / alpha);
            int green = Math.min(255, ((argb >> 8) & 0xff) * 255 / alpha);
            int blue = Math.min(255, (argb & 0xff) * 255 / alpha);
            pixels[i] = (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private static void blurPass(int[] input, int[] output, int width, int height, int radius,
                                 boolean horizontal) {
        int span = radius * 2 + 1;
        int lines = horizontal ? height : width;
        int length = horizontal ? width : height;
        for (int line = 0; line < lines; line++) {
            int sa = 0;
            int sr = 0;
            int sg = 0;
            int sb = 0;
            for (int offset = -radius; offset <= radius; offset++) {
                int argb = sample(input, width, height, line, offset, horizontal);
                sa += (argb >>> 24) & 0xff;
                sr += (argb >> 16) & 0xff;
                sg += (argb >> 8) & 0xff;
                sb += argb & 0xff;
            }
            for (int index = 0; index < length; index++) {
                int outIndex = horizontal ? line * width + index : index * width + line;
                output[outIndex] = ((sa / span) << 24) | ((sr / span) << 16)
                        | ((sg / span) << 8) | (sb / span);
                int add = sample(input, width, height, line, index + radius + 1, horizontal);
                int sub = sample(input, width, height, line, index - radius, horizontal);
                sa += ((add >>> 24) & 0xff) - ((sub >>> 24) & 0xff);
                sr += ((add >> 16) & 0xff) - ((sub >> 16) & 0xff);
                sg += ((add >> 8) & 0xff) - ((sub >> 8) & 0xff);
                sb += (add & 0xff) - (sub & 0xff);
            }
        }
    }

    private static int sample(int[] input, int width, int height, int line, int index,
                              boolean horizontal) {
        if (horizontal) {
            return input[line * width + Math.max(0, Math.min(width - 1, index))];
        }
        return input[Math.max(0, Math.min(height - 1, index)) * width + line];
    }

    /** Mirror image composited onto the opaque floor colour, fading out towards it. */
    private static void finishReflection(Bitmap bitmap, int background) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int bgRed = (background >> 16) & 0xff;
        int bgGreen = (background >> 8) & 0xff;
        int bgBlue = background & 0xff;
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int y = 0; y < height; y++) {
            float t = y / (float) (height - 1);
            float fade = (float) Math.pow(1f - t, 1.9) * 0.58f;
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int argb = pixels[index];
                float alpha = ((argb >>> 24) & 0xff) / 255f * fade;
                float keep = 1f - alpha;
                int red = clampByte(bgRed * keep + ((argb >> 16) & 0xff) * FLOOR_LOSS * alpha);
                int green = clampByte(bgGreen * keep + ((argb >> 8) & 0xff) * FLOOR_LOSS * alpha);
                int blue = clampByte(bgBlue * keep + (argb & 0xff) * FLOOR_LOSS * alpha);
                pixels[index] = 0xff000000 | (red << 16) | (green << 8) | blue;
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private static int clampByte(float value) {
        return value <= 0f ? 0 : value >= 255f ? 255 : (int) (value + .5f);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshAppsIfChanged();
        removeCallbacks(clockTick);
        removeCallbacks(warmUp);
        postDelayed(clockTick, 30_000L);
        // After the first frame: the tiles beyond the five around the selection.
        postDelayed(warmUp, WARMUP_DELAY_MS);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(clockTick);
        removeCallbacks(warmUp);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas rawCanvas) {
        super.onDraw(rawCanvas);
        float scale = Math.min(getWidth() / DESIGN_W, getHeight() / DESIGN_H);
        float dx = (getWidth() - DESIGN_W * scale) * 0.5f;
        float dy = (getHeight() - DESIGN_H * scale) * 0.5f;
        int save = rawCanvas.save();
        rawCanvas.translate(dx, dy);
        rawCanvas.scale(scale, scale);
        drawScene(rawCanvas);
        rawCanvas.restoreToCount(save);
    }

    private void drawScene(Canvas canvas) {
        int background = dark ? Color.BLACK : 0xfffdfdfd;
        int foreground = dark ? 0xfff3f3f3 : 0xff111111;
        int secondary = dark ? 0xff9a9a9a : 0xff7b7b7b;
        canvas.drawColor(background);

        drawHeader(canvas, foreground, secondary);

        // Match the HTML stacking: farther covers first, the card closest to center last.
        for (int i = 0; i < apps.length; i++) {
            renderOrder[i] = i;
            renderPositions[i] = circularPosition(i) + motion;
        }
        for (int i = 1; i < apps.length; i++) {
            int item = renderOrder[i];
            float distance = Math.abs(renderPositions[item]);
            int j = i - 1;
            while (j >= 0 && Math.abs(renderPositions[renderOrder[j]]) < distance) {
                renderOrder[j + 1] = renderOrder[j];
                j--;
            }
            renderOrder[j + 1] = item;
        }
        for (int index : renderOrder) {
            float position = renderPositions[index];
            drawCard(canvas, apps[index], position, edgeFade(Math.abs(position)));
            // The tile that wraps crosses to the opposite edge: draw the leaving copy there so
            // it slides out while fading instead of blinking away.
            if (Math.abs(position) > WRAP_DISTANCE - 1f && Math.abs(motion) > .001f) {
                float leaving = position - Math.signum(motion) * apps.length;
                drawCard(canvas, apps[index], leaving, Math.abs(motion));
            }
        }

        textPaint.setColor(foreground);
        textPaint.setTextSize(44f);
        textPaint.setFakeBoldText(false);
        textPaint.setTypeface(selectedFace());
        textPaint.setLetterSpacing(.015f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setStrokeWidth(0f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(apps[selected].name, 960f, 823f, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setTypeface(Typeface.DEFAULT);
        textPaint.setLetterSpacing(0f);
        textPaint.setStyle(Paint.Style.FILL);
    }

    private float circularPosition(int index) {
        int delta = index - selected;
        int half = apps.length / 2;
        if (delta > half) delta -= apps.length;
        if (delta < -half) delta += apps.length;
        return delta;
    }

    private void drawCard(Canvas canvas, AppItem app, float position, float alpha) {
        float absolute = Math.abs(position);
        if (absolute > WRAP_DISTANCE + .05f || alpha <= .01f) return;
        ensureCard(app);

        float half = HTML_CARD * .5f;
        float texturePad = HTML_CARD * TEXTURE_PAD / CARD;

        // Every tile keeps its reflection; the outermost ones only get fainter. Overlaps are
        // resolved by the floor itself (see drawReflection): tiles are painted outermost
        // first, so the floor of the tile closest to the centre always covers the rest.
        float strength = absolute <= 2.6f ? 1f
                : Math.max(.42f, 1f - (absolute - 2.6f) * .21f);
        if (strength * alpha > .01f) {
            drawReflection(canvas, app, position, half, strength * alpha);
        }

        int save = canvas.save();
        if (absolute > 1.02f) clipBehindNeighbour(canvas, position, absolute, half);
        fillProjectedQuad(position, -half - texturePad, -half - texturePad,
                half + texturePad, half + texturePad, destination);
        matrix.reset();
        matrix.setPolyToPoly(source, 0, destination, 0, 4);
        paint.setAlpha(clampByte(alpha * 255f));
        paint.setShader(null);
        canvas.drawBitmap(app.card, matrix, paint);
        canvas.restoreToCount(save);
    }

    /** Clip away whatever the tile one slot closer to the centre already covers. */
    private void clipBehindNeighbour(Canvas canvas, float position, float absolute, float half) {
        float near = Math.max(0f, absolute - 1f);
        float nearPosition = near <= 0f ? 0f : Math.signum(position) * near;
        fillProjectedQuad(nearPosition, -half, -half, half, half, occluder);
        if (position > 0f) {
            canvas.clipRect(Math.max(occluder[2], occluder[4]) - 2f, 0f, DESIGN_W, DESIGN_H);
        } else {
            canvas.clipRect(0f, 0f, Math.min(occluder[0], occluder[6]) + 2f, DESIGN_H);
        }
    }

    /** 1 while the tile is inside the ring, easing to 0 as it wraps around the far edge. */
    private static float edgeFade(float absolute) {
        if (absolute <= WRAP_DISTANCE - 1f) return 1f;
        return Math.max(0f, WRAP_DISTANCE - absolute);
    }

    private void drawReflection(Canvas canvas, AppItem app, float position, float half,
                                float strength) {
        if (strength <= .01f) return;
        // Sit flush against the card: a positive offset here was the visible seam.
        float top = half - .5f;
        float bottom = top + HTML_CARD * REFL_H * REFL_SCALE / CARD;
        fillProjectedQuad(position, -half, top, half, bottom, destination);
        // The texture already carries an opaque floor patch (see bakeReflection), so painting
        // it also erases the reflections of the tiles painted before it. Tiles are drawn
        // outermost first, hence the tile nearest the centre always owns the overlap.
        matrix.reset();
        matrix.setPolyToPoly(reflectionSource, 0, destination, 0, 4);
        paint.setShader(null);
        paint.setAlpha(clampByte(255f * strength));
        canvas.drawBitmap(dark ? app.reflectionDark : app.reflection, matrix, paint);
        paint.setAlpha(255);
    }

    /** Reproduces CSS perspective: 1180px perspective, origin at screen center, card rail at 43%. */
    private void fillProjectedQuad(float position, float left, float top, float right, float bottom,
                                   float[] output) {
        float absolute = Math.min(WRAP_DISTANCE, Math.abs(position));
        int low = Math.min(4, (int) Math.floor(absolute));
        int high = Math.min(5, low + 1);
        float amount = absolute - low;
        float sign = Math.signum(position);
        float tx = sign * DESIGN_W * lerp(X_FRACTIONS[low], X_FRACTIONS[high], amount);
        float ty = lerp(Y_OFFSETS[low], Y_OFFSETS[high], amount);
        float tz = lerp(Z_OFFSETS[low], Z_OFFSETS[high], amount);
        float rotation = (float) Math.toRadians(-sign * lerp(ROTATIONS[low], ROTATIONS[high], amount));
        float scale = lerp(SCALES[low], SCALES[high], amount);
        float cosine = (float) Math.cos(rotation);
        float sine = (float) Math.sin(rotation);

        projectCorner(output, 0, left, top, tx, ty, tz, scale, cosine, sine);
        projectCorner(output, 2, right, top, tx, ty, tz, scale, cosine, sine);
        projectCorner(output, 4, right, bottom, tx, ty, tz, scale, cosine, sine);
        projectCorner(output, 6, left, bottom, tx, ty, tz, scale, cosine, sine);
    }

    private void projectCorner(float[] output, int offset, float localX, float localY, float tx, float ty,
                               float tz, float scale, float cosine, float sine) {
        float scaledX = localX * scale;
        float worldX = DESIGN_W * .5f + tx + scaledX * cosine;
        float worldY = RAIL_Y + ty + localY * scale;
        float worldZ = tz - scaledX * sine;
        float projection = PERSPECTIVE / (PERSPECTIVE - worldZ);
        output[offset] = DESIGN_W * .5f + (worldX - DESIGN_W * .5f) * projection;
        output[offset + 1] = DESIGN_H * .5f + (worldY - DESIGN_H * .5f) * projection;
    }

    /**
     * The date line. The reference design uppercases it, which is right for Latin scripts and
     * wrong for CJK ones - and the order of the parts comes from the system locale, not from a
     * hard-coded "EEEE, MMM d, yyyy".
     */
    private String headerDate(Date now) {
        String date = dateFormat.format(now);
        return isCjk(locale) ? date : date.toUpperCase(locale);
    }

    private static boolean isCjk(Locale locale) {
        String language = locale.getLanguage();
        return "zh".equals(language) || "ja".equals(language) || "ko".equals(language);
    }

    private void drawHeader(Canvas canvas, int foreground, int secondary) {
        Date now = new Date();
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setStrokeWidth(0f);
        textPaint.setFakeBoldText(false);
        textPaint.setTypeface(clockFace);
        textPaint.setColor(foreground);
        textPaint.setTextSize(48f);
        textPaint.setLetterSpacing(-0.02f);
        canvas.drawText(timeFormat.format(now), 128f, 72f, textPaint);
        textPaint.setTypeface(dateFace);
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(18f);
        textPaint.setLetterSpacing(0.11f);
        textPaint.setColor(dark ? 0xff9a9a9a : 0xff8e8e93);
        canvas.drawText(headerDate(now), 130f, 102f, textPaint);
        textPaint.setLetterSpacing(0f);
        textPaint.setTypeface(Typeface.DEFAULT);

        // Equal gaps: switch | 19px | gear | 19px | wifi, right edge stays at 96px padding.
        drawThemeButton(canvas, 1686f, 64f, headerFocus == 0, foreground);
        drawSettingsButton(canvas, 1752f, 64f, headerFocus == 1, foreground);
        drawWifiButton(canvas, 1802f, 64f, headerFocus == 2, foreground);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(dark ? 0xff202020 : 0xffe4e4e4);
        strokePaint.setStrokeWidth(1.5f);
        canvas.drawLine(0f, 124f, 1920f, 124f, strokePaint);
    }

    /** Serif for the selected app name: bundled subset covers CJK, which the TV lacks in serif. */
    private Typeface loadNameFace() {
        try {
            return Typeface.createFromAsset(getContext().getAssets(), "app_name_serif.ttf");
        } catch (RuntimeException ignored) {
            return Typeface.create(Typeface.SERIF, Typeface.BOLD);
        }
    }

    private static Typeface loadFace(String path, String family) {
        try {
            Typeface fileFace = Typeface.createFromFile(path);
            if (fileFace != null) return fileFace;
        } catch (RuntimeException ignored) {
        }
        Typeface created = Typeface.create(family, Typeface.NORMAL);
        return created != null ? created : Typeface.DEFAULT_BOLD;
    }

    private void drawButtonBase(Canvas canvas, float cx, float cy, float radius, boolean focused) {
        // Nothing is drawn at rest, so the icons sit on the bare background in both themes;
        // focus fills a solid disc and flips the icon colour instead of adding an outline.
        if (focused) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(dark ? 0xfff4f4f4 : 0xff111111);
            canvas.drawCircle(cx, cy, radius, paint);
        }
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    private void drawThemeButton(Canvas canvas, float cx, float cy, boolean focused, int foreground) {
        // A real toggle, matching the reference: dark capsule, one round knob on the side that
        // shows the current state, and the moon drawn as an outline on the free side.
        float grow = focused ? 2f : 0f;
        float halfW = 34f + grow;
        float halfH = 15f + grow;
        RectF pill = new RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(dark ? 0xffececec : 0xff2b2b2b);
        canvas.drawRoundRect(pill, halfH, halfH, paint);

        float knobRadius = halfH - 3.5f;
        float travel = halfW - halfH;
        float knobX = dark ? cx + travel : cx - travel;
        paint.setColor(dark ? 0xff1a1a1a : Color.WHITE);
        canvas.drawCircle(knobX, cy, knobRadius, paint);

        // The glyph keeps the same distance from the capsule end as the knob does, so the
        // longer capsule gains breathing room instead of pushing it into the edge.
        float glyphX = dark ? cx - travel : cx + travel;
        int glyph = dark ? 0xff1a1a1a : Color.WHITE;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.9f);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(glyph);
        if (dark) {
            drawMoon(canvas, glyphX, cy, halfH * .46f);
        } else {
            drawSun(canvas, glyphX, cy);
        }
        strokePaint.setStrokeCap(Paint.Cap.BUTT);
    }

    /** Outline crescent: a disc with a second disc punched out of it. */
    private void drawMoon(Canvas canvas, float cx, float cy, float radius) {
        Path moon = new Path();
        moon.addCircle(cx, cy, radius, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx + radius * .60f, cy - radius * .12f, radius, Path.Direction.CW);
        moon.op(bite, Path.Op.DIFFERENCE);
        canvas.drawPath(moon, strokePaint);
    }

    /** Sun: a small ring with eight short rays. */
    private void drawSun(Canvas canvas, float cx, float cy) {
        canvas.drawCircle(cx, cy, 3.6f, strokePaint);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            canvas.drawLine(cx + cos * 6.4f, cy + sin * 6.4f,
                    cx + cos * 9.8f, cy + sin * 9.8f, strokePaint);
        }
    }

    private void drawSettingsButton(Canvas canvas, float cx, float cy, boolean focused, int foreground) {
        drawButtonBase(canvas, cx, cy, 20f, focused);
        // The gear is baked to a bitmap: stroking this path every frame left a translucent
        // grey haze inside the toothed rim under hardware acceleration.
        // Dark glyph on light backgrounds, light glyph when focused (dark disc) or in dark mode.
        Bitmap gear = (dark != focused) ? gearDark : gearLight;
        canvas.drawBitmap(gear, cx - GEAR_SIZE * .5f, cy - GEAR_SIZE * .5f, paint);
    }

    private void bakeGear() {
        gearLight = bakeGearBitmap(0xff111111);
        gearDark = bakeGearBitmap(0xfff3f3f3);
    }

    private static Bitmap bakeGearBitmap(int color) {
        int bake = GEAR_SIZE * GEAR_SUPERSAMPLE;
        Bitmap bitmap = Bitmap.createBitmap(bake, bake, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // 4x supersample: the glyph is only 28px on screen, so most of its smoothness comes
        // from averaging many samples on the way down. No alpha thresholding here — snapping
        // edge coverage to black/transparent is what produced visible stair-stepping.
        canvas.scale(GEAR_SUPERSAMPLE, GEAR_SUPERSAMPLE);
        canvas.translate(GEAR_SIZE * .5f, GEAR_SIZE * .5f);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        // Match the Wi-Fi glyph's 2.2px stroke so the header reads as one set of line icons.
        stroke.setStrokeWidth(2.2f);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(color);
        canvas.drawPath(GEAR_PATH, stroke);
        canvas.drawCircle(0f, 0f, 4.6f, stroke);
        // Downscale during the bake so the final texture is display-sized and blits 1:1.
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, GEAR_SIZE, GEAR_SIZE, true);
        bitmap.recycle();
        softenAlpha(scaled);
        return scaled;
    }

    /**
     * Gentle contrast lift on the antialiased rim: keeps the edge gradient (no stair steps)
     * while stopping half-covered pixels from reading as a grey halo.
     */
    private static void softenAlpha(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int alpha = (pixel >>> 24) & 0xff;
            if (alpha == 0 || alpha == 255) continue;
            float normalised = alpha / 255f;
            int adjusted = Math.round((float) Math.pow(normalised, 0.78f) * 255f);
            pixels[i] = (adjusted << 24) | (pixel & 0x00ffffff);
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    /**
     * Eight-tooth outline gear. The tooth roots are straight chords rather than arcs: a 25°
     * arc on a 9.4px radius is only ~4px long and its stroke degenerates when rasterised.
     */
    private static Path buildGearPath() {
        float outer = 13f;
        float inner = 9.4f;
        RectF rim = new RectF(-outer, -outer, outer, outer);
        Path path = new Path();
        for (int i = 0; i < 8; i++) {
            float centre = i * 45f;
            path.arcTo(rim, centre - 11f, 22f);
            path.lineTo(polar(inner, centre + 11f, true), polar(inner, centre + 11f, false));
            path.lineTo(polar(inner, centre + 34f, true), polar(inner, centre + 34f, false));
            path.lineTo(polar(outer, centre + 34f, true), polar(outer, centre + 34f, false));
        }
        path.close();
        return path;
    }

    /** X (cos) or Y (sin) of the point at {@code degrees} and {@code radius}. */
    private static float polar(float radius, float degrees, boolean cosine) {
        double radians = Math.toRadians(degrees);
        return radius * (float) (cosine ? Math.cos(radians) : Math.sin(radians));
    }

    private void drawWifiButton(Canvas canvas, float cx, float cy, boolean focused, int foreground) {
        drawButtonBase(canvas, cx, cy, 20f, focused);
        int icon = focused ? (dark ? Color.BLACK : Color.WHITE) : foreground;
        strokePaint.setColor(icon);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeWidth(2.2f);
        // Standard Wi-Fi glyph: the dot sits at the centre the three arcs share, so the
        // innermost arc hugs the dot instead of floating far above it.
        float originY = cy + 10f;
        float[] radii = {10f, 16.5f, 23f};
        for (float radius : radii) {
            RectF arc = new RectF(cx - radius, originY - radius, cx + radius, originY + radius);
            canvas.drawArc(arc, 225f, 90f, false, strokePaint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(icon);
        canvas.drawCircle(cx, originY, 2.5f, paint);
        strokePaint.setStrokeCap(Paint.Cap.BUTT);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() > 0) return true;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (headerFocus >= 0) headerFocus = Math.max(0, headerFocus - 1);
                else navigate(-1);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (headerFocus >= 0) headerFocus = Math.min(2, headerFocus + 1);
                else navigate(1);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                headerFocus = headerFocus < 0 ? 0 : headerFocus;
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                headerFocus = -1;
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                activate();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            return true;
        }
        // KEYCODE_MENU is deliberately not consumed: the firmware's side panel lives on it.
        return super.onKeyUp(keyCode, event);
    }

    private void navigate(int direction) {
        if (animator != null && animator.isRunning()) {
            pendingDirection = direction;
            return;
        }
        final float start = direction;
        selected = wrap(selected + direction);
        motion = start;
        lastAnimationDrawNanos = 0L;
        animator = ValueAnimator.ofFloat(start, 0f);
        animator.setDuration(ANIMATION_MS);
        animator.setInterpolator(new PathInterpolator(0.22f, 0.72f, 0.18f, 1f));
        animator.addUpdateListener(value -> {
            motion = (float) value.getAnimatedValue();
            long now = System.nanoTime();
            if (lastAnimationDrawNanos == 0L || now - lastAnimationDrawNanos >= 20_000_000L
                    || Math.abs(motion) < .001f) {
                lastAnimationDrawNanos = now;
                invalidate();
            }
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                motion = 0f;
                invalidate();
                if (pendingDirection != 0) {
                    int next = pendingDirection;
                    pendingDirection = 0;
                    navigate(next);
                }
            }
        });
        animator.start();
    }

    private int wrap(int value) {
        int result = value % apps.length;
        return result < 0 ? result + apps.length : result;
    }

    private void activate() {
        if (headerFocus == 0) {
            toggleTheme();
        } else if (headerFocus == 1) {
            openSystemPage(Settings.ACTION_SETTINGS);
        } else if (headerFocus == 2) {
            openSystemPage(Settings.ACTION_WIFI_SETTINGS);
        } else {
            AppItem app = apps[selected];
            if (app.sourceIndex >= 0 && switchSource(app.sourceIndex)) return;
            Intent intent = getContext().getPackageManager()
                    .getLeanbackLaunchIntentForPackage(app.packageName);
            if (intent == null) {
                intent = getContext().getPackageManager().getLaunchIntentForPackage(app.packageName);
            }
            if (intent == null) {
                // Last resort for an entry that only exists as a settings shim.
                intent = new Intent(Settings.ACTION_SETTINGS);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                getContext().startActivity(intent);
            } catch (RuntimeException ignored) {
                // Nothing on this firmware can handle the entry: stay on the ring.
            }
        }
    }

    /**
     * Opens a system settings page. Not every firmware handles every action, and an unhandled one
     * throws ActivityNotFoundException - from the HOME activity, which would leave the TV without
     * a usable home screen until ADB reopens it. So: try, and if the page is missing, fall back to
     * whatever "settings" means here.
     */
    private void openSystemPage(String action) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(intent);
            return;
        } catch (RuntimeException missing) {
            // Fall through to the plain settings page (or do nothing at all).
        }
        if (!Settings.ACTION_SETTINGS.equals(action)) {
            try {
                getContext().startActivity(
                        new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {
                // No settings page on this firmware: stay on the ring rather than crash Home.
            }
        }
    }

    /**
     * Asks SkyOS to bring up an HDMI input. Returns false when the provider is missing
     * (any non-Skyworth TV), so the caller can fall back to launching a package.
     */
    private boolean switchSource(int index) {
        try {
            Bundle args = new Bundle();
            args.putString("name", "HDMI");
            args.putInt("index", index);
            getContext().getContentResolver().call(
                    Uri.parse("content://com.skyworth.tv.sdk.provider/method"),
                    "METHOD_GOTO_SOURCE", null, args);
            return true;
        } catch (Exception missing) {
            return false;
        }
    }

    private void toggleTheme() {
        dark = !dark;
        preferences.edit().putBoolean("dark", dark).apply();
        invalidate();
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }
}
