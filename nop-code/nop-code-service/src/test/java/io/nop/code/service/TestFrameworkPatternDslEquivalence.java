package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.framework.FrameworkPatternDsl;
import io.nop.code.core.framework.FrameworkPatternModel;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.util.ExtDataHelper;
import io.nop.code.flow.ConfigDrivenEntryPointPatternProvider;
import io.nop.code.flow.IEntryPointPatternProvider;
import io.nop.code.flow.SpringEntryPointPatternProvider;
import io.nop.code.lang.java.convention.ConfigDrivenRouteConvention;
import io.nop.code.lang.java.convention.SpringFrameworkRouteConvention;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N5.3: DSL parsing + Spring equivalence. The config-driven implementations loaded from
 * spring.framework-patterns.xml must behave identically to the hardcoded Spring
 * implementations (entry-point decisions per fixture symbol, the full httpMethod mapping,
 * prefix annotations). namePatterns are declarative metadata: exposed via getNamePatterns()
 * but never affecting isEntryPoint.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestFrameworkPatternDslEquivalence extends JunitAutoTestCase {

    private FrameworkPatternModel loadSpringModel() throws Exception {
        try (InputStream is = getClass().getResourceAsStream(
                "/_vfs/nop/code/frameworks/spring.framework-patterns.xml")) {
            assertNotNull(is, "spring.framework-patterns.xml must be on the classpath (flow module resource)");
            String xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            List<FrameworkPatternModel> models = FrameworkPatternDsl.parse(xml);
            assertEquals(1, models.size());
            assertEquals("spring", models.get(0).getName());
            return models.get(0);
        }
    }

    private static void assertNotNull(Object value, String message) {
        org.junit.jupiter.api.Assertions.assertNotNull(value, message);
    }

    private CodeSymbol symbol(String qn, CodeSymbolKind kind, String annotationShortName) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId("sym-" + qn);
        symbol.setKind(kind);
        symbol.setName(qn.substring(qn.lastIndexOf('.') + 1));
        symbol.setQualifiedName(qn);
        if (annotationShortName != null) {
            symbol.setExtData(ExtDataHelper.setAnnotations(null, List.of(annotationShortName)));
        }
        return symbol;
    }

    @Test
    void testParseMalformedInputsThrowExplicitly() {
        assertThrows(IllegalArgumentException.class,
                () -> FrameworkPatternDsl.parse("<unknown-root/>"));
        assertThrows(IllegalArgumentException.class,
                () -> FrameworkPatternDsl.parse("<framework-patterns><framework><entry-point/></framework></framework-patterns>"),
                "missing framework name must fail explicitly");
        assertThrows(IllegalArgumentException.class,
                () -> FrameworkPatternDsl.parse(
                        "<framework-patterns><framework name=\"x\"><bogus-element/></framework></framework-patterns>"),
                "unknown child element must fail explicitly");
        assertThrows(IllegalArgumentException.class,
                () -> FrameworkPatternDsl.parse(
                        "<framework-patterns><framework name=\"x\"><route-convention><mapping method=\"GET\"/></route-convention></framework></framework-patterns>"),
                "mapping without annotation must fail explicitly");
    }

    @Test
    void testConfigDrivenProviderEquivalenceOnEntryPoints() throws Exception {
        FrameworkPatternModel model = loadSpringModel();
        IEntryPointPatternProvider configDriven = new ConfigDrivenEntryPointPatternProvider(model);
        IEntryPointPatternProvider hardcoded = new SpringEntryPointPatternProvider();

        CodeSymbol[] fixtures = {
                symbol("demo.UserController.handle", CodeSymbolKind.METHOD, null),
                symbol("demo.UserController.handle", CodeSymbolKind.CLASS, null),
                symbol("demo.SchedulerJob.tick", CodeSymbolKind.METHOD, "Scheduled"),
                symbol("demo.PlainBean.echo", CodeSymbolKind.METHOD, "GetMapping"),
                symbol("demo.KafkaConsumer.onMessage", CodeSymbolKind.METHOD, null),
                symbol("demo.PlainService.compute", CodeSymbolKind.METHOD, null),
        };

        for (CodeSymbol fixture : fixtures) {
            assertEquals(hardcoded.isEntryPoint(fixture), configDriven.isEntryPoint(fixture),
                    "entry-point decision must be identical for " + fixture.getQualifiedName());
        }
        assertTrue(configDriven.isEntryPoint(fixtures[0]), "controller-suffix method must be an entry point");
        assertTrue(configDriven.isEntryPoint(fixtures[3]), "annotation short name must be matched");
        assertFalse(configDriven.isEntryPoint(fixtures[5]), "plain symbol must not be an entry point");
    }

    @Test
    void testAnnotationPatternsExposed() throws Exception {
        FrameworkPatternModel model = loadSpringModel();
        IEntryPointPatternProvider configDriven = new ConfigDrivenEntryPointPatternProvider(model);
        // order is non-semantic: the hardcoded implementation builds its list from Set.of
        // (arbitrary iteration order), so equivalence is set equality
        java.util.Set<String> expected = new java.util.HashSet<>(
                new SpringEntryPointPatternProvider().getAnnotationPatterns());
        java.util.Set<String> actual = new java.util.HashSet<>(configDriven.getAnnotationPatterns());
        assertEquals(expected, actual);
    }

    @Test
    void testNamePatternsAreDeclarativeMetadataOnly() throws Exception {
        FrameworkPatternModel model = loadSpringModel();
        IEntryPointPatternProvider configDriven = new ConfigDrivenEntryPointPatternProvider(model);
        assertEquals(List.of("main", "handle*", "process*", "onEvent*"), configDriven.getNamePatterns());
        // "main" matches a name pattern, but the class suffix does not qualify:
        // namePatterns must NOT turn it into an entry point
        assertFalse(configDriven.isEntryPoint(symbol("demo.PlainMain.main", CodeSymbolKind.METHOD, null)),
                "namePatterns are declarative metadata and must not affect isEntryPoint");
    }

    @Test
    void testConfigDrivenRouteConventionEquivalence() throws Exception {
        FrameworkPatternModel model = loadSpringModel();
        ConfigDrivenRouteConvention configDriven = new ConfigDrivenRouteConvention(model);
        SpringFrameworkRouteConvention hardcoded = new SpringFrameworkRouteConvention();

        assertEquals(hardcoded.mappingAnnotations(), configDriven.mappingAnnotations());
        assertEquals(hardcoded.classPrefixAnnotations(), configDriven.classPrefixAnnotations());
        for (String annotation : hardcoded.mappingAnnotations()) {
            assertEquals(hardcoded.httpMethodFor(annotation), configDriven.httpMethodFor(annotation),
                    "httpMethod mapping must be identical for " + annotation);
        }
        assertEquals("", configDriven.httpMethodFor("RequestMapping"),
                "RequestMapping maps to empty method explicitly");
        assertEquals("", configDriven.httpMethodFor("UnknownMapping"));
    }
}
