package com.imo.operator;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import java.util.List;

public class IMOPlannerTest {
    @Test public void plansWhatsapp() {
        List<IMOAction> actions = IMOPlanner.plan("buka whatsapp");
        assertEquals(1, actions.size());
        assertEquals(IMOAction.Type.OPEN_APP, actions.get(0).type);
    }

    @Test public void sensitiveActionsNeverAutoExecute() {
        List<IMOAction> actions = IMOPlanner.plan("kirim pesan");
        assertEquals(1, actions.size());
        assertEquals(IMOAction.Type.CONFIRM, actions.get(0).type);
        if (!actions.get(0).sensitive) throw new AssertionError("Sensitive action must require confirmation");
    }
}
