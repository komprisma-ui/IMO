package com.imo.operator;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;

/**
 * Local speaker-profile helper.
 * This is a lightweight acoustic fingerprint, not a security-grade biometric.
 * The profile is stored encrypted through SecureKeyStore.
 */
public final class VoiceProfileManager {
    public interface CaptureCallback { void done(float[] feature, String error); }

    private static final String PROFILE_KEY = "voice_profile_v1";
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME = 256;
    private static final int HOP = 128;
    private static final int BANDS = 24;

    private final Context context;
    private final SecureKeyStore store;

    public VoiceProfileManager(Context context, SecureKeyStore store) {
        this.context = context.getApplicationContext();
        this.store = store;
    }

    public boolean hasProfile() {
        try { return !store.loadNamed(PROFILE_KEY).trim().isEmpty(); }
        catch (Exception e) { return false; }
    }

    public void clearProfile() { store.clearNamed(PROFILE_KEY); }

    public void saveAverage(float[][] samples) throws Exception {
        if (samples == null || samples.length == 0) throw new IllegalArgumentException("Tidak ada sampel suara.");
        float[] avg = new float[BANDS];
        for (float[] s : samples) {
            if (s == null || s.length != BANDS) throw new IllegalArgumentException("Sampel suara tidak valid.");
            for (int i=0;i<BANDS;i++) avg[i] += s[i];
        }
        for (int i=0;i<BANDS;i++) avg[i] /= samples.length;
        normalize(avg);
        JSONObject o = new JSONObject();
        JSONArray a = new JSONArray();
        for (float v : avg) a.put(v);
        o.put("version", 1);
        o.put("sample_rate", SAMPLE_RATE);
        o.put("bands", BANDS);
        o.put("profile", a);
        store.saveNamed(PROFILE_KEY, o.toString());
    }

    public float similarity(float[] feature) {
        try {
            String raw = store.loadNamed(PROFILE_KEY);
            if (raw == null || raw.trim().isEmpty()) return -1f;
            JSONObject o = new JSONObject(raw);
            JSONArray a = o.getJSONArray("profile");
            if (a.length() != BANDS || feature == null || feature.length != BANDS) return -1f;
            float[] p = new float[BANDS];
            for (int i=0;i<BANDS;i++) p[i] = (float)a.getDouble(i);
            normalize(feature);
            float dot=0f, ap=0f, af=0f;
            for (int i=0;i<BANDS;i++){ dot += p[i]*feature[i]; ap += p[i]*p[i]; af += feature[i]*feature[i]; }
            if (ap <= 0f || af <= 0f) return -1f;
            return dot/(float)(Math.sqrt(ap)*Math.sqrt(af));
        } catch (Exception e) { return -1f; }
    }

    public void capture(long durationMs, CaptureCallback callback) {
        new Thread(() -> {
            AudioRecord recorder = null;
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    callback.done(null, "Izin mikrofon belum diberikan.");
                    return;
                }
                int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (min <= 0) { callback.done(null, "Perangkat tidak menyediakan buffer mikrofon."); return; }
                int buffer = Math.max(min, SAMPLE_RATE / 2);
                recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer * 2);
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    callback.done(null, "Mikrofon tidak dapat dibuka. Tutup aplikasi lain yang sedang memakai mikrofon.");
                    return;
                }
                short[] data = new short[(int)(SAMPLE_RATE * Math.max(500, durationMs) / 1000L)];
                recorder.startRecording();
                int offset=0;
                long end=System.currentTimeMillis()+Math.max(500,durationMs);
                while (offset < data.length && System.currentTimeMillis() < end) {
                    int n=recorder.read(data, offset, Math.min(data.length-offset, 1024));
                    if (n > 0) offset += n;
                    else if (n < 0) throw new IllegalStateException("AudioRecord error "+n);
                }
                recorder.stop();
                if (offset < SAMPLE_RATE/3) { callback.done(null, "Suara terlalu pendek atau mikrofon tidak menangkap audio."); return; }
                callback.done(extract(data, offset), null);
            } catch (SecurityException e) {
                callback.done(null, "Akses mikrofon ditolak.");
            } catch (Throwable e) {
                callback.done(null, e.getMessage()==null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                if (recorder != null) { try { if (recorder.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING) recorder.stop(); } catch(Exception ignored){} try{recorder.release();}catch(Exception ignored){} }
            }
        }, "luna-voice-capture").start();
    }

    private static float[] extract(short[] pcm, int length) {
        float[] out = new float[BANDS];
        int frames=0;
        double[] window = new double[FRAME];
        for (int start=0; start+FRAME<=length; start+=HOP) {
            double rms=0;
            for(int i=0;i<FRAME;i++){ double x=pcm[start+i]/32768.0; rms += x*x; }
            rms=Math.sqrt(rms/FRAME);
            if(rms < 0.012) continue;
            for(int i=0;i<FRAME;i++) window[i]=(pcm[start+i]/32768.0)*(0.54-0.46*Math.cos(2*Math.PI*i/(FRAME-1)));
            for(int b=0;b<BANDS;b++){
                int k0=1+(b*18);
                int k1=Math.min(FRAME/2-1,k0+18);
                double e=0;
                for(int k=k0;k<=k1;k++){
                    double re=0,im=0;
                    double ang=2*Math.PI*k/FRAME;
                    for(int n=0;n<FRAME;n++){ double a=ang*n; re+=window[n]*Math.cos(a); im-=window[n]*Math.sin(a); }
                    e += re*re+im*im;
                }
                out[b] += (float)Math.log1p(e);
            }
            frames++;
        }
        if(frames==0) return new float[BANDS];
        for(int i=0;i<BANDS;i++) out[i]/=frames;
        normalize(out);
        return out;
    }

    private static void normalize(float[] v) {
        float mean=0; for(float x:v) mean+=x; mean/=v.length;
        float norm=0; for(int i=0;i<v.length;i++){v[i]-=mean;norm+=v[i]*v[i];}
        norm=(float)Math.sqrt(norm);
        if(norm>1e-6f) for(int i=0;i<v.length;i++) v[i]/=norm;
    }
}
