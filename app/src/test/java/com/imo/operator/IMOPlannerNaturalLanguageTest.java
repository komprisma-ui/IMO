package com.imo.operator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import java.util.List;

public class IMOPlannerNaturalLanguageTest {
    @Test public void whatsappAliasOpensCorrectPackage() {
        List<IMOAction> a = IMOPlanner.plan("tolong bukakan WA");
        assertEquals(1, a.size());
        assertEquals(IMOAction.Type.OPEN_APP, a.get(0).type);
        assertEquals("com.whatsapp", a.get(0).value);
    }

    @Test public void searchProducesMultiStepPlan() {
        List<IMOAction> a = IMOPlanner.plan("cari Budi");
        assertTrue(a.size() >= 4);
        assertEquals(IMOAction.Type.CLICK, a.get(0).type);
        assertEquals("cari", a.get(0).value);
        assertEquals(IMOAction.Type.TYPE, a.get(2).type);
        assertEquals("Budi", a.get(2).value);
    }

    @Test public void sensitiveSendIsGated() {
        List<IMOAction> a = IMOPlanner.plan("kirim pesan");
        assertEquals(1, a.size());
        assertTrue(a.get(0).sensitive);
    }
}
