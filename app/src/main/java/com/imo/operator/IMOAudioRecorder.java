package com.imo.operator;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/** Small blocking PCM recorder used only by the voice-enrollment/verification pipeline. */
public final class IMOAudioRecorder {
    public static final int SAMPLE_RATE = 16000;
    private IMOAudioRecorder() {}

    public static short[] record(long durationMs) throws Exception {
        int samples = (int)((SAMPLE_RATE * durationMs) / 1000L);
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IllegalStateException("AudioRecord is unavailable");
        int bufferBytes = Math.max(min, 2048);
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) { recorder.release(); throw new IllegalStateException("Microphone initialization failed"); }
        short[] pcm = new short[samples]; int offset = 0;
        try {
            recorder.startRecording();
            while (offset < samples) {
                int n = recorder.read(pcm, offset, Math.min(samples - offset, bufferBytes / 2));
                if (n < 0) throw new IllegalStateException("Microphone read failed: " + n);
                offset += n;
            }
            return pcm;
        } finally {
            try { recorder.stop(); } catch (Exception ignored) {}
            recorder.release();
        }
    }
}
