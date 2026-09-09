package com.imo.operator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class IMOPlannerTest {
    @Test public void plansWhatsapp(){List<IMOAction>a=IMOPlanner.plan("buka whatsapp");assertEquals(1,a.size());assertEquals(IMOAction.Type.OPEN_APP,a.get(0).type);}
    @Test public void sensitiveActionsRemainConfirmed(){List<IMOAction>a=IMOPlanner.plan("kirim pesan");assertEquals(1,a.size());assertEquals(IMOAction.Type.CLICK,a.get(0).type);assertTrue(a.get(0).sensitive);}
    @Test public void plansGlobalNavigation(){assertEquals(IMOAction.Type.RECENTS,IMOPlanner.plan("buka aplikasi terbaru").get(0).type);assertEquals(IMOAction.Type.NOTIFICATIONS,IMOPlanner.plan("buka notifikasi").get(0).type);assertEquals(IMOAction.Type.QUICK_SETTINGS,IMOPlanner.plan("buka pengaturan cepat").get(0).type);}
    @Test public void plansArbitraryInstalledAppLabel(){List<IMOAction>a=IMOPlanner.plan("buka kalkulator");assertFalse(a.isEmpty());assertEquals(IMOAction.Type.OPEN_APP,a.get(0).type);assertEquals("kalkulator",a.get(0).value);}
    @Test public void plansUrl(){List<IMOAction>a=IMOPlanner.plan("buka https://example.com");assertEquals(IMOAction.Type.OPEN_URL,a.get(0).type);assertEquals("https://example.com",a.get(0).value);}
    @Test public void marksPowerMenuSensitive(){assertTrue(IMOPlanner.plan("buka menu daya").get(0).sensitive);}
}
