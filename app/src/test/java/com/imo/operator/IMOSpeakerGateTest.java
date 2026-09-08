package com.imo.operator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class IMOSpeakerGateTest {
    @Test public void requiresRepeatedEvidence() {
        IMOSpeakerGate gate = new IMOSpeakerGate(5, 4, 0.72f);
        assertFalse(gate.acceptScore(0.90f));
        assertFalse(gate.acceptScore(0.90f));
        assertFalse(gate.acceptScore(0.90f));
        assertTrue(gate.acceptScore(0.90f));
    }

    @Test public void rejectsOtherSpeakerScore() {
        IMOSpeakerGate gate = new IMOSpeakerGate(5, 4, 0.72f);
        for (int i = 0; i < 5; i++) assertFalse(gate.acceptScore(0.30f));
    }

    @Test public void cooldownBlocksActivation() {
        IMOSpeakerGate gate = new IMOSpeakerGate(5, 4, 0.72f);
        gate.cooldown(5000);
        for (int i = 0; i < 5; i++) assertFalse(gate.acceptScore(0.99f));
    }
}
