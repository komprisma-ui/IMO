package com.imo.operator;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class IMOPerformanceProfileTest {
    @Test public void shortSpeechUsesFastSilenceWindow() {
        assertEquals(350, IMOPerformanceProfile.speechSilenceMs("buka wa"));
    }

    @Test public void mediumSpeechUsesBalancedWindow() {
        assertEquals(500, IMOPerformanceProfile.speechSilenceMs("tolong buka whatsapp sekarang"));
    }

    @Test public void longSpeechAllowsMoreTime() {
        assertEquals(700, IMOPerformanceProfile.speechSilenceMs("tolong buka whatsapp lalu cari pesan dari ibu dan bacakan"));
    }
}
