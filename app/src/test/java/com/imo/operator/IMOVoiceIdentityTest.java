package com.imo.operator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class IMOVoiceIdentityTest {
    @Test public void cosineSimilarityMatchesIdenticalVectors() {
        float[] a = new float[]{1, 2, 3, 4, 5, 6, 7, 8};
        assertEquals(1f, IMOVoiceIdentity.cosineSimilarity(a, a), 0.0001f);
    }

    @Test public void cosineSimilarityRejectsDifferentDimensions() {
        float[] a = new float[]{1, 2, 3, 4, 5, 6, 7, 8};
        float[] b = new float[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertTrue(IMOVoiceIdentity.cosineSimilarity(a, b) < 0);
    }
}
