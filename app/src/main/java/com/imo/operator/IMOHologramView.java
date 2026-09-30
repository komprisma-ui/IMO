package com.imo.operator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.View;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * IMO live hologram renderer.
 *
 * The reference portrait is the visual identity. The renderer animates it as
 * a live holographic projection: breathing, lateral parallax, scan shimmer,
 * chromatic ghosting, energy sweep, orbit markers and voice-state glow.
 * Hardware rendering is deliberately used for stable animation performance.
 */
public final class IMOHologramView extends View {
    private static final String[] TARGET_CHUNKS = {
            "imo_target_00","imo_target_01","imo_target_02","imo_target_03","imo_target_04",
            "imo_target_05","imo_target_06","imo_target_07","imo_target_08","imo_target_09"
    };

    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(19);
    private Bitmap master;
    private final float[] px = new float[70], py = new float[70], ps = new float[70];
    private final long start = SystemClock.uptimeMillis();
    private boolean speaking, listening, thinking;

    public IMOHologramView(Context c) { super(c); init(); }
    public IMOHologramView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        // Keep the view on the normal GPU path. The previous forced software
        // layer made the full-screen animated hologram unnecessarily heavy.
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        imagePaint.setFilterBitmap(true);
        imagePaint.setDither(true);

        try {
            StringBuilder b64 = new StringBuilder(52000);
            for (String name : TARGET_CHUNKS) {
                int id = getResources().getIdentifier(name, "raw", getContext().getPackageName());
                if (id == 0) throw new IllegalStateException("Missing hologram chunk: " + name);
                try (InputStream in = getResources().openRawResource(id)) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        b64.append(new String(buf, 0, n, StandardCharsets.US_ASCII));
                    }
                }
            }
            byte[] data = Base64.decode(b64.toString(), Base64.DEFAULT);
            master = BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Throwable ignored) {
            master = null;
        }

        for (int i = 0; i < px.length; i++) {
            px[i] = random.nextFloat();
            py[i] = random.nextFloat();
            ps[i] = .7f + random.nextFloat() * 2.5f;
        }
        setBackgroundColor(Color.rgb(1, 5, 16));
    }

    public void setSpeaking(boolean v) { speaking = v; invalidate(); }
    public void setListening(boolean v) { listening = v; invalidate(); }
    public void setThinking(boolean v) { thinking = v; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        float pulse = .5f + .5f * (float)Math.sin(t * 2.4f);

        c.drawColor(Color.rgb(1, 5, 16));

        fxPaint.setStyle(Paint.Style.FILL);
        fxPaint.setShader(new android.graphics.RadialGradient(
                w * .5f, h * .40f, Math.min(w, h) * .66f,
                new int[]{Color.argb(90, 20, 130, 255), Color.argb(28, 10, 70, 190), Color.TRANSPARENT},
                new float[]{0f, .46f, 1f},
                android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, fxPaint);
        fxPaint.setShader(null);

        drawParticles(c, w, h, t, pulse);
        drawAvatar(c, w, h, t, pulse);
        drawInterfaceRings(c, w, h, t, pulse);
        drawScan(c, w, h, t);
        drawStateGlow(c, w, h, t, pulse);

        // Frame-synchronised animation. This is more reliable than a fixed
        // delayed runnable and avoids animation stalls after screen redraws.
        postInvalidateOnAnimation();
    }

    private void drawParticles(Canvas c, float w, float h, float t, float pulse) {
        fxPaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < px.length; i++) {
            float x = px[i] * w + (float)Math.sin(t * (.55f + ps[i] * .08f) + i) * 7f;
            float y = (py[i] * h + t * (7f + ps[i] * 3.2f)) % h;
            fxPaint.setColor(Color.argb((int)(25 + 85 * pulse), 75, 215, 255));
            c.drawCircle(x, y, ps[i] * (.75f + .4f * pulse), fxPaint);
        }
    }

    private RectF avatarRect(float w, float h, float t) {
        float maxW = w * .96f;
        float maxH = Math.min(h * .82f, maxW * .897f);
        float bw = Math.min(maxW, maxH / .897f);
        float bh = bw * .897f;

        // Very small breathing/parallax keeps the face recognisable instead
        // of visibly jumping around.
        float breathe = 1f + .014f * (float)Math.sin(t * 1.55f);
        bw *= breathe;
        bh *= breathe;

        float drift = (float)Math.sin(t * .85f) * 5.0f;
        float top = Math.max(0, h * .018f) + (float)Math.sin(t * 1.1f) * 2.2f;
        return new RectF((w - bw) * .5f + drift, top,
                (w + bw) * .5f + drift, top + bh);
    }

    private void drawAvatar(Canvas c, float w, float h, float t, float pulse) {
        if (master == null) return;
        RectF dst = avatarRect(w, h, t);

        // Aura.
        imagePaint.setAlpha((int)(36 + 28 * pulse));
        imagePaint.setColorFilter(null);
        imagePaint.setShadowLayer(24 + 12 * pulse, 0, 0, Color.rgb(0, 165, 255));
        c.drawBitmap(master, null, dst, imagePaint);
        imagePaint.clearShadowLayer();

        // Cyan ghosting. The offsets are intentionally small so the face
        // remains sharp while visibly behaving like a projection.
        float ghost = (float)Math.sin(t * 7.5f) * 4.8f;
        imagePaint.setAlpha(42);
        c.drawBitmap(master, null,
                new RectF(dst.left - ghost, dst.top, dst.right - ghost, dst.bottom), imagePaint);
        imagePaint.setAlpha(34);
        c.drawBitmap(master, null,
                new RectF(dst.left + ghost, dst.top + 1f, dst.right + ghost, dst.bottom + 1f), imagePaint);

        // 24 animated horizontal slices: enough motion to be clearly alive,
        // but much cheaper than the old 42-slice software renderer.
        imagePaint.setAlpha(238);
        final int slices = 24;
        float sliceH = dst.height() / slices;
        for (int i = 0; i < slices; i++) {
            float f0 = (float)i / slices;
            float f1 = (float)(i + 1) / slices;
            float waveAmp = speaking ? 9.0f : (listening ? 8.0f : 6.5f);
            float wave = (float)Math.sin(t * 5.0f + f0 * 22f) * waveAmp + (float)Math.sin(t * 11.0f + i * 1.7f) * 0.8f;
            float shimmer = (float)Math.sin(t * 9f + i * .8f) * .7f;
            float top = dst.top + i * sliceH;
            float bottom = (i == slices - 1) ? dst.bottom : top + sliceH + .45f;

            Rect src = new Rect(
                    0,
                    Math.max(0, (int)(master.getHeight() * f0)),
                    master.getWidth(),
                    Math.min(master.getHeight(),
                            Math.max(1, (int)(master.getHeight() * f1))));
            RectF d = new RectF(
                    dst.left + wave + shimmer, top,
                    dst.right + wave + shimmer, bottom);
            c.drawBitmap(master, src, d, imagePaint);
        }
        imagePaint.setAlpha(255);

        // Moving energy beam.
        float bandX = dst.left + ((t * 72f) % (dst.width() + 150f)) - 75f;
        fxPaint.setStyle(Paint.Style.FILL);
        fxPaint.setShader(new android.graphics.LinearGradient(
                bandX - 34f, 0, bandX + 34f, 0,
                new int[]{Color.TRANSPARENT, Color.argb(65, 110, 240, 255), Color.TRANSPARENT},
                null, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(dst.left, dst.top, dst.right, dst.bottom, fxPaint);
        fxPaint.setShader(null);

        // Voice activity ring around the mouth area.
        if (speaking) {
            float pulseMouth = .5f + .5f * (float)Math.sin(t * 14f);
            float mx = (dst.left + dst.right) * .5f;
            float my = dst.top + dst.height() * .60f;
            fxPaint.setStyle(Paint.Style.STROKE);
            fxPaint.setStrokeWidth(1.2f + 1.7f * pulseMouth);
            fxPaint.setColor(Color.argb((int)(55 + 90 * pulseMouth), 90, 235, 255));
            fxPaint.setShadowLayer(10, 0, 0, Color.rgb(40, 210, 255));
            c.drawOval(new RectF(mx - 24 - 6 * pulseMouth, my - 5,
                    mx + 24 + 6 * pulseMouth, my + 7 + 5 * pulseMouth), fxPaint);
            fxPaint.clearShadowLayer();
        }
        fxPaint.setStyle(Paint.Style.FILL);

        // Final cyan projection edge.
        imagePaint.setAlpha((int)(18 + 20 * pulse));
        imagePaint.setShadowLayer(18, 0, 0, Color.rgb(20, 190, 255));
        c.drawBitmap(master, null,
                new RectF(dst.left - 1, dst.top - 1, dst.right + 1, dst.bottom + 1),
                imagePaint);
        imagePaint.clearShadowLayer();
        imagePaint.setAlpha(255);
    }

    private void drawInterfaceRings(Canvas c, float w, float h, float t, float pulse) {
        float cx = w * .5f, cy = h * .55f;
        float rx = w * .41f, ry = Math.min(h * .29f, w * .39f);

        fxPaint.setStyle(Paint.Style.STROKE);
        fxPaint.setStrokeWidth(1.3f);
        fxPaint.setColor(Color.argb((int)(48 + 55 * pulse), 40, 185, 255));
        c.drawOval(new RectF(cx - rx, cy - ry, cx + rx, cy + ry), fxPaint);

        for (int j = 0; j < 2; j++) {
            float a = t * (.75f + j * .22f) + j * 3.14f;
            float x = cx + (float)Math.cos(a) * rx;
            float y = cy + (float)Math.sin(a) * ry;
            fxPaint.setStyle(Paint.Style.FILL);
            fxPaint.setColor(Color.argb(235, 115, 245, 255));
            c.drawCircle(x, y, 3.8f + 2.2f * pulse, fxPaint);
        }

        fxPaint.setStyle(Paint.Style.STROKE);
        fxPaint.setColor(Color.argb((int)(35 + 40 * pulse), 40, 210, 255));
        c.drawOval(new RectF(w * .25f, h * .73f, w * .75f, h * .80f), fxPaint);
    }

    private void drawScan(Canvas c, float w, float h, float t) {
        float y = (h * .08f + (t * 90f) % (h * .72f));
        fxPaint.setStyle(Paint.Style.FILL);
        fxPaint.setColor(Color.argb(42, 100, 235, 255));
        c.drawRect(w * .04f, y, w * .96f, y + 3f, fxPaint);
        fxPaint.setColor(Color.argb(14, 80, 210, 255));
        int phase = (int)(t * 18f) % 6;
        for (float yy = phase; yy < h * .76f; yy += 6f) {
            c.drawRect(w * .07f, yy, w * .93f, yy + 1f, fxPaint);
        }
    }

    private void drawStateGlow(Canvas c, float w, float h, float t, float pulse) {
        float cx = w * .5f, cy = h * .55f;
        int base = listening ? 105 : (speaking ? 130 : (thinking ? 82 : 40));
        float wave = .5f + .5f * (float)Math.sin(t * (speaking ? 11f : 3.5f));
        fxPaint.setStyle(Paint.Style.FILL);
        fxPaint.setColor(Color.argb((int)(base + 50 * wave), 50, 225, 255));
        c.drawCircle(cx, cy, 3 + 2.5f * pulse, fxPaint);
    }
}