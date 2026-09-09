package com.imo.operator;

import org.junit.Test;
import static org.junit.Assert.*;

public class IMOActionPolicyBoundaryTest {
    @Test public void financialCommandsAreNeverLowRisk() {
        IMOActionPolicy.Assessment a = IMOActionPolicy.assess("transfer uang sekarang");
        assertTrue(a.risk != IMOActionPolicy.Risk.LOW);
        assertTrue(a.confirm);
    }
}
