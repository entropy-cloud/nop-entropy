package io.nop.code.service.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.service.api.ICodeIndexService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression pin for the findSymbols limit crowd-out defect surfaced by TestIndexNopXlangModule:
 * the fuzzy filter is {@code name contains q OR qualifiedName contains q} and used to be issued as
 * one unordered LIMIT query. Every member of a type whose qualified name merely contains the query
 * (e.g. the 50 methods of {@code IXLangCompileScope}) matched through the qualifiedName leg and,
 * being persisted earlier, pushed the exact-name hit ({@code XLangCompileScope}) past the limit.
 *
 * Contract pinned here: under a positive limit, results are ranked exact-name, then name-contains,
 * then qualifiedName-only, so a weaker match can never evict a stronger one.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestFindSymbolsRelevanceRanking extends JunitAutoTestCase {

    private static final String INDEX_ID = "find-symbols-ranking";
    private static final String EXACT_QN = "rank.scope.WidgetScope";

    /** Indexed first: 1 name-contains hit (the interface) + 5 qualifiedName-only hits (its methods). */
    private static final String API_SOURCE = """
            package rank.api;
            public interface IWidgetScope {
                void alpha();
                void beta();
                void gamma();
                void delta();
                void epsilon();
            }
            """;

    /** Indexed last: the exact-name hit, persisted after 6 weaker matches. */
    private static final String IMPL_SOURCE = """
            package rank.scope;
            public class WidgetScope {
                public void run() {}
            }
            """;

    @Inject
    ICodeIndexService codeIndexService;

    @Test
    void exactNameMatchSurvivesLimitAndRanksFirst() {
        codeIndexService.indexFile(INDEX_ID, "rank/api/IWidgetScope.java", API_SOURCE);
        codeIndexService.indexFile(INDEX_ID, "rank/scope/WidgetScope.java", IMPL_SOURCE);

        // Precondition: the weaker matches alone outnumber the limit, and the exact hit is indexed.
        List<CodeSymbol> all = codeIndexService.findSymbols(INDEX_ID, "WidgetScope", null, null, 0);
        assertTrue(all.size() >= 8, "expected interface + 5 methods + class + its method, got " + all.size());
        assertTrue(all.stream().anyMatch(s -> EXACT_QN.equals(s.getQualifiedName())),
                "unlimited search must contain the exact-name class");

        List<CodeSymbol> top = codeIndexService.findSymbols(INDEX_ID, "WidgetScope", null, null, 3);
        List<String> topQns = top.stream().map(CodeSymbol::getQualifiedName).collect(Collectors.toList());

        assertEquals(3, top.size(), "limit must still be honored: " + topQns);
        assertEquals(top.size(), new HashSet<>(top.stream().map(CodeSymbol::getId).collect(Collectors.toList())).size(),
                "ranking tiers must not produce duplicate rows: " + topQns);
        assertEquals(EXACT_QN, top.get(0).getQualifiedName(),
                "exact-name match must rank first instead of being evicted by qualifiedName-only matches: " + topQns);
        assertEquals("IWidgetScope", top.get(1).getName(),
                "name-contains match must outrank qualifiedName-only matches: " + topQns);
        assertFalse(top.get(2).getName().contains("WidgetScope"),
                "remaining slots are filled from the qualifiedName-only tier: " + topQns);
    }

    @Test
    void rankingKeepsKindFilter() {
        codeIndexService.indexFile(INDEX_ID, "rank/api/IWidgetScope.java", API_SOURCE);
        codeIndexService.indexFile(INDEX_ID, "rank/scope/WidgetScope.java", IMPL_SOURCE);

        List<CodeSymbol> classes = codeIndexService.findSymbols(INDEX_ID, "WidgetScope",
                List.of(CodeSymbolKind.CLASS), null, 3);
        assertEquals(1, classes.size(), "only the class matches the kind filter");
        assertEquals(EXACT_QN, classes.get(0).getQualifiedName());

        List<CodeSymbol> methods = codeIndexService.findSymbols(INDEX_ID, "WidgetScope",
                List.of(CodeSymbolKind.METHOD), null, 3);
        assertEquals(3, methods.size());
        assertTrue(methods.stream().allMatch(s -> s.getKind() == CodeSymbolKind.METHOD),
                "kind filter must apply to every ranking tier");
    }
}
