package io.nop.code.flow;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeAccessModifier;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.util.ExtDataHelper;
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

    // ==================== AR-68 Regression Tests ====================

    @Test
    void testFrameworkAnnotationInExtDataExcludesSymbol() {
        // AR-68: Annotations must be read from extData, not from signature
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        CodeSymbol controller = new CodeSymbol();
        controller.setId("pkg.controllerMethod");
        controller.setQualifiedName("pkg.MyController.handleRequest");
        controller.setName("handleRequest");
        controller.setKind(CodeSymbolKind.METHOD);
        controller.setAccessModifier(CodeAccessModifier.PUBLIC);
        // Simulate annotation stored in extData (as populated by CodeIndexService)
        controller.setExtData(ExtDataHelper.setAnnotations(null, List.of("GetMapping")));
        // Signature should NOT contain @GetMapping — that's the old buggy check
        controller.setSignature("handleRequest()");
        st.add(controller);

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        boolean found = report.getDeadSymbols().stream()
                .anyMatch(e -> "pkg.controllerMethod".equals(e.getSymbolId()));
        boolean foundSuspicious = report.getSuspiciousSymbols().stream()
                .anyMatch(e -> "pkg.controllerMethod".equals(e.getSymbolId()));
        assertFalse(found, "@GetMapping annotated method should be excluded from dead code");
        assertFalse(foundSuspicious, "@GetMapping annotated method should be excluded from suspicious code");
    }

    @Test
    void testOrmEntityAnnotationInExtDataExcludesSymbol() {
        // AR-68: @Entity annotation checked via extData
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        CodeSymbol entity = new CodeSymbol();
        entity.setId("pkg.myEntity");
        entity.setQualifiedName("pkg.MyEntity");
        entity.setName("MyEntity");
        entity.setKind(CodeSymbolKind.CLASS);
        entity.setAccessModifier(CodeAccessModifier.PUBLIC);
        entity.setSuperClassName("SomeBase");
        // @Entity in extData, not in signature
        entity.setExtData(ExtDataHelper.setAnnotations(null, List.of("Entity")));
        entity.setSignature("MyEntity()");
        st.add(entity);

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        boolean found = report.getDeadSymbols().stream()
                .anyMatch(e -> "pkg.myEntity".equals(e.getSymbolId()));
        boolean foundSuspicious = report.getSuspiciousSymbols().stream()
                .anyMatch(e -> "pkg.myEntity".equals(e.getSymbolId()));
        assertFalse(found, "@Entity annotated class should be excluded from dead code");
        assertFalse(foundSuspicious, "@Entity annotated class should be excluded from suspicious code");
    }

    @Test
    void testPythonDecoratorInExtDataExcludesSymbol() {
        // AR-68: Python decorators checked via extData
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        CodeSymbol propMethod = new CodeSymbol();
        propMethod.setId("pkg.propMethod");
        propMethod.setQualifiedName("pkg.MyClass.my_property");
        propMethod.setName("my_property");
        propMethod.setKind(CodeSymbolKind.METHOD);
        propMethod.setAccessModifier(CodeAccessModifier.PUBLIC);
        propMethod.setExtData(ExtDataHelper.setAnnotations(null, List.of("property")));
        propMethod.setSignature("my_property(self)");
        st.add(propMethod);

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        boolean found = report.getDeadSymbols().stream()
                .anyMatch(e -> "pkg.propMethod".equals(e.getSymbolId()));
        boolean foundSuspicious = report.getSuspiciousSymbols().stream()
                .anyMatch(e -> "pkg.propMethod".equals(e.getSymbolId()));
        assertFalse(found, "@property decorated method should be excluded from dead code");
        assertFalse(foundSuspicious, "@property decorated method should be excluded from suspicious code");
    }

    @Test
    void testSignatureWithoutAnnotationsNotFalsePositive() {
        // AR-68: Ensure methods with annotation-like text in signature but no
        // annotations in extData are NOT excluded (the old bug would exclude them)
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        CodeSymbol method = new CodeSymbol();
        method.setId("pkg.methodWithGetMappingInName");
        method.setQualifiedName("pkg.Utils.getMapping");
        method.setName("getMapping");
        method.setKind(CodeSymbolKind.METHOD);
        method.setAccessModifier(CodeAccessModifier.PRIVATE);
        // No annotations in extData
        method.setSignature("getMapping()");
        st.add(method);

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        boolean found = report.getDeadSymbols().stream()
                .anyMatch(e -> "pkg.methodWithGetMappingInName".equals(e.getSymbolId()));
        assertTrue(found, "Method without actual @GetMapping annotation should be detected as dead");
    }

    @Test
    void testMultipleAnnotationsInExtData() {
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        CodeSymbol bizMethod = new CodeSymbol();
        bizMethod.setId("pkg.bizMethod");
        bizMethod.setQualifiedName("pkg.MyBizModel.doAction");
        bizMethod.setName("doAction");
        bizMethod.setKind(CodeSymbolKind.METHOD);
        bizMethod.setAccessModifier(CodeAccessModifier.PUBLIC);
        bizMethod.setExtData(ExtDataHelper.setAnnotations(null,
                List.of("BizModel", "BizAction", "Inject")));
        bizMethod.setSignature("doAction()");
        st.add(bizMethod);

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        boolean found = report.getDeadSymbols().stream()
                .anyMatch(e -> "pkg.bizMethod".equals(e.getSymbolId()));
        boolean foundSuspicious = report.getSuspiciousSymbols().stream()
                .anyMatch(e -> "pkg.bizMethod".equals(e.getSymbolId()));
        assertFalse(found, "@BizAction annotated method should be excluded from dead code");
        assertFalse(foundSuspicious, "@BizAction annotated method should be excluded from suspicious code");
    }

    @Test
    void testExtDataHelperRoundTrip() {
        // Verify ExtDataHelper can set and get annotations correctly
        String extData = ExtDataHelper.setAnnotations(null, List.of("GetMapping", "Scheduled"));
        List<String> annotations = ExtDataHelper.getAnnotations(extData);
        assertEquals(2, annotations.size());
        assertTrue(annotations.contains("GetMapping"));
        assertTrue(annotations.contains("Scheduled"));
    }

    @Test
    void testExtDataHelperPreservesExistingData() {
        // Setting annotations should preserve existing extData keys
        String existing = "{\"filePath\":\"/src/Main.java\"}";
        String extData = ExtDataHelper.setAnnotations(existing, List.of("Entity"));
        List<String> annotations = ExtDataHelper.getAnnotations(extData);
        assertEquals(1, annotations.size());
        assertTrue(annotations.contains("Entity"));
        // filePath should still be extractable
        assertEquals("/src/Main.java", ExtDataHelper.extractFilePath(extData));
    }

    @Test
    void testExtDataHelperGetAnnotationsReturnsEmptyOnNull() {
        assertTrue(ExtDataHelper.getAnnotations(null).isEmpty());
        assertTrue(ExtDataHelper.getAnnotations("").isEmpty());
        assertTrue(ExtDataHelper.getAnnotations("{}").isEmpty());
        assertTrue(ExtDataHelper.getAnnotations("{\"filePath\":\"/a.java\"}").isEmpty());
    }

    // ==================== AR-84 Regression Tests (exact annotation matching) ====================

    @Test
    void testExactBeanAnnotationExcludesFromDeadCode() {
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        CodeSymbol beanMethod = new CodeSymbol();
        beanMethod.setId("pkg.beanMethod");
        beanMethod.setQualifiedName("pkg.Config.myBean");
        beanMethod.setName("myBean");
        beanMethod.setKind(CodeSymbolKind.METHOD);
        beanMethod.setAccessModifier(CodeAccessModifier.PUBLIC);
        beanMethod.setExtData(ExtDataHelper.setAnnotations(null, List.of("Bean")));
        st.add(beanMethod);

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        boolean inDead = report.getDeadSymbols().stream().anyMatch(e -> "pkg.beanMethod".equals(e.getSymbolId()));
        assertFalse(inDead, "@Bean (exact match) should be excluded as potentially dynamic");
    }

    @Test
    void testSubstringBeanAnnotationNotExcluded() {
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        CodeSymbol someBeanMethod = new CodeSymbol();
        someBeanMethod.setId("pkg.someBeanMethod");
        someBeanMethod.setQualifiedName("pkg.Utils.someBeanMethod");
        someBeanMethod.setName("someBeanMethod");
        someBeanMethod.setKind(CodeSymbolKind.METHOD);
        someBeanMethod.setAccessModifier(CodeAccessModifier.PRIVATE);
        someBeanMethod.setExtData(ExtDataHelper.setAnnotations(null, List.of("SomeBeanAnnotation")));
        st.add(someBeanMethod);

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        boolean inDead = report.getDeadSymbols().stream().anyMatch(e -> "pkg.someBeanMethod".equals(e.getSymbolId()));
        assertTrue(inDead, "Annotation 'SomeBeanAnnotation' should NOT match 'Bean' with exact equals");
    }

    @Test
    void testSubstringServiceAnnotationNotExcluded() {
        CallGraph cg = new CallGraph();
        SymbolTable st = new SymbolTable();

        CodeSymbol method = new CodeSymbol();
        method.setId("pkg.customServiceMethod");
        method.setQualifiedName("pkg.Utils.customServiceMethod");
        method.setName("customServiceMethod");
        method.setKind(CodeSymbolKind.METHOD);
        method.setAccessModifier(CodeAccessModifier.PRIVATE);
        method.setExtData(ExtDataHelper.setAnnotations(null, List.of("MyServiceCustom")));
        st.add(method);

        DeadCodeReport report = detector.detectDeadCode("test-idx", st, cg);

        boolean inDead = report.getDeadSymbols().stream().anyMatch(e -> "pkg.customServiceMethod".equals(e.getSymbolId()));
        assertTrue(inDead, "Annotation 'MyServiceCustom' should NOT match 'Service' with exact equals");
    }
}
