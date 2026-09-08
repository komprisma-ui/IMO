package com.imo.operator;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class IMOSemanticMatcherTest {
    @Test public void canonicalizesSearchSynonyms() {
        assertEquals("cari budi", IMOSemanticMatcher.canonical("pencarian Budi"));
        assertEquals("back", IMOSemanticMatcher.canonical("kembali"));
        assertEquals("delete pesan", IMOSemanticMatcher.canonical("hapus pesan"));
    }
}
