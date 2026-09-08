package com.imo.operator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class IMOAudioPipelineTest {
    @Test public void silentBufferIsRejected() {
        assertFalse(IMOVoiceCommandPipeline.hasSpeech(new short[16000]));
    }

    @Test public void sufficientlyLoudBufferIsAccepted() {
        short[] pcm = new short[16000];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short)((i % 40) * 500);
        assertTrue(IMOVoiceCommandPipeline.hasSpeech(pcm));
    }
}
