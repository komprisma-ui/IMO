package com.imo.operator;

import org.junit.Test;
import static org.junit.Assert.*;

public class IMOJarvisCoreCompileTest {
    @Test public void coreProducesDecision() {
        IMOJarvisCore.Decision d = IMOJarvisCore.understand("buka WhatsApp");
        assertNotNull(d);
        assertEquals(IMOJarvisCore.Intent.OPEN_APP, d.intent);
    }
}
