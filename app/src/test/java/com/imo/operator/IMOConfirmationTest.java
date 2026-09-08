package com.imo.operator;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class IMOConfirmationTest {
    @Test public void requiresExplicitConfirmation() {
        IMOConfirmation gate = new IMOConfirmation();
        IMOAction action = IMOAction.sensitive(IMOAction.Type.CONFIRM, "kirim pesan");
        gate.request(action);
        assertNull(gate.consumeIfConfirmed("mungkin"));
        assertNotNull(gate.consumeIfConfirmed("ya"));
    }

    @Test public void cancelClearsPendingAction() {
        IMOConfirmation gate = new IMOConfirmation();
        gate.request(IMOAction.sensitive(IMOAction.Type.CONFIRM, "bayar"));
        IMOAction result = gate.consumeIfConfirmed("batal");
        if (result == null || result.type != IMOAction.Type.NONE) throw new AssertionError("Cancellation must clear action");
        assertNull(gate.pending());
    }
}
