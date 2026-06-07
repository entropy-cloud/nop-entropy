package io.nop.code.lang.java;

import io.nop.code.core.model.*;
import io.nop.code.lang.java.analyzer.JavaFileAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestProvenanceAndSpringRoutes {

    private final JavaFileAnalyzer analyzer = new JavaFileAnalyzer();

    @Test
    void testMethodCallsHaveProvenance() {
        String code = "public class Foo { public void bar() { baz(); } public void baz() {} }";
        CodeFileAnalysisResult result = analyzer.analyze("Foo.java", code);
        assertNotNull(result);
        for (CodeMethodCall call : result.getCalls()) {
            assertNotNull(call.getProvenance(), "All calls should have provenance");
            assertEquals(EdgeProvenance.SYMBOL_SOLVER, call.getProvenance());
        }
    }

    @Test
    void testInheritancesHaveProvenance() {
        String code = "public class Foo implements Runnable { public void run() {} }";
        CodeFileAnalysisResult result = analyzer.analyze("Foo.java", code);
        assertNotNull(result);
        for (CodeInheritance inh : result.getInheritances()) {
            assertNotNull(inh.getProvenance(), "All inheritances should have provenance");
            assertEquals(EdgeProvenance.AST_EXTRACTION, inh.getProvenance());
        }
    }

    @Test
    void testAnnotationUsagesHaveProvenance() {
        String code = "@Deprecated public class Foo {}";
        CodeFileAnalysisResult result = analyzer.analyze("Foo.java", code);
        assertNotNull(result);
        for (CodeAnnotationUsage usage : result.getAnnotationUsages()) {
            assertNotNull(usage.getProvenance(), "All annotation usages should have provenance");
            assertEquals(EdgeProvenance.AST_EXTRACTION, usage.getProvenance());
        }
    }

    @Test
    void testSpringRouteExtraction() {
        String code = "import org.springframework.web.bind.annotation.*;\n"
                + "@RequestMapping(path = \"/api\")\n"
                + "public class UserController {\n"
                + "    @GetMapping(\"/users/{id}\")\n"
                + "    public String getUser() { return \"user\"; }\n"
                + "    @PostMapping(\"/users\")\n"
                + "    public String createUser() { return \"created\"; }\n"
                + "    @PutMapping(\"/users/{id}\")\n"
                + "    public String updateUser() { return \"updated\"; }\n"
                + "    @DeleteMapping(\"/users/{id}\")\n"
                + "    public String deleteUser() { return \"deleted\"; }\n"
                + "}\n";
        CodeFileAnalysisResult result = analyzer.analyze("UserController.java", code);
        assertNotNull(result);
        List<CodeRouteInfo> routes = result.getRoutes();
        assertFalse(routes.isEmpty(), "Should extract Spring routes");
        assertEquals(4, routes.size());

        CodeRouteInfo getRoute = routes.stream()
                .filter(r -> "GET".equals(r.getHttpMethod()))
                .findFirst().orElse(null);
        assertNotNull(getRoute);
        assertEquals("/api/users/{id}", getRoute.getRoutePath());

        CodeRouteInfo postRoute = routes.stream()
                .filter(r -> "POST".equals(r.getHttpMethod()))
                .findFirst().orElse(null);
        assertNotNull(postRoute);
        assertEquals("/api/users", postRoute.getRoutePath());
    }

    @Test
    void testSpringRouteNoPrefix() {
        String code = "import org.springframework.web.bind.annotation.*;\n"
                + "public class SimpleController {\n"
                + "    @GetMapping(\"/hello\")\n"
                + "    public String hello() { return \"hello\"; }\n"
                + "}\n";
        CodeFileAnalysisResult result = analyzer.analyze("SimpleController.java", code);
        assertNotNull(result);
        List<CodeRouteInfo> routes = result.getRoutes();
        assertEquals(1, routes.size());
        assertEquals("/hello", routes.get(0).getRoutePath());
        assertEquals("GET", routes.get(0).getHttpMethod());
    }

    @Test
    void testRouteInfoInHandlerExtData() {
        String code = "import org.springframework.web.bind.annotation.*;\n"
                + "@RequestMapping(\"/api\")\n"
                + "public class ApiCtrl {\n"
                + "    @GetMapping(\"/test\")\n"
                + "    public String test() { return \"ok\"; }\n"
                + "}\n";
        CodeFileAnalysisResult result = analyzer.analyze("ApiCtrl.java", code);
        assertNotNull(result);

        CodeSymbol testMethod = result.getSymbols().stream()
                .filter(s -> "test".equals(s.getName()))
                .findFirst().orElse(null);
        assertNotNull(testMethod);
        assertNotNull(testMethod.getExtData());
        assertTrue(testMethod.getExtData().contains("/api/test"));
        assertTrue(testMethod.getExtData().contains("GET"));
    }

    @Test
    void testExportedFlagOnPublicClass() {
        String code = "public class Foo { public void bar() {} private void baz() {} }";
        CodeFileAnalysisResult result = analyzer.analyze("Foo.java", code);
        assertNotNull(result);

        CodeSymbol fooClass = result.getSymbols().stream()
                .filter(s -> "Foo".equals(s.getName()) && s.getKind() == CodeSymbolKind.CLASS)
                .findFirst().orElse(null);
        assertNotNull(fooClass);
        assertTrue(fooClass.isExportedFlag());

        CodeSymbol barMethod = result.getSymbols().stream()
                .filter(s -> "bar".equals(s.getName()) && s.getKind() == CodeSymbolKind.METHOD)
                .findFirst().orElse(null);
        assertNotNull(barMethod);
        assertTrue(barMethod.isExportedFlag());

        CodeSymbol bazMethod = result.getSymbols().stream()
                .filter(s -> "baz".equals(s.getName()) && s.getKind() == CodeSymbolKind.METHOD)
                .findFirst().orElse(null);
        assertNotNull(bazMethod);
        assertFalse(bazMethod.isExportedFlag());
    }
}
