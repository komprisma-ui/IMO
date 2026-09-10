package com.imo.operator;

import org.junit.Test;
import static org.junit.Assert.*;

public class IMOGoalVerifierTest {
    @Test public void parsesDone() {
        IMOGoalVerifier.Result r = IMOGoalVerifier.parse("STATUS: DONE\nSAY: Sudah selesai.");
        assertEquals(IMOGoalVerifier.Status.DONE, r.status);
        assertEquals("Sudah selesai.", r.message);
        assertTrue(r.action.isEmpty());
    }

    @Test public void parsesContinueAndAction() {
        IMOGoalVerifier.Result r = IMOGoalVerifier.parse("STATUS: CONTINUE\nSAY: Saya lanjutkan.\nACTION: buka pengaturan");
        assertEquals(IMOGoalVerifier.Status.CONTINUE, r.status);
        assertEquals("buka pengaturan", r.action);
    }

    @Test public void malformedStatusFailsClosed() {
        IMOGoalVerifier.Result r = IMOGoalVerifier.parse("STATUS: MAYBE\nSAY: tidak jelas");
        assertEquals(IMOGoalVerifier.Status.UNKNOWN, r.status);
        assertTrue(r.action.isEmpty());
    }

    @Test public void emptyInputFailsClosed() {
        assertEquals(IMOGoalVerifier.Status.UNKNOWN, IMOGoalVerifier.parse("").status);
    }
}
