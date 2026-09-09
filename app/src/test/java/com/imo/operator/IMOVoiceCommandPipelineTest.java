package com.imo.operator;

import static org.junit.Assert.assertEquals;
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

    @Test public void acceptsQuietButUsableSpeech() {
        short[] quiet = new short[16000];
        for (int i = 0; i < quiet.length; i++) quiet[i] = (short)((i % 32) * 12);
        assertTrue(IMOVoiceCommandPipeline.hasSpeech(quiet));
    }

    @Test public void trimsLongLeadingAndTrailingSilence() {
        short[] pcm = new short[16000];
        for (int i = 4000; i < 12000; i++) pcm[i] = (short)((i % 40) * 100);
        short[] trimmed = IMOVoiceCommandPipeline.trimSilence(pcm);
        assertTrue(trimmed.length < pcm.length);
        assertTrue(trimmed.length >= 8000);
        assertTrue(trimmed[trimmed.length / 2] != 0);
    }

    @Test public void rejectsVeryShortAudio() {
        assertFalse(IMOVoiceCommandPipeline.hasSpeech(new short[2000]));
    }
}
