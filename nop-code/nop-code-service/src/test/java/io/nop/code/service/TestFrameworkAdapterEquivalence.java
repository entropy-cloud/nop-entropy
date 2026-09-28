package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.model.CodeRouteInfo;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.util.ExtDataHelper;
import io.nop.code.flow.DeadCodeDetector;
import io.nop.code.flow.DeadCodeReport;
import io.nop.code.flow.ExecutionFlow;
import io.nop.code.flow.FlowDetector;
import io.nop.code.flow.IFlowDetector;
import io.nop.code.flow.SpringEntryPointPatternProvider;
import io.nop.code.lang.java.analyzer.JavaFileAnalyzer;
import io.nop.code.lang.java.convention.IFrameworkRouteConvention;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N5.2 behavior-equivalence proof: the IoC-assembled FlowDetector/DeadCodeDetector must
 * produce exactly the same results as default-constructed instances (the pre-refactor
 * behavior), and the assembled beans must actually hold the injected providers (wiring
 * verification). The JavaFileAnalyzer seam is proven by a custom convention changing the
 * extraction outcome.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestFrameworkAdapterEquivalence extends JunitAutoTestCase {

    @Inject
    IFlowDetector assembledFlowDetector;

    @Inject
    DeadCodeDetector assembledDeadCodeDetector;

    private SymbolTable newSymbolTable() {
        SymbolTable table = new SymbolTable();
        table.add(symbol("UserController.handle", CodeSymbolKind.METHOD, null));
        table.add(symbol("UserController.handleReq", CodeSymbolKind.METHOD, "GetMapping"));
        table.add(symbol("JobScheduler.tick", CodeSymbolKind.METHOD, "Scheduled"));
        table.add(symbol("PlainService.compute", CodeSymbolKind.METHOD, null));
        return table;
    }

    private CodeSymbol symbol(String qualifiedName, CodeSymbolKind kind, String annotation) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId("sym-" + qualifiedName);
        symbol.setKind(kind);
        symbol.setName(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1));
        symbol.setQualifiedName(qualifiedName);
        if (annotation != null) {
            symbol.setExtData(ExtDataHelper.setAnnotations(null, List.of(annotation)));
        }
        return symbol;
    }

    private CallGraph newCallGraph() {
        CallGraph graph = new CallGraph();
        graph.addEdge("UserController.handle", "PlainService.compute");
        graph.addEdge("UserController.handleReq", "PlainService.compute");
        return graph;
    }

    /** Wiring evidence: the container bean must hold the injected provider, not the default. */
    @Test
    void testAssembledFlowDetectorHoldsInjectedProvider() throws Exception {
        Field field = FlowDetector.class.getDeclaredField("patternProviders");
        field.setAccessible(true);
        List<?> providers = (List<?>) field.get(assembledFlowDetector);
        assertNotNull(providers, "container bean must have providers collected");
        assertFalse(providers.isEmpty(), "collect-beans must have found the provider bean");
        assertTrue(providers.stream().anyMatch(p -> p instanceof SpringEntryPointPatternProvider),
                "assembled providers must contain the Spring provider bean");
    }

    /** Wiring evidence: FQN annotation patterns must be normalized to short names. */
    @Test
    void testAssembledDeadCodeDetectorUsesNormalizedShortNames() throws Exception {
        Field field = DeadCodeDetector.class.getDeclaredField("frameworkAnnotations");
        field.setAccessible(true);
        Set<?> annotations = (Set<?>) field.get(assembledDeadCodeDetector);
        assertNotNull(annotations);
        assertTrue(annotations.contains("GetMapping"),
                "provider FQN must be normalized to the short name, got: " + annotations);
        assertFalse(annotations.contains("org.springframework.web.bind.annotation.GetMapping"),
                "raw FQN must not leak into the short-name match set");
    }

    /** Equivalence: assembled vs default FlowDetector produce identical flows. */
    @Test
    void testFlowDetectorAssembledEqualsDefault() {
        SymbolTable symbolTable = newSymbolTable();
        CallGraph callGraph = newCallGraph();

        List<ExecutionFlow> assembled =
                assembledFlowDetector.detectFlows("n52-flow-eq", symbolTable, callGraph);
        List<ExecutionFlow> defaults =
                new FlowDetector().detectFlows("n52-flow-eq", symbolTable, callGraph);

        assertEquals(defaults.stream().map(ExecutionFlow::getEntryPointSymbolId).collect(Collectors.toList()),
                assembled.stream().map(ExecutionFlow::getEntryPointSymbolId).collect(Collectors.toList()),
                "assembled FlowDetector must detect the same entry points as the default");
        assertEquals(defaults.size(), assembled.size());
    }

    /** Equivalence: assembled vs default DeadCodeDetector produce identical reports. */
    @Test
    void testDeadCodeDetectorAssembledEqualsDefault() {
        SymbolTable symbolTable = newSymbolTable();
        CallGraph callGraph = newCallGraph();

        DeadCodeReport assembled =
                assembledDeadCodeDetector.detectDeadCode("n52-dead-eq", symbolTable, callGraph);
        DeadCodeReport defaults =
                new DeadCodeDetector().detectDeadCode("n52-dead-eq", symbolTable, callGraph);

        assertEquals(names(defaults.getDeadSymbols()), names(assembled.getDeadSymbols()),
                "assembled detector must report the same dead symbols");
        assertEquals(names(defaults.getSuspiciousSymbols()), names(assembled.getSuspiciousSymbols()),
                "assembled detector must report the same suspicious symbols");
    }

    private List<String> names(List<DeadCodeReport.DeadCodeEntry> entries) {
        if (entries == null) return List.of();
        return entries.stream().map(DeadCodeReport.DeadCodeEntry::getQualifiedName)
                .sorted().collect(Collectors.toList());
    }

    /** Seam evidence: a custom convention actually drives route extraction. */
    @Test
    void testRouteConventionSeamChangesExtraction() {
        String source = """
                package demo;

                import org.springframework.web.bind.annotation.*;

                @RequestMapping("/demo")
                public class DemoController {
                    @GetMapping("/list")
                    public String list() { return "x"; }
                }
                """;

        JavaFileAnalyzer analyzer = newAnalyzer();
        List<CodeRouteInfo> defaultRoutes = analyzer.analyze("demo/DemoController.java", source).getRoutes();
        assertEquals(1, defaultRoutes.size(), "default Spring convention must extract the route");
        assertEquals("/demo/list", defaultRoutes.get(0).getRoutePath(),
                "class prefix must be applied");

        IFrameworkRouteConvention noPrefix = new IFrameworkRouteConvention() {
            @Override
            public Set<String> mappingAnnotations() {
                return Set.of("GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping");
            }

            @Override
            public Set<String> classPrefixAnnotations() {
                return Set.of();
            }

            @Override
            public String httpMethodFor(String mappingAnnotationName) {
                return "GET";
            }
        };
        JavaFileAnalyzer custom = newAnalyzer();
        custom.setRouteConvention(noPrefix);
        List<CodeRouteInfo> customRoutes = custom.analyze("demo/DemoController.java", source).getRoutes();
        assertEquals(1, customRoutes.size());
        assertEquals("/list", customRoutes.get(0).getRoutePath(),
                "custom convention without class prefix must change the extraction outcome");

        assertEquals("GET", defaultRoutes.get(0).getHttpMethod(),
                "httpMethod must come from the convention mapping");
    }

    private JavaFileAnalyzer newAnalyzer() {
        return new JavaFileAnalyzer();
    }
}
