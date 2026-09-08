package com.imo.operator;

import android.content.Context;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig;

/** Real local speaker encoder backed by sherpa-onnx + 3D-Speaker CAM++ model. */
public final class IMOSherpaSpeakerEncoder implements IMOSpeakerEncoder {
    private static final String MODEL_NAME = "3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx";
    private final SpeakerEmbeddingExtractor extractor;

    public IMOSherpaSpeakerEncoder(Context context) throws Exception {
        SpeakerEmbeddingExtractorConfig config = new SpeakerEmbeddingExtractorConfig(
                MODEL_NAME, 2, false, "cpu");
        extractor = new SpeakerEmbeddingExtractor(context.getAssets(), config);
    }

    @Override public synchronized float[] embed(short[] pcm16, int sampleRateHz) throws Exception {
        if (pcm16 == null || pcm16.length < 4800) throw new IllegalArgumentException("Voice sample is too short");
        if (sampleRateHz <= 0) throw new IllegalArgumentException("Invalid sample rate");
        float[] samples = new float[pcm16.length];
        for (int i = 0; i < pcm16.length; i++) samples[i] = pcm16[i] / 32768.0f;
        OnlineStream stream = extractor.createStream();
        try {
            stream.acceptWaveform(samples, sampleRateHz);
            stream.inputFinished();
            float[] embedding = extractor.compute(stream);
            if (embedding == null || embedding.length < 8) throw new IllegalStateException("Speaker model returned no embedding");
            return embedding;
        } finally { stream.release(); }
    }

    public synchronized void release() { extractor.release(); }
}
