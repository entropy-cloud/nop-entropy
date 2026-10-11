package io.nop.ai.tools;

import io.nop.api.core.exceptions.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Description-contract regression guard for {@link NopAiToolsErrors}
 * (WI9 error-code relocation, plan 2438): the nine {@code nop.err.ai.tools.*}
 * codes moved verbatim from {@code NopAiCoreErrors} to nop-ai-tools, and the
 * description contract pin from
 * {@code io.nop.ai.core.service.TestNopAiCoreErrorsDescriptionEnglish} moves
 * with them. Pins three contracts so a future drift is caught at test time:
 *
 * <ul>
 *   <li><b>ID stability</b> — each code keeps its {@code nop.err.ai.tools.*}
 *       id string (persisted/logged id, renaming is a behavior change; the
 *       zh-CN i18n yaml in nop-cli-core is keyed by these ids).</li>
 *   <li><b>English description</b> — no CJK codepoints and the description is
 *       not blanked/emptied.</li>
 *   <li><b>Parameter contract</b> — every {@code {param}} placeholder that
 *       the throw sites attach is still present in the template.</li>
 * </ul>
 */
public class TestNopAiToolsErrorsDescriptionEnglish {

    private record CodeSpec(ErrorCode code, String expectedId, String... placeholders) {}

    private static final List<CodeSpec> TOOLS_CODES = List.of(
            new CodeSpec(NopAiToolsErrors.ERR_AI_TOOLS_INVALID_THOUGHT, "nop.err.ai.tools.invalid-thought", "{value}"),
            new CodeSpec(NopAiToolsErrors.ERR_AI_TOOLS_INVALID_PROJECT_NAME, "nop.err.ai.tools.invalid-project-name",
                    "{value}"),
            new CodeSpec(NopAiToolsErrors.ERR_AI_TOOLS_THOUGHT_EMPTY, "nop.err.ai.tools.thought-empty"),
            new CodeSpec(NopAiToolsErrors.ERR_AI_TOOLS_INVALID_THOUGHT_NUMBER, "nop.err.ai.tools.invalid-thought-number"),
            new CodeSpec(NopAiToolsErrors.ERR_AI_TOOLS_INVALID_TOTAL_THOUGHTS, "nop.err.ai.tools.invalid-total-thoughts"),
            new CodeSpec(NopAiToolsErrors.ERR_AI_TOOLS_TOTAL_THOUGHTS_LESS_THAN_NUMBER,
                    "nop.err.ai.tools.total-thoughts-less-than-number"),
            new CodeSpec(NopAiToolsErrors.ERR_AI_TOOLS_INVALID_STAGE, "nop.err.ai.tools.invalid-stage", "{value}"),
            new CodeSpec(NopAiToolsErrors.ERR_AI_TOOLS_INVALID_MAX_RESULTS, "nop.err.ai.tools.invalid-max-results"),
            new CodeSpec(NopAiToolsErrors.ERR_AI_TOOLS_SESSION_FILE_PATH_INVALID,
                    "nop.err.ai.tools.session-file-path-invalid", "{filePath}")
    );

    @Test
    public void testToolsCodesDescriptionIsEnglishAndContractStable() {
        assertEquals(9, TOOLS_CODES.size(),
                "nop-ai-tools owns exactly 9 nop.err.ai.tools.* codes (WI9/plan 2438 relocation; drift is caught)");
        for (CodeSpec spec : TOOLS_CODES) {
            ErrorCode code = spec.code();
            assertEquals(spec.expectedId(), code.getErrorCode(),
                    "error-code id must stay stable for " + spec.expectedId());
            String description = code.getDescription();
            assertNotNull(description, "description must not be null for " + spec.expectedId());
            assertTrue(!description.isBlank(), "description must not be blank for " + spec.expectedId());
            assertNoCjk(description, spec.expectedId());
            for (String placeholder : spec.placeholders()) {
                assertTrue(description.contains(placeholder),
                        "description of " + spec.expectedId() + " must keep placeholder " + placeholder
                                + ", actual=" + description);
            }
        }
    }

    private static void assertNoCjk(String text, String errorId) {
        assertTrue(text.codePoints().noneMatch(TestNopAiToolsErrorsDescriptionEnglish::isCjk),
                "description of " + errorId + " must not contain CJK characters, actual=" + text);
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF);
    }
}
