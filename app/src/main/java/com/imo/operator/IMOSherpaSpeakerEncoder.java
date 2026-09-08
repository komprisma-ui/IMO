package com.imo.operator;

import android.content.Context;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Real local speaker encoder backed by sherpa-onnx + 3D-Speaker CAM++ model. */
public final class IMOSherpaSpeakerEncoder implements IMOSpeakerEncoder {
    private static final String MODEL_NAME = "3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx";
    private final SpeakerEmbeddingExtractor extractor;

    public IMOSherpaSpeakerEncoder(Context context) throws Exception {
        File model = copyAssetIfNeeded(context, MODEL_NAME);
        SpeakerEmbeddingExtractorConfig config = SpeakerEmbeddingExtractorConfig.builder()
                .setModel(model.getAbsolutePath())
                .setNumThreads(2)
                .setDebug(false)
                .build();
        extractor = new SpeakerEmbeddingExtractor(config);
        if (extractor.getDim() < 8) throw new IllegalStateException("Invalid speaker model dimension");
    }

    @Override public synchronized float[] embed(short[] pcm16, int sampleRateHz) throws Exception {
        if (pcm16 == null || pcm16.length < 4800) throw new IllegalArgumentException("Voice sample is too short");
        float[] samples = new float[pcm16.length];
        for (int i = 0; i < pcm16.length; i++) samples[i] = pcm16[i] / 32768.0f;
        OnlineStream stream = extractor.createStream();
        try {
            stream.acceptWaveform(samples, sampleRateHz);
            stream.inputFinished();
            if (!extractor.isReady(stream)) throw new IllegalStateException("Speaker model needs more speech");
            float[] embedding = extractor.compute(stream);
            if (embedding == null || embedding.length < 8) throw new IllegalStateException("Speaker model returned no embedding");
            return embedding;
        } finally { stream.release(); }
    }

    public synchronized void release() { extractor.release(); }

    private static File copyAssetIfNeeded(Context context, String name) throws Exception {
        File target = new File(context.getFilesDir(), name);
        if (target.exists() && target.length() > 1_000_000L) return target;
        try (InputStream in = context.getAssets().open(name); FileOutputStream out = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) >= 0) { if (n > 0) out.write(buffer, 0, n); }
        }
        if (target.length() < 1_000_000L) throw new IllegalStateException("Speaker model copy is incomplete");
        return target;
    }
}
