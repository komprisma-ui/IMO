package com.imo.operator;

import org.junit.Test;
import static org.junit.Assert.*;

public class IMOJarvisCoreTest {
    @Test public void understandsNaturalOpenCommand() {
        IMOJarvisCore.Decision d = IMOJarvisCore.understand("Bukakan WhatsApp untuk saya");
        assertEquals(IMOJarvisCore.Intent.OPEN_APP, d.intent);
        assertTrue(d.confidence >= .9f);
        assertFalse(d.needsConfirmation);
    }

    @Test public void detectsSensitiveCommand() {
        IMOJarvisCore.Decision d = IMOJarvisCore.understand("kirim pesan ke Budi");
        assertTrue(d.sensitive);
        assertTrue(d.needsConfirmation);
    }

    @Test public void normalizesCommonSynonyms() {
        String s = IMOJarvisCore.normalizeForPlanner("tolong bukakan WhatsApp");
        assertTrue(s.startsWith("buka"));
    }

    @Test public void policyEscalatesFinancialActions() {
        IMOActionPolicy.Assessment a = IMOActionPolicy.assess("bayar tagihan");
        assertEquals(IMOActionPolicy.Risk.HIGH, a.risk);
        assertTrue(a.confirm);
    }
}
