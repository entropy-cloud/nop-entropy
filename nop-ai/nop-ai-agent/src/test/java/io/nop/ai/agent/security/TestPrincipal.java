package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies the {@link Principal} value object immutability, factories, and
 * value equality.
 */
public class TestPrincipal {

    @Test
    void fieldsAreStoredAndAccessible() {
        Principal p = new Principal(PrincipalRole.OPERATOR, "ch-1", "tenant-A");
        assertEquals(PrincipalRole.OPERATOR, p.getRole());
        assertEquals("ch-1", p.getChannelId());
        assertEquals("tenant-A", p.getTenantId());
    }

    @Test
    void nullFieldsAreAllowed() {
        Principal p = new Principal(null, null, null);
        assertNull(p.getRole());
        assertNull(p.getChannelId());
        assertNull(p.getTenantId());
    }

    @Test
    void userFactoryReturnsUserRoleWithNullContext() {
        Principal p = Principal.user();
        assertSame(PrincipalRole.USER, p.getRole());
        assertNull(p.getChannelId());
        assertNull(p.getTenantId());
    }

    @Test
    void operatorFactoryReturnsOperatorRoleWithNullContext() {
        Principal p = Principal.operator();
        assertSame(PrincipalRole.OPERATOR, p.getRole());
        assertNull(p.getChannelId());
        assertNull(p.getTenantId());
    }

    @Test
    void equalsAndHashCodeByValue() {
        Principal a = new Principal(PrincipalRole.USER, "ch", "t");
        Principal b = new Principal(PrincipalRole.USER, "ch", "t");
        Principal c = new Principal(PrincipalRole.OPERATOR, "ch", "t");
        Principal d = new Principal(PrincipalRole.USER, "other", "t");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(a, null);
        assertNotEquals(a, "not-a-principal");
    }
}
