package com.imo.operator;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/** Compatibility-first PCM recorder. Tries Android voice-recognition input, then the normal microphone input. */
public final class IMOAudioRecorder {
    public static final int SAMPLE_RATE = 16000;
    private static final int MAX_ZERO_READS = 25;
    private IMOAudioRecorder() {}

    public static short[] record(long durationMs) throws Exception {
        int samples = (int)((SAMPLE_RATE * durationMs) / 1000L);
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IllegalStateException("Mikrofon tidak tersedia pada perangkat ini");
        int bufferBytes = Math.max(min, 4096);
        Exception first = null;
        int[] sources = new int[]{MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC};
        for (int source : sources) {
            AudioRecord recorder = null;
            try {
                recorder = new AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED)
                    throw new IllegalStateException("AudioRecord gagal diinisialisasi (source=" + source + ")");
                short[] pcm = new short[samples];
                int offset = 0;
                int zeroReads = 0;
                recorder.startRecording();
                if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                    throw new IllegalStateException("Mikrofon tidak masuk mode recording");
                while (offset < samples) {
                    int n = recorder.read(pcm, offset, Math.min(samples - offset, bufferBytes / 2), AudioRecord.READ_BLOCKING);
                    if (n < 0) throw new IllegalStateException("Pembacaan mikrofon gagal (kode " + n + ")");
                    if (n == 0) {
                        if (++zeroReads > MAX_ZERO_READS)
                            throw new IllegalStateException("Mikrofon tidak mengirim data audio");
                        continue;
                    }
                    zeroReads = 0;
                    offset += n;
                }
                return pcm;
            } catch (Exception e) {
                if (first == null) first = e;
            } finally {
                if (recorder != null) {
                    try { recorder.stop(); } catch (Exception ignored) {}
                    recorder.release();
                }
            }
        }
        throw new IllegalStateException("Mikrofon tidak dapat digunakan. Pastikan izin mikrofon aktif dan tidak sedang dikunci/digunakan aplikasi lain.", first);
    }
}
