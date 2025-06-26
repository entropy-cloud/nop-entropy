package io.nop.orm.support;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class OrmCompositePkTest {
    @Test
    public void testEquals() {
        OrmCompositePk pk1 = new OrmCompositePk(Arrays.asList("a", "b"), new Object[]{1, 2});
        OrmCompositePk pk2 = new OrmCompositePk(Arrays.asList("a", "b"), new Object[]{1, 2});
        assertEquals(pk1.hashCode(), pk2.hashCode());
        assertEquals(pk1, pk2);
    }
}
