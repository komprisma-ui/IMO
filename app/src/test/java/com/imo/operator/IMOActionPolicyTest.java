package com.imo.operator;

import org.junit.Test;
import static org.junit.Assert.*;

public class IMOActionPolicyTest {
    @Test public void harmlessActionIsLowRisk() {
        IMOActionPolicy.Assessment a = IMOActionPolicy.assess("buka pengaturan");
        assertEquals(IMOActionPolicy.Risk.LOW, a.risk);
        assertFalse(a.confirm);
    }

    @Test public void destructiveActionRequiresConfirmation() {
        IMOActionPolicy.Assessment a = IMOActionPolicy.assess("hapus file");
        assertEquals(IMOActionPolicy.Risk.MEDIUM, a.risk);
        assertTrue(a.confirm);
    }
}
