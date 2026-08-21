package io.nop.xlang.java.compare;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.java.translator.ExecToJavaTranslator;
import io.nop.xlang.java.translator.GeneratedJavaSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 夹具反漂移断言（I10 Phase 1 D6）：提交的生成类夹具源码 == 转译器对 corpus 树的**当前**输出
 * （逐串相等）——转译器演进致夹具漂移即红灯；同步 = 手动运行
 * {@code io.nop.xlang.java.compare.GeneratedFixtureMain} 再生成。
 */
public class TestGeneratedFixtureSources {

    private static final ExecToJavaTranslator TRANSLATOR = new ExecToJavaTranslator();

    private static Path fixtureDir;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        fixtureDir = GeneratedFixtureMain.resolveFixtureDir();
        assertTrue(Files.isDirectory(fixtureDir), "fixture dir exists: " + fixtureDir);
    }

    static Stream<String> corpusUnitPaths() {
        return ProductionBindingCorpus.staticUnits().stream()
                .map(ProductionBindingCorpus.StaticUnit::getPath);
    }

    @ParameterizedTest(name = "fixture-sync:{0}")
    @MethodSource("corpusUnitPaths")
    public void testCorpusFixtureMatchesTranslatorOutput(String path) {
        assertFixtureInSync(path);
    }

    @Test
    public void testTenantUnitFixtureMatchesTranslatorOutput() {
        assertFixtureInSync(TenantBindingTestUnit.PATH);
    }

    @Test
    public void testFixtureCountMatchesCorpusPlusTenant() throws IOException {
        long genCount;
        try (Stream<Path> files = Files.list(fixtureDir)) {
            genCount = files.filter(p -> p.getFileName().toString().startsWith("Gen_")).count();
        }
        // corpus 静态单元 + 租户单元（I10）+ xlib 标签夹具（I11，反漂移护栏见 TestTagFixtureSources）
        assertEquals(ProductionBindingCorpus.staticUnits().size() + 2L, genCount,
                "fixtures = corpus static units + tenant unit + tag fixture (no stale fixtures allowed)");
    }

    private void assertFixtureInSync(String path) {
        ProductionBindingCorpus.StaticUnit unit = unitOf(path);
        IExecutableExpression tree = ProductionBindingCorpus.compileCanonicalTree(unit);
        GeneratedJavaSource source = TRANSLATOR.translate(path, tree);

        Path fixture = fixtureDir.resolve(source.getClassName()
                .substring(source.getClassName().lastIndexOf('.') + 1) + ".java");
        assertTrue(Files.isRegularFile(fixture), "fixture source missing: " + fixture
                + " (regenerate via io.nop.xlang.java.compare.GeneratedFixtureMain)");
        String actual;
        try {
            actual = Files.readString(fixture, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("read fixture failed: " + fixture, e);
        }
        assertEquals(GeneratedFixtureMain.HEADER + source.getCode(), actual,
                "fixture drifted from translator output (regenerate via GeneratedFixtureMain): " + path);
    }

    private static ProductionBindingCorpus.StaticUnit unitOf(String path) {
        List<ProductionBindingCorpus.StaticUnit> units = ProductionBindingCorpus.staticUnits();
        return units.stream()
                .filter(u -> u.getPath().equals(path))
                .findFirst()
                .orElseGet(() -> path.equals(TenantBindingTestUnit.PATH)
                        ? ProductionBindingCorpus.standalone(TenantBindingTestUnit.PATH,
                                TenantBindingTestUnit.BASE_SOURCE, TenantBindingTestUnit.MODE)
                        : null);
    }
}
