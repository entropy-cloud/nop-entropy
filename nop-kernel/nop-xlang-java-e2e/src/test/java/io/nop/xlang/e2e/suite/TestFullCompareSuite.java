package io.nop.xlang.e2e.suite;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.backend.EvalStaticBoundExecutable;
import io.nop.xlang.compare.ColumnOutcome;
import io.nop.xlang.compare.ColumnSkipRecord;
import io.nop.xlang.compare.CompareBackendIds;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.CompareUnitReport;
import io.nop.xlang.compare.ExecCompareHarness;
import io.nop.xlang.compare.InterpreterBackendColumn;
import io.nop.xlang.java.backend.JavaEvalExecutionBackend;
import io.nop.xlang.java.gen.EvalMethodConvention;
import io.nop.xlang.java.gen.GeneratedManifestFiles;
import io.nop.xlang.truffle.nodes.XLangRootNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全量三后端对拍套件——单一可复跑入口（I12 Phase 1，roadmap 验收第一项的直接执行收口）。
 *
 * <p>三列（解释器 / java 生产绑定 / truffle）全 corpus **直接执行**（48 静态物化单元 +
 * 26 动态单元 = 74），逐单元经 I1 对拍 harness 三层断言（返回值 typedEquals / 副作用 /
 * 异常语义）+ 后端身份断言（java = 确定性生成类入口 Method / truffle = 翻译 AST 经
 * CallTarget / 解释器 = 非 bound 执行体）+ 列间交叉比对；列缺席 = 红灯（skipRecords 空），
 * 防漏枚举失败显式失败（No Silent No-Op）。非引用形式：不引用既有列测试结果。
 *
 * <p>复跑入口：mission.json {@code test} 命令（
 * {@code ./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle,:nop-xlang-java-e2e -am -T 1C}，
 * 本套件随 nop-xlang-java-e2e 模块执行）。
 */
public class TestFullCompareSuite {

    private static ExecCompareHarness harness;
    private static SuiteColumns.SuiteTruffleColumn truffleColumn;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        assertTrue(GeneratedManifestFiles.installSupplies(TestFullCompareSuite.class.getClassLoader()),
                "classpath dual manifests (build pipeline products) must be present");
        assertTrue(JavaEvalExecutionBackend.instance().isAvailable(),
                "java backend must be available (manifests loaded)");

        harness = ExecCompareHarness.withInterpreterBaseline();
        harness.registerColumn(new SuiteColumns.ProductionJavaColumn());
        harness.registerIdentityRule(new SuiteColumns.JavaIdentityRule());
        truffleColumn = new SuiteColumns.SuiteTruffleColumn();
        harness.registerColumn(truffleColumn);
        harness.registerIdentityRule(new SuiteColumns.TruffleIdentityRule());
        harness.setCompiler(new E2eCorpusUnits.SuiteCompiler());
    }

    @AfterAll
    public static void destroy() {
        if (truffleColumn != null)
            truffleColumn.close();
        GeneratedManifestFiles.installSupplies(TestFullCompareSuite.class.getClassLoader());
        CoreInitialization.destroy();
    }

    static Stream<CompareUnit> units() {
        return E2eCorpusUnits.allUnits().stream();
    }

    // ---- 三列全 corpus 直接执行（主体） ----

    @ParameterizedTest(name = "{0}")
    @MethodSource("units")
    public void testFullCompare(CompareUnit unit) {
        CompareUnitReport report = harness.runUnit(unit);

        // 单元整体判定（三层断言 + 身份断言 + 列间交叉比对，任一失败即红）
        assertTrue(report.isPassed(), "unit diverged: " + report);

        // 列适用性：静态三列、动态两列（java 列不适用为既定约定——缺席期望列显式红灯）
        boolean staticUnit = unit.getKind() == CompareUnitKind.STATIC;
        int expectedColumns = staticUnit ? 3 : 2;
        assertEquals(expectedColumns, report.getColumnOutcomes().size(),
                "executed column count for " + unit.getName() + ": " + report);
        assertTrue(report.getSkipRecords().isEmpty(),
                "no expected column may be absent: " + report.getSkipRecords());

        Map<String, ColumnOutcome> byBackend = new LinkedHashMap<>();
        for (ColumnOutcome outcome : report.getColumnOutcomes())
            byBackend.put(outcome.getBackendId(), outcome);
        assertTrue(byBackend.containsKey(CompareBackendIds.INTERPRETER));
        assertTrue(byBackend.get(CompareBackendIds.INTERPRETER).isPassed());
        assertTrue(byBackend.containsKey(CompareBackendIds.TRUFFLE));
        assertTrue(byBackend.get(CompareBackendIds.TRUFFLE).isPassed());
        assertEquals(staticUnit, byBackend.containsKey(CompareBackendIds.JAVA),
                "java column applies to static units only: " + unit.getName());
        if (staticUnit) {
            assertTrue(byBackend.get(CompareBackendIds.JAVA).isPassed());
            assertWiring(unit, report, byBackend);
        }
    }

    /** 接线验证：三列执行体各归其位（非仅类型存在——证据逐列核验） */
    private void assertWiring(CompareUnit unit, CompareUnitReport report,
                              Map<String, ColumnOutcome> byBackend) {
        IExecutableExpression tree = report.getCompiledTree();
        // 解释器列 = 非 bound 执行体（干净解析取树自身，经全局执行器）
        Object interpArtifact = byBackend.get(CompareBackendIds.INTERPRETER).getExecution()
                .getEvidence().getExecutedArtifact();
        assertTrue(interpArtifact == tree && !(interpArtifact instanceof EvalStaticBoundExecutable),
                "interpreter column must execute the clean-parsed (non-bound) tree: " + unit.getName());
        // java 列 = 生成类实例入口（生产绑定路径产物）
        Object javaArtifact = byBackend.get(CompareBackendIds.JAVA).getExecution()
                .getEvidence().getExecutedArtifact();
        String expectedFqn = EvalMethodConvention.GENERATED_PACKAGE + '.'
                + EvalMethodConvention.generatedClassName(unit.getSourceLocationPath());
        assertEquals(expectedFqn, javaArtifact.getClass().getName(),
                "java column must execute the generated class instance: " + unit.getName());
        // truffle 列 = 翻译 AST（sourceTree 即请求树）经 CallTarget
        Object truffleArtifact = byBackend.get(CompareBackendIds.TRUFFLE).getExecution()
                .getEvidence().getExecutedArtifact();
        XLangRootNode root = assertInstanceOf(XLangRootNode.class, truffleArtifact);
        assertTrue(root.getSourceTree() == tree,
                "truffle column must translate the same tree instance: " + unit.getName());
    }

    // ---- 完整性防漏（corpus 新增单元漏跑 / 物化漂移即红灯） ----

    /** corpus 单元计数钉线：静态 48（物化全集）+ 动态 26——corpus 增删未同步套件即红灯 */
    @Test
    public void testCorpusCountsPinned() {
        List<CompareUnit> units = E2eCorpusUnits.allUnits();
        long staticCount = units.stream().filter(u -> u.getKind() == CompareUnitKind.STATIC).count();
        long dynamicCount = units.stream().filter(u -> u.getKind() == CompareUnitKind.DYNAMIC).count();
        assertEquals(48, staticCount, "materialized corpus static units (11 + 20 + 17)");
        assertEquals(26, dynamicCount, "dynamic corpus units (V1 11 + A 13 + B 2)");
        assertEquals(74, units.size(), "full corpus");
    }

    /** 物化完整性与防漏（双向）：套件静态单元 ↔ e2e `_vfs` corpus 资源 ↔ 双清单成员 ↔ 产物类在 classpath */
    @Test
    public void testMaterializationIntegrityGreenPath() {
        Set<String> suiteStaticPaths = E2eCorpusUnits.allUnits().stream()
                .filter(u -> u.getKind() == CompareUnitKind.STATIC)
                .map(CompareUnit::getSourceLocationPath)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> vfsPaths = vfsCorpusXplPaths();
        List<String> violations = corpusIntegrityViolations(vfsPaths, suiteStaticPaths);
        assertTrue(violations.isEmpty(), "materialization integrity violations: " + violations);

        // 双清单成员资格 + 产物类可加载（java 列供给下界——缺一即漏跑）
        Set<String> scanList = JavaEvalExecutionBackend.instance().getStaticScanList();
        for (String path : suiteStaticPaths) {
            assertTrue(scanList.contains(path), "scan list must contain materialized unit: " + path);
            String fqn = EvalMethodConvention.GENERATED_PACKAGE + '.'
                    + EvalMethodConvention.generatedClassName(path);
            try {
                Class.forName(fqn);
            } catch (ClassNotFoundException e) {
                throw new AssertionError("generated class not on classpath for " + path + ": " + fqn, e);
            }
        }
    }

    /** 防漏红/绿对照（负测试）：人为增删 corpus 单元清单 → 完整性检查红灯；恢复 → 绿灯 */
    @Test
    public void testAntiLeakRedGreenTamperedListing() {
        Set<String> suiteStaticPaths = E2eCorpusUnits.allUnits().stream()
                .filter(u -> u.getKind() == CompareUnitKind.STATIC)
                .map(CompareUnit::getSourceLocationPath)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> vfsPaths = vfsCorpusXplPaths();

        // 红：物化侧多出一个孤儿文件（corpus 新增未入套件供给——漏跑形态）
        Set<String> withOrphan = new LinkedHashSet<>(vfsPaths);
        String orphan = E2eCorpusUnits.CORPUS_VFS_BASE + "static/orphan-unit.xpl";
        withOrphan.add(orphan);
        List<String> violations = corpusIntegrityViolations(withOrphan, suiteStaticPaths);
        assertTrue(violations.stream().anyMatch(v -> v.contains(orphan)),
                "orphan materialized unit must be reported: " + violations);

        // 红：套件侧多出一个未物化单元（供给枚举了 corpus 但物化缺失——供给断层形态）
        Set<String> withPhantom = new LinkedHashSet<>(suiteStaticPaths);
        String phantom = E2eCorpusUnits.CORPUS_VFS_BASE + "static-b/phantom-unit.xpl";
        withPhantom.add(phantom);
        violations = corpusIntegrityViolations(vfsPaths, withPhantom);
        assertTrue(violations.stream().anyMatch(v -> v.contains(phantom)),
                "unmaterialized suite unit must be reported: " + violations);

        // 红：删除一个物化文件（corpus 单元被剔除——静默漏跑形态）
        Set<String> shrunk = new LinkedHashSet<>(vfsPaths);
        String removed = E2eCorpusUnits.CORPUS_VFS_BASE + "static/literal-int.xpl";
        assertTrue(shrunk.remove(removed), "fixture file must exist for red-path setup");
        violations = corpusIntegrityViolations(shrunk, suiteStaticPaths);
        assertTrue(violations.stream().anyMatch(v -> v.contains(removed)),
                "removed corpus unit must be reported: " + violations);

        // 绿：恢复（原始集合）→ 零违规
        assertTrue(corpusIntegrityViolations(vfsPaths, suiteStaticPaths).isEmpty(),
                "restored listing must be green");
    }

    /**
     * 完整性检查核（双向 diff + 目录计数）：物化 VFS 文件集 ↔ 套件静态单元路径集；
     * 任何一侧多出/缺失即违规（防漏枚举失败显式失败，非跳过）。
     */
    static List<String> corpusIntegrityViolations(Set<String> vfsXplPaths, Set<String> suiteStaticPaths) {
        List<String> violations = new ArrayList<>();
        for (String path : vfsXplPaths)
            if (!suiteStaticPaths.contains(path))
                violations.add("materialized-but-not-in-suite: " + path);
        for (String path : suiteStaticPaths)
            if (!vfsXplPaths.contains(path))
                violations.add("in-suite-but-not-materialized: " + path);
        Map<String, Long> byDir = vfsXplPaths.stream().collect(Collectors.groupingBy(
                p -> p.substring(0, p.lastIndexOf('/')), Collectors.counting()));
        Map<String, Long> expected = Map.of(
                E2eCorpusUnits.CORPUS_VFS_BASE + "static", 11L,
                E2eCorpusUnits.CORPUS_VFS_BASE + "static-a", 20L,
                E2eCorpusUnits.CORPUS_VFS_BASE + "static-b", 17L);
        if (!expected.equals(byDir))
            violations.add("corpus directory counts drifted: expected=" + expected + ", was=" + byDir);
        return violations;
    }

    /** e2e `_vfs` corpus 下全部 *.xpl 物化资源（VFS 实时枚举） */
    static Set<String> vfsCorpusXplPaths() {
        Set<String> paths = new LinkedHashSet<>();
        for (String dir : List.of("static", "static-a", "static-b")) {
            String dirPath = E2eCorpusUnits.CORPUS_VFS_BASE + dir;
            List<? extends IResource> children = VirtualFileSystem.instance().getChildren(dirPath);
            assertNotNull(children, "VFS corpus directory must be enumerable: " + dirPath);
            for (IResource child : children) {
                if (child.getPath().endsWith(".xpl"))
                    paths.add(child.getPath());
            }
        }
        return paths;
    }

    // ---- 5 个 html 变体单元 + 3 个 TAG_NONE collect 核验点显式记录（D1 裁定的事实记录面） ----

    @Test
    public void testHtmlVariantAndCollectVerifyUnitsRecorded() {
        // 5 个非 html 语义单元在套件中按 html 变体树驱动（原 per-unit mode 语义覆盖由既有列测试保持）
        List<String> htmlVariantInSuite = E2eCorpusUnits.allUnits().stream()
                .filter(u -> u.getKind() == CompareUnitKind.STATIC)
                .map(CompareUnit::getSourceLocationPath)
                .filter(p -> E2eCorpusUnits.HTML_VARIANT_UNITS.stream()
                        .anyMatch(c -> E2eCorpusUnits.vfsPath(c).equals(p)))
                .collect(Collectors.toList());
        assertEquals(5, htmlVariantInSuite.size(), "html-variant units: " + htmlVariantInSuite);
        // 3 个 TAG_NONE collect 单元：html 统一驱动下返回值不变（实测绿——显式核验点在案）
        List<String> collectVerifyInSuite = E2eCorpusUnits.allUnits().stream()
                .filter(u -> u.getKind() == CompareUnitKind.STATIC)
                .map(CompareUnit::getSourceLocationPath)
                .filter(p -> E2eCorpusUnits.COLLECT_VERIFY_UNITS.stream()
                        .anyMatch(c -> E2eCorpusUnits.vfsPath(c).equals(p)))
                .collect(Collectors.toList());
        assertEquals(3, collectVerifyInSuite.size(), "collect verify units: " + collectVerifyInSuite);
    }

    /** java 列入口产物（接线可见性）：静态首单元生成类入口 Method 形态（EvalMethod 约定） */
    @Test
    public void testJavaColumnEntryMethodConvention() throws Exception {
        CompareUnit unit = E2eCorpusUnits.allUnits().stream()
                .filter(u -> u.getKind() == CompareUnitKind.STATIC).findFirst().orElseThrow();
        String fqn = EvalMethodConvention.GENERATED_PACKAGE + '.'
                + EvalMethodConvention.generatedClassName(unit.getSourceLocationPath());
        Class<?> genClass = Class.forName(fqn);
        Method entry = null;
        for (Method m : genClass.getDeclaredMethods()) {
            if (m.getName().equals(EvalMethodConvention.ENTRY_METHOD_NAME)
                    && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                entry = m;
                break;
            }
        }
        assertNotNull(entry, "generated class must expose the EvalMethod-convention static entry");
    }
}
