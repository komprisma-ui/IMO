package com.imo.operator;

import org.junit.Test;
import static org.junit.Assert.*;

public class IMOJarvisNaturalLanguageTest {
    @Test public void informalOpenPhraseIsRecognized() {
        IMOJarvisCore.Decision d = IMOJarvisCore.understand("tolong bukakan WhatsApp untukku");
        assertEquals(IMOJarvisCore.Intent.OPEN_APP, d.intent);
        assertTrue(d.confidence > .9f);
    }
}
