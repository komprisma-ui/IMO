package com.imo.operator;

import org.junit.Test;
import static org.junit.Assert.*;

public class IMOFastIntentTest {
    @Test public void navigationIsInstant() {
        assertEquals(IMOAction.Type.BACK, IMOFastIntent.parse("kembali").type);
        assertEquals(IMOAction.Type.BACK, IMOFastIntent.parse("tolong kembali").type);
        assertEquals(IMOAction.Type.HOME, IMOFastIntent.parse("beranda").type);
        assertEquals(IMOAction.Type.RECENTS, IMOFastIntent.parse("aplikasi terbaru").type);
    }

    @Test public void commonAppsResolve() {
        assertEquals("com.whatsapp", IMOFastIntent.parse("buka whatsapp").parameter);
        assertEquals("com.whatsapp", IMOFastIntent.parse("buka whatsapp dong").parameter);
        assertEquals("com.android.chrome", IMOFastIntent.parse("open chrome").parameter);
        assertEquals("com.google.android.youtube", IMOFastIntent.parse("buka youtube").parameter);
    }

    @Test public void deviceControlsResolve() {
        assertEquals(IMOAction.Type.VOLUME_UP, IMOFastIntent.parse("naikkan volume").type);
        assertEquals(IMOAction.Type.VOLUME_UP, IMOFastIntent.parse("tolong naikkan volume sedikit").type);
        assertEquals(IMOAction.Type.TORCH_ON, IMOFastIntent.parse("nyalakan senter").type);
        assertEquals(IMOAction.Type.TORCH_OFF, IMOFastIntent.parse("bisa matikan senter").type);
        assertEquals(IMOAction.Type.SCROLL_DOWN, IMOFastIntent.parse("geser ke bawah").type);
    }

    @Test public void unknownConversationFallsThroughToAi() {
        assertNull(IMOFastIntent.parse("bagaimana kabarmu hari ini"));
        assertNull(IMOFastIntent.parse("tolong bantu saya memilih hadiah"));
    }
}
