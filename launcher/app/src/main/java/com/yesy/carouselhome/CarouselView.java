package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import com.yesy.carouselhome.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Lightweight, allocation-free Cover Flow renderer for low-memory Android TVs. */
public final class CarouselView extends View {
    private static final int CARD = 384;
    private static final int TEXTURE_PAD = 32;
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

    private static final class AppItem {
        String name;
        final String packageName;
        final int imageRes;
        final int color;
        final float logoWidth;
        Bitmap card;
        Bitmap face;
        Bitmap reflection;
        Bitmap reflectionDark;

        AppItem(String name, String packageName, int imageRes, int color, float logoWidth) {
            this.name = name;
            this.packageName = packageName;
            this.imageRes = imageRes;
            this.color = color;
            this.logoWidth = logoWidth;
        }
    }

    private final AppItem[] apps = {
            new AppItem("Quark", "com.quark.yun.tv", R.drawable.quark, 0xfff7f7f5, .54f),
            new AppItem("HDMI 2", "com.coocaa.hdmi2", R.drawable.hdmi2, 0xffe60012, .58f),
            new AppItem("Cloud TV", "com.xiaodianshi.tv.yst", R.drawable.cloudtv, 0xffff3d78, .55f),
            new AppItem("Spotify", "com.spotify.tv.android", R.drawable.spotify, 0xff171414, .70f),
            new AppItem("HappyCast", "com.hpplay.happyplay.aw", R.drawable.happycast, 0xff378cf8, .50f),
            new AppItem("App Store", "com.tianci.appstore", R.drawable.appstore, 0xfff7f7f5, .48f),
            new AppItem("Fresh TV", "com.ixigua.android.tv.wasu", R.drawable.ixigua, 0xfff7f7f5, .48f),
            new AppItem("Mango TV", "com.hunantv.market", R.drawable.mgtv, 0xffff5900, .50f),
            new AppItem("NetEase Music", "com.netease.cloudmusic.tv", R.drawable.netease, 0xffe5002b, .48f)
    };

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
    private final int[] renderOrder = new int[apps.length];
    private final float[] renderPositions = new float[apps.length];
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d, yyyy", Locale.US);
    private final SharedPreferences preferences;
    private final Typeface clockFace = loadFace("/system/fonts/Roboto-Black.ttf", "sans-serif-black");
    private final Typeface dateFace = loadFace("/system/fonts/Roboto-Medium.ttf", "sans-serif-medium");
    private final Typeface nameFace = loadNameFace();

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
        composeCards();
        bakeGear();
    }

    /** Use the name the app itself ships with: Chinese stays Chinese, English stays English. */
    private void resolveLabels() {
        PackageManager manager = getContext().getPackageManager();
        for (AppItem app : apps) {
            try {
                ApplicationInfo info = manager.getApplicationInfo(app.packageName, 0);
                CharSequence label = manager.getApplicationLabel(info);
                if (label != null && label.length() > 0) {
                    app.name = label.toString().trim();
                }
            } catch (Exception ignored) {
                // Package missing or not queryable: keep the bundled fallback name.
            }
        }
    }

    private void composeCards() {
        resolveLabels();
        for (AppItem app : apps) {
            Bitmap sourceBitmap = BitmapFactory.decodeResource(getResources(), app.imageRes);
            Bitmap face = Bitmap.createBitmap(CARD, CARD, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(face);
            Path roundedCard = new Path();
            roundedCard.addRoundRect(new RectF(0f, 0f, CARD, CARD), 7f, 7f, Path.Direction.CW);
            canvas.clipPath(roundedCard);
            canvas.drawColor(app.color);
            float maxWidth = CARD * app.logoWidth;
            float maxHeight = CARD * .68f;
            float scale = Math.min(maxWidth / sourceBitmap.getWidth(), maxHeight / sourceBitmap.getHeight());
            float width = sourceBitmap.getWidth() * scale;
            float height = sourceBitmap.getHeight() * scale;
            RectF target = new RectF((CARD - width) / 2f, (CARD - height) / 2f,
                    (CARD + width) / 2f, (CARD + height) / 2f);
            paint.setAlpha(255);
            canvas.drawBitmap(sourceBitmap, null, target, paint);
            sourceBitmap.recycle();

            paint.setShader(new LinearGradient(0f, 0f, CARD, CARD,
                    new int[] {0x22ffffff, 0x00ffffff, 0x14000000},
                    new float[] {0f, .52f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, CARD, CARD, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(0x18000000);
            canvas.drawRoundRect(new RectF(.5f, .5f, CARD - .5f, CARD - .5f), 7f, 7f, paint);
            paint.setStyle(Paint.Style.FILL);

            Bitmap card = Bitmap.createBitmap(TEXTURE, TEXTURE, Bitmap.Config.ARGB_8888);
            Canvas textureCanvas = new Canvas(card);
            Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadow.setColor(app.color);
            shadow.setShadowLayer(22f, 0f, 13f, 0x42000000);
            RectF faceBounds = new RectF(TEXTURE_PAD, TEXTURE_PAD,
                    TEXTURE_PAD + CARD, TEXTURE_PAD + CARD);
            textureCanvas.drawRoundRect(faceBounds, 7f, 7f, shadow);
            shadow.clearShadowLayer();
            paint.setAlpha(255);
            textureCanvas.drawBitmap(face, TEXTURE_PAD, TEXTURE_PAD, paint);
            app.card = card;
            app.face = face;

            // The floor patch is baked into the texture: an opaque background inside the quad,
            // so painting the reflection also erases whatever tile was painted before it.
            app.reflection = bakeReflection(face, 0xfffdfdfd);
            app.reflectionDark = bakeReflection(face, Color.BLACK);
        }
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
        removeCallbacks(clockTick);
        postDelayed(clockTick, 30_000L);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(clockTick);
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
        textPaint.setTypeface(nameFace);
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
        canvas.drawText(dateFormat.format(now).toUpperCase(Locale.US), 130f, 102f, textPaint);
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
            getContext().startActivity(new Intent(Settings.ACTION_SETTINGS));
        } else if (headerFocus == 2) {
            getContext().startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } else {
            Intent intent = getContext().getPackageManager()
                    .getLeanbackLaunchIntentForPackage(apps[selected].packageName);
            if (intent == null) {
                intent = getContext().getPackageManager().getLaunchIntentForPackage(apps[selected].packageName);
            }
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                getContext().startActivity(intent);
            }
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
