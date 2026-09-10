package com.imo.operator;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/** Compatibility-first PCM recorder with an adaptive end-of-speech path for low-latency commands. */
public final class IMOAudioRecorder {
    public static final int SAMPLE_RATE = 16000;
    private static final int MAX_ZERO_READS = 25;
    private IMOAudioRecorder() {}

    /** Records up to maxDurationMs but stops shortly after the speaker finishes. */
    public static short[] recordAdaptive(long maxDurationMs) throws Exception {
        long max = Math.max(1500L, Math.min(10000L, maxDurationMs));
        int maxSamples = (int)((SAMPLE_RATE * max) / 1000L);
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) throw new IllegalStateException("Mikrofon tidak tersedia pada perangkat ini");
        int bufferBytes = Math.max(minBuffer, 4096);
        Exception first = null;
        int[] sources = new int[]{MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC};
        for (int source : sources) {
            AudioRecord recorder = null;
            try {
                recorder = new AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED)
                    throw new IllegalStateException("AudioRecord gagal diinisialisasi (source=" + source + ")");
                short[] pcm = new short[maxSamples];
                short[] chunk = new short[Math.max(320, bufferBytes / 2)];
                int offset = 0;
                int zeroReads = 0;
                int silentChunks = 0;
                boolean speechStarted = false;
                int speechSamples = 0;
                recorder.startRecording();
                if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                    throw new IllegalStateException("Mikrofon tidak masuk mode recording");

                while (offset < maxSamples) {
                    int want = Math.min(chunk.length, maxSamples - offset);
                    int n = recorder.read(chunk, 0, want, AudioRecord.READ_BLOCKING);
                    if (n < 0) throw new IllegalStateException("Pembacaan mikrofon gagal (kode " + n + ")");
                    if (n == 0) {
                        if (++zeroReads > MAX_ZERO_READS) throw new IllegalStateException("Mikrofon tidak mengirim data audio");
                        continue;
                    }
                    zeroReads = 0;
                    System.arraycopy(chunk, 0, pcm, offset, n);
                    offset += n;

                    boolean active = speechChunk(chunk, n);
                    if (active) {
                        speechStarted = true;
                        speechSamples += n;
                        silentChunks = 0;
                    } else if (speechStarted) {
                        silentChunks++;
                    }

                    // Start listening immediately, but once speech is detected, finish after ~640 ms
                    // of trailing silence. This removes most of the old fixed 5-second dead time.
                    if (speechStarted && speechSamples >= SAMPLE_RATE * 0.45 && silentChunks * chunk.length >= SAMPLE_RATE * 0.64) {
                        break;
                    }
                }
                if (offset == 0) throw new IllegalStateException("Tidak ada data audio");
                short[] result = new short[offset];
                System.arraycopy(pcm, 0, result, 0, offset);
                return result;
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

    /** Legacy fixed-duration recorder retained for compatibility/tests. */
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
                        if (++zeroReads > MAX_ZERO_READS) throw new IllegalStateException("Mikrofon tidak mengirim data audio");
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

    private static boolean speechChunk(short[] pcm, int length) {
        if (pcm == null || length <= 0) return false;
        double energy = 0;
        int peak = 0;
        for (int i = 0; i < length; i++) {
            int a = Math.abs((int) pcm[i]);
            peak = Math.max(peak, a);
            energy += (double) a * a;
        }
        double rms = Math.sqrt(energy / length) / 32768.0;
        return rms >= 0.0016 && peak >= 220;
    }
}
