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
        if (context == null) throw new IllegalArgumentException("Context is required");
        OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig();
        whisper.setEncoder(MODEL_DIR + "/tiny-encoder.int8.onnx");
        whisper.setDecoder(MODEL_DIR + "/tiny-decoder.int8.onnx");
        whisper.setLanguage("id");
        whisper.setTask("transcribe");
        whisper.setTailPaddings(1000);

        OfflineModelConfig model = new OfflineModelConfig();
        model.setWhisper(whisper);
        model.setTokens(MODEL_DIR + "/tiny-tokens.txt");
        model.setNumThreads(2);
        model.setProvider("cpu");
        model.setModelType("whisper");

        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.setModelConfig(model);
        config.setDecodingMethod("greedy_search");
        recognizer = new OfflineRecognizer(context.getAssets(), config);
    }

    public synchronized String transcribe(short[] pcm16, int sampleRateHz) throws Exception {
        if (pcm16 == null || pcm16.length < 1600) throw new IllegalArgumentException("Audio terlalu pendek");
        if (sampleRateHz <= 0) throw new IllegalArgumentException("Sample rate tidak valid");
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
