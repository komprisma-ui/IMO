package com.imo.operator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class IMOAudioPipelineTest {
    @Test public void silentBufferHasZeroRms() {
        assertEquals(0f, IMOAudioPipeline.rms(new short[16000]), 0.0001f);
    }

    @Test public void nonSilentBufferHasPositiveRms() {
        short[] pcm = new short[16000];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short)(i % 1000);
        assertTrue(IMOAudioPipeline.rms(pcm) > 0f);
    }
}
