package io.nop.code.flow;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeAccessModifier;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestDeadCodeDetector {

    private DeadCodeDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DeadCodeDetector();
    }

    @Test
    void testNoCallerSymbolsDetectedAsDead() {
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        CodeSymbol used = new CodeSymbol();
        used.setId("pkg.A");
        used.setQualifiedName("pkg.A.used");
        used.setName("used");
        used.setKind(CodeSymbolKind.METHOD);
        used.setAccessModifier(CodeAccessModifier.PRIVATE);
        st.add(used);

        CodeSymbol unused = new CodeSymbol();
        unused.setId("pkg.B");
        unused.setQualifiedName("pkg.B.unused");
        unused.setName("unused");
        unused.setKind(CodeSymbolKind.METHOD);
        unused.setAccessModifier(CodeAccessModifier.PRIVATE);
        st.add(unused);

        cg.addEdge("some.caller", "pkg.A");

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        assertNotNull(report);
        assertNotNull(report.getDeadSymbols());
        assertNotNull(report.getSuspiciousSymbols());

        boolean unusedFound = report.getDeadSymbols().stream()
                .anyMatch(e -> "pkg.B".equals(e.getSymbolId()));
        assertTrue(unusedFound, "pkg.B with no callers should be detected as dead code");
    }

    @Test
    void testConstructorExcluded() {
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        CodeSymbol constructor = new CodeSymbol();
        constructor.setId("pkg.Ctor");
        constructor.setQualifiedName("pkg.MyClass.<init>");
        constructor.setName("<init>");
        constructor.setKind(CodeSymbolKind.CONSTRUCTOR);
        st.add(constructor);

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        boolean ctorInDead = report.getDeadSymbols().stream()
                .anyMatch(e -> "pkg.Ctor".equals(e.getSymbolId()));
        boolean ctorInSuspicious = report.getSuspiciousSymbols().stream()
                .anyMatch(e -> "pkg.Ctor".equals(e.getSymbolId()));
        assertFalse(ctorInDead, "Constructors should be excluded from dead code");
        assertFalse(ctorInSuspicious, "Constructors should be excluded from suspicious code");
    }

    @Test
    void testTestSymbolsExcluded() {
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        CodeSymbol testMethod = new CodeSymbol();
        testMethod.setId("pkg.testMethod");
        testMethod.setQualifiedName("pkg.MyTestClass.testMethod");
        testMethod.setName("testMethod");
        testMethod.setKind(CodeSymbolKind.METHOD);
        testMethod.setAccessModifier(CodeAccessModifier.PUBLIC);
        testMethod.setExtData("filePath:/test/MyTestClass.java");
        st.add(testMethod);

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        boolean testFound = report.getDeadSymbols().stream()
                .anyMatch(e -> "pkg.testMethod".equals(e.getSymbolId()));
        assertFalse(testFound, "Test class symbols should be excluded");
    }

    @Test
    void testStatsCorrectlyComputed() {
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        CodeSymbol unused = new CodeSymbol();
        unused.setId("pkg.dead");
        unused.setQualifiedName("pkg.dead");
        unused.setName("dead");
        unused.setKind(CodeSymbolKind.METHOD);
        unused.setAccessModifier(CodeAccessModifier.PRIVATE);
        st.add(unused);

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        assertNotNull(report.getStats());
        assertEquals(1, report.getStats().getTotal());
        int total = report.getStats().getDead() + report.getStats().getSuspicious();
        assertTrue(total >= 1, "Total dead + suspicious should be at least 1");
    }

    @Test
    void testEmptySymbolTableReturnsEmptyReport() {
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        assertNotNull(report);
        assertTrue(report.getDeadSymbols().isEmpty());
        assertTrue(report.getSuspiciousSymbols().isEmpty());
        assertEquals(0, report.getStats().getTotal());
    }

    @Test
    void testSymbolWithCallersNotDead() {
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        CodeSymbol alive = new CodeSymbol();
        alive.setId("pkg.alive");
        alive.setQualifiedName("pkg.alive");
        alive.setName("alive");
        alive.setKind(CodeSymbolKind.METHOD);
        alive.setAccessModifier(CodeAccessModifier.PUBLIC);
        st.add(alive);

        cg.addEdge("pkg.caller", "pkg.alive");

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        boolean aliveInDead = report.getDeadSymbols().stream()
                .anyMatch(e -> "pkg.alive".equals(e.getSymbolId()));
        boolean aliveInSuspicious = report.getSuspiciousSymbols().stream()
                .anyMatch(e -> "pkg.alive".equals(e.getSymbolId()));
        assertFalse(aliveInDead, "Symbol with callers should not be dead");
        assertFalse(aliveInSuspicious, "Symbol with callers should not be suspicious");
    }
}
