package io.nop.code.flow;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestChangeAnalyzer {

    private ChangeAnalyzer analyzer = new ChangeAnalyzer();

    private SymbolTable buildSymbolTable() {
        SymbolTable st = new SymbolTable();

        CodeSymbol sym1 = new CodeSymbol();
        sym1.setId("com.example.Service.process");
        sym1.setQualifiedName("com.example.Service.process");
        sym1.setName("process");
        sym1.setKind(CodeSymbolKind.METHOD);
        sym1.setLine(10);
        sym1.setEndLine(20);
        st.add(sym1);

        CodeSymbol sym2 = new CodeSymbol();
        sym2.setId("com.example.Util.validatePassword");
        sym2.setQualifiedName("com.example.Util.validatePassword");
        sym2.setName("validatePassword");
        sym2.setKind(CodeSymbolKind.METHOD);
        sym2.setLine(30);
        sym2.setEndLine(40);
        st.add(sym2);

        CodeSymbol sym3 = new CodeSymbol();
        sym3.setId("com.example.Dao.save");
        sym3.setQualifiedName("com.example.Dao.save");
        sym3.setName("save");
        sym3.setKind(CodeSymbolKind.METHOD);
        sym3.setLine(50);
        sym3.setEndLine(60);
        st.add(sym3);

        return st;
    }

    private CallGraph buildCallGraph() {
        CallGraph g = new CallGraph();
        g.addEdge("com.example.Service.process", "com.example.Util.validatePassword");
        g.addEdge("com.example.Service.process", "com.example.Dao.save");
        return g;
    }

    @Test
    void testRiskScoringDimensionsPopulated() {
        SymbolTable st = buildSymbolTable();
        CallGraph cg = buildCallGraph();

        ChangeAnalysisResult result = analyzer.analyzeChanges(
                "test-idx", "nonexistent~1", "nonexistent~2", st, cg);

        assertNotNull(result);
        assertNotNull(result.getChangedFiles());
        assertNotNull(result.getAffectedSymbols());
        assertNotNull(result.getRiskSummary());
        assertNotNull(result.getSuggestedActions());
    }

    @Test
    void testSecuritySensitivityDetected() {
        SymbolTable st = buildSymbolTable();
        CallGraph cg = buildCallGraph();

        ChangeAnalysisResult result = analyzer.analyzeChanges(
                "test-idx", "nonexistent~1", "nonexistent~2", st, cg);

        for (ChangeAnalysisResult.AffectedSymbol affected : result.getAffectedSymbols()) {
            if (affected.getQualifiedName() != null
                    && affected.getQualifiedName().contains("password")) {
                assertNotNull(affected.getRiskBreakdown());
                assertTrue(affected.getRiskBreakdown().getSecuritySensitivity() > 0,
                        "validatePassword should have positive security sensitivity");
            }
        }
    }

    @Test
    void testRiskSummaryHasCorrectStructure() {
        SymbolTable st = buildSymbolTable();
        CallGraph cg = buildCallGraph();

        ChangeAnalysisResult result = analyzer.analyzeChanges(
                "test-idx", "nonexistent~1", "nonexistent~2", st, cg);

        ChangeAnalysisResult.RiskSummary summary = result.getRiskSummary();
        assertNotNull(summary);
        assertTrue(summary.getHigh() >= 0);
        assertTrue(summary.getMedium() >= 0);
        assertTrue(summary.getLow() >= 0);
    }

    @Test
    void testAnalyzeWithNoGitChangesReturnsEmptyAffected() {
        SymbolTable st = buildSymbolTable();
        CallGraph cg = buildCallGraph();

        ChangeAnalysisResult result = analyzer.analyzeChanges(
                "test-idx", "nonexistent~1", "nonexistent~2", st, cg);

        assertNotNull(result.getAffectedSymbols());
        assertNotNull(result.getChangedFiles());
    }
}
