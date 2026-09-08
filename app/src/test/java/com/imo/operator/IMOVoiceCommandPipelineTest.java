package com.imo.operator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class IMOVoiceCommandPipelineTest {
    @Test public void detectsSpeechByEnergyAndPeak() {
        short[] silent = new short[16000];
        assertFalse(IMOVoiceCommandPipeline.hasSpeech(silent));
        short[] voiceLike = new short[16000];
        for (int i = 0; i < voiceLike.length; i++) voiceLike[i] = (short)((i % 40) * 100);
        assertTrue(IMOVoiceCommandPipeline.hasSpeech(voiceLike));
    }
}
