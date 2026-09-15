package io.nop.ai.dsl.orm;

import io.nop.ai.dsl.orm.consts.GptOrmSqlType;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static io.nop.ai.dsl.orm.GptOrmErrors.ARG_SQL_TYPE;
import static io.nop.ai.dsl.orm.GptOrmErrors.ERR_DSL_ORM_UNKNOWN_SQL_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-4 description Englishization regression guard (plan
 * 2026-09-15-1231-2 Phase 2): {@link GptOrmErrors#ERR_DSL_ORM_UNKNOWN_SQL_TYPE}
 * pins three contracts so a future drift is caught at test time — ID
 * stability, English (no-CJK) description, and the {@code {sqlType}} parameter
 * contract. The end-to-end test exercises the real production throw site
 * {@link GptOrmSqlType#getStdSqlType} with an unknown SQL type.
 */
public class TestGptOrmErrorsDescriptionEnglish {

    @Test
    public void testUnknownSqlTypeDescriptionIsEnglishAndContractStable() {
        assertEquals("nop.err.ai.dsl-orm.unknown-sql-type", ERR_DSL_ORM_UNKNOWN_SQL_TYPE.getErrorCode(),
                "error-code ID contract must stay stable (round 4)");
        String description = ERR_DSL_ORM_UNKNOWN_SQL_TYPE.getDescription();
        assertNotNull(description, "description must not be null");
        assertTrue(!description.isBlank(), "description must not be blank");
        assertNoCjk(description);
        assertTrue(description.contains("{sqlType}"),
                "description must keep the {sqlType} placeholder, actual=" + description);
        assertTrue(description.contains("Unknown SQL type"),
                "description must carry the sql-type semantics, actual=" + description);
    }

    @Test
    public void testUnknownSqlTypeThrowSiteCarriesEnglishMessage() {
        NopException ex = assertThrows(NopException.class,
                () -> GptOrmSqlType.getStdSqlType("bogus-type"),
                "unknown sql type must fail fast at the throw site");
        assertEquals(ERR_DSL_ORM_UNKNOWN_SQL_TYPE.getErrorCode(), ex.getErrorCode(),
                "throw site must keep pointing at ERR_DSL_ORM_UNKNOWN_SQL_TYPE");
        assertEquals("nop.err.ai.dsl-orm.unknown-sql-type", ex.getErrorCode(),
                "error-code ID contract must stay stable (round 4)");
        assertEquals("bogus-type", ex.getParam(ARG_SQL_TYPE),
                "the unknown sql type must be attached as a param");
        String description = ex.getDescription();
        assertNoCjk(description);
        assertTrue(description.contains("Unknown SQL type"),
                "throw-site description must be English, actual=" + description);
        assertTrue(ex.getMessage().contains("Unknown SQL type: bogus-type"),
                "throw-site message must render the English description, actual=" + ex.getMessage());
    }

    /** 描述文本不得含 CJK 字符（AGENTS.md English error-message 约定，round 4 收口）。 */
    private static void assertNoCjk(String text) {
        assertTrue(text.codePoints().noneMatch(TestGptOrmErrorsDescriptionEnglish::isCjk),
                "description must not contain CJK characters, actual=" + text);
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF);
    }
}