package com.imo.operator;

import android.content.Context;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;

/** Offline multilingual Whisper ASR. Raw PCM is transient and is not persisted. */
public final class IMOLocalAsr {
    private static final String MODEL_DIR = "asr/whisper-tiny";
    private final OfflineRecognizer recognizer;

    public IMOLocalAsr(Context context) {
        OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig(
                MODEL_DIR + "/tiny-encoder.int8.onnx",
                MODEL_DIR + "/tiny-decoder.int8.onnx",
                "id",
                "transcribe",
                -1,
                false,
                false
        );
        OfflineModelConfig model = new OfflineModelConfig(
                whisper,
                null, null, null, null, null, null, null, null, null, null, null, null,
                MODEL_DIR + "/tiny-tokens.txt",
                2,
                false,
                "cpu",
                "",
                "",
                ""
        );
        recognizer = new OfflineRecognizer(context.getAssets(), new OfflineRecognizerConfig(model));
    }

    public synchronized String transcribe(short[] pcm16, int sampleRateHz) throws Exception {
        if (pcm16 == null || pcm16.length < 1600) throw new IllegalArgumentException("Audio terlalu pendek");
        float[] samples = new float[pcm16.length];
        for (int i = 0; i < pcm16.length; i++) samples[i] = pcm16[i] / 32768.0f;
        OfflineStream stream = recognizer.createStream();
        try {
            stream.acceptWaveform(samples, sampleRateHz);
            recognizer.decode(stream);
            String text = recognizer.getResult(stream).getText();
            return text == null ? "" : text.trim();
        } finally { stream.release(); }
    }

    public synchronized void release() { recognizer.release(); }
}
