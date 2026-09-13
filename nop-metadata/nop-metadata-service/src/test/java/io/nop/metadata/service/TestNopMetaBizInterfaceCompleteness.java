package io.nop.metadata.service;

import io.nop.metadata.biz.INopMetaDataContractBiz;
import io.nop.metadata.biz.INopMetaDataProductBiz;
import io.nop.metadata.biz.INopMetaDataSourceBiz;
import io.nop.metadata.biz.INopMetaLineageEdgeBiz;
import io.nop.metadata.biz.INopMetaModuleBiz;
import io.nop.metadata.biz.INopMetaProfilingRuleBiz;
import io.nop.metadata.biz.INopMetaQualityCheckpointBiz;
import io.nop.metadata.biz.INopMetaQualityResultBiz;
import io.nop.metadata.biz.INopMetaQualityRuleBiz;
import io.nop.metadata.biz.INopMetaQualityScoreBiz;
import io.nop.metadata.biz.INopMetaReconciliationConfigBiz;
import io.nop.metadata.biz.INopMetaReconciliationResultBiz;
import io.nop.metadata.biz.INopMetaTableBiz;
import io.nop.metadata.biz.INopMetaTagLabelBiz;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2026-07-19-1250-3 Phase 1 Proof：验证至少 7 个 I*Biz 接口（实际验证 9 个）包含全部自定义方法签名。
 *
 * <p>Anti-Hollow 验证：不只是接口存在，方法签名也必须在接口上声明（接口可被跨模块 @Inject 调用）。
 * 接线验证：通过反射确认接口声明的方法名 + 参数个数与契约一致。
 *
 * <p>plan 2026-08-16-0549-2 Phase 1（P2-17）：覆盖面扩至全部非空 I*Biz（live 2026-08-16 重扫 = 14），
 * 并增加程序化全集守卫（{@link #testCoverageListMatchesFilesystemScanOfAllBizInterfaces}）——
 * 新增 I*Biz 接口或既有接口新增自定义方法而未登记覆盖清单时测试变红（F19 动态发现形态，
 * 封"新增可静默逃逸"盲区）。守卫发现机制 = 文件系统扫描 nop-metadata-dao 的 biz 包源目录；
 * <b>盲区契约</b>：本守卫只覆盖驻留在 {@code io.nop.metadata.biz} 包的 I*Biz（包约定 +
 * owner doc F15 IBiz 表背书）；接口移包即视为结构性变更需同步守卫。
 */
public class TestNopMetaBizInterfaceCompleteness {

    /**
     * 覆盖清单（P2-17 全集守卫的真值表）：非空 I*Biz 接口名 → 该接口声明的全部自定义方法名集合。
     * live 2026-08-16 重扫定稿：14 个非空接口（扫描命令与输出见当日 daily log）。
     * 新增接口 / 新增自定义方法未登记此表 → {@link #testCoverageListMatchesFilesystemScanOfAllBizInterfaces} 红。
     */
    private static final Map<String, Set<String>> COVERAGE = Map.ofEntries(
            Map.entry("INopMetaTableBiz", Set.of("profileTable", "createSqlTable", "previewSqlFields",
                    "resolveTableFields", "queryTableData", "queryJoinData", "queryAggregation")),
            Map.entry("INopMetaDataSourceBiz", Set.of("bindCredential", "unbindCredential", "migrateDataSourcesCredential", "testConnection", "syncExternalTables",
                    "collectCatalog", "collectCatalogForTable")),
            Map.entry("INopMetaModuleBiz", Set.of("importOrmModel", "importOrmModels",
                    "releaseModule", "generateManifest")),
            Map.entry("INopMetaLineageEdgeBiz", Set.of("recordLineage", "extractLineageFromSql",
                    "extractColumnLineageFromSql", "extractMeasureLineage", "getUpstream",
                    "getDownstream", "getLineagePath", "getImpactAnalysis")),
            Map.entry("INopMetaQualityRuleBiz", Set.of("executeQualityRule",
                    "executeQualityRulesForDataSource", "judgeByRuleId")),
            Map.entry("INopMetaQualityCheckpointBiz", Set.of("executeCheckpoint")),
            Map.entry("INopMetaQualityScoreBiz", Set.of("computeQualityScore")),
            Map.entry("INopMetaDataContractBiz", Set.of("checkContract", "checkContractReadOnly")),
            Map.entry("INopMetaDataProductBiz", Set.of("linkAsset", "unlinkAsset", "getLinkedAssets")),
            Map.entry("INopMetaQualityResultBiz", Set.of("approve", "reject")),
            Map.entry("INopMetaProfilingRuleBiz", Set.of("executeProfilingRule")),
            Map.entry("INopMetaTagLabelBiz", Set.of("propagateTags", "suggestTags")),
            Map.entry("INopMetaReconciliationConfigBiz", Set.of("executeReconciliation")),
            Map.entry("INopMetaReconciliationResultBiz", Set.of("confirmMatch", "batchConfirmMatches")));

    /**
     * 验证全部非空 I*Biz 接口（live 2026-08-16 重扫 = 14 个）的全部自定义方法签名存在（精度层：方法名 + 参数个数下限）。
     *
     * <p>覆盖：Table / DataSource / Module / LineageEdge / QualityScore / QualityCheckpoint /
     * QualityRule（plan 2026-07-19-1250-3 必需 7 个）+ DataContract / ProfilingRule /
     * QualityResult / DataProduct / TagLabel + ReconciliationConfig / ReconciliationResult
     * （P2-17 补齐，全集 = 14）。全集一致性由
     * {@link #testCoverageListMatchesFilesystemScanOfAllBizInterfaces} 守卫。
     */
    @Test
    public void testRequiredInterfacesContainCustomMethods() {
        assertDeclaresMethod(INopMetaTableBiz.class, "profileTable", 4);
        assertDeclaresMethod(INopMetaTableBiz.class, "createSqlTable", 6);
        assertDeclaresMethod(INopMetaTableBiz.class, "previewSqlFields", 2);
        assertDeclaresMethod(INopMetaTableBiz.class, "resolveTableFields", 2);
        assertDeclaresMethod(INopMetaTableBiz.class, "queryTableData", 6);
        assertDeclaresMethod(INopMetaTableBiz.class, "queryJoinData", 7);
        assertDeclaresMethod(INopMetaTableBiz.class, "queryAggregation", 11);

        assertDeclaresMethod(INopMetaDataSourceBiz.class, "testConnection", 2);
        assertDeclaresMethod(INopMetaDataSourceBiz.class, "syncExternalTables", 3);
        assertDeclaresMethod(INopMetaDataSourceBiz.class, "collectCatalog", 3);
        assertDeclaresMethod(INopMetaDataSourceBiz.class, "collectCatalogForTable", 3);

        assertDeclaresMethod(INopMetaModuleBiz.class, "importOrmModel", 2);
        assertDeclaresMethod(INopMetaModuleBiz.class, "importOrmModels", 2);
        assertDeclaresMethod(INopMetaModuleBiz.class, "releaseModule", 2);
        assertDeclaresMethod(INopMetaModuleBiz.class, "generateManifest", 2);

        assertDeclaresMethod(INopMetaLineageEdgeBiz.class, "recordLineage", 2);
        assertDeclaresMethod(INopMetaLineageEdgeBiz.class, "extractLineageFromSql", 2);
        assertDeclaresMethod(INopMetaLineageEdgeBiz.class, "extractColumnLineageFromSql", 2);
        assertDeclaresMethod(INopMetaLineageEdgeBiz.class, "extractMeasureLineage", 2);
        assertDeclaresMethod(INopMetaLineageEdgeBiz.class, "getUpstream", 1);
        assertDeclaresMethod(INopMetaLineageEdgeBiz.class, "getDownstream", 1);
        assertDeclaresMethod(INopMetaLineageEdgeBiz.class, "getLineagePath", 2);
        assertDeclaresMethod(INopMetaLineageEdgeBiz.class, "getImpactAnalysis", 2);

        assertDeclaresMethod(INopMetaQualityRuleBiz.class, "executeQualityRule", 3);
        assertDeclaresMethod(INopMetaQualityRuleBiz.class, "executeQualityRulesForDataSource", 3);
        assertDeclaresMethod(INopMetaQualityRuleBiz.class, "judgeByRuleId", 2);

        assertDeclaresMethod(INopMetaQualityCheckpointBiz.class, "executeCheckpoint", 3);

        assertDeclaresMethod(INopMetaQualityScoreBiz.class, "computeQualityScore", 2);

        assertDeclaresMethod(INopMetaDataContractBiz.class, "checkContract", 2);
        assertDeclaresMethod(INopMetaDataContractBiz.class, "checkContractReadOnly", 2);

        assertDeclaresMethod(INopMetaDataProductBiz.class, "linkAsset", 4);
        assertDeclaresMethod(INopMetaDataProductBiz.class, "unlinkAsset", 4);
        assertDeclaresMethod(INopMetaDataProductBiz.class, "getLinkedAssets", 2);

        assertDeclaresMethod(INopMetaQualityResultBiz.class, "approve", 2);
        assertDeclaresMethod(INopMetaQualityResultBiz.class, "reject", 2);

        assertDeclaresMethod(INopMetaProfilingRuleBiz.class, "executeProfilingRule", 3);

        assertDeclaresMethod(INopMetaTagLabelBiz.class, "propagateTags", 4);
        assertDeclaresMethod(INopMetaTagLabelBiz.class, "suggestTags", 3);

        // P2-17（plan 2026-08-16-0549-2）：补齐 live 重扫发现的 2 个未覆盖非空接口
        assertDeclaresMethod(INopMetaReconciliationConfigBiz.class, "executeReconciliation", 2);
        assertDeclaresMethod(INopMetaReconciliationResultBiz.class, "confirmMatch", 4);
        assertDeclaresMethod(INopMetaReconciliationResultBiz.class, "batchConfirmMatches", 3);
    }

    /**
     * P2-17 程序化全集守卫：文件系统扫描 nop-metadata-dao biz 包的全部 {@code INopMeta*Biz.java} 源文件，
     * 经反射取各接口声明的方法集；断言（a）非空接口集合与 {@link #COVERAGE} 键集完全一致（新接口不可
     * 静默逃逸，删除接口需同步清单）；（b）每个接口的方法名集合与清单完全一致（新增自定义方法未登记即红）；
     * （c）≥N sanity（N = live 2026-08-16 重扫实数 14）。
     *
     * <p>方法集取自 {@code Class.getDeclaredMethods()}（= 接口自身声明的方法，不含 ICrudBiz 继承面），
     * 避免 Java 源码文本解析的脆弱性；接口全集发现钉死为源目录扫描（TestLimitTargetSetCompleteness
     * 目录行走 + 路径回退先例）。
     */
    @Test
    public void testCoverageListMatchesFilesystemScanOfAllBizInterfaces() throws Exception {
        java.io.File bizDir = locateBizInterfaceSourceDir();
        java.io.File[] files = bizDir.listFiles((d, name) -> name.matches("INopMeta.*Biz\\.java"));
        assertNotNull(files, "biz interface source dir must be listable: " + bizDir.getAbsolutePath());
        assertTrue(files.length >= COVERAGE.size(),
                "scanned interface file count (" + files.length + ") must be >= coverage list size ("
                        + COVERAGE.size() + ")");

        Map<String, Set<String>> scanned = new TreeMap<>();
        for (java.io.File f : files) {
            String simpleName = f.getName().substring(0, f.getName().length() - ".java".length());
            Class<?> iface = Class.forName("io.nop.metadata.biz." + simpleName);
            Set<String> methods = new TreeSet<>();
            for (Method m : iface.getDeclaredMethods()) {
                methods.add(m.getName());
            }
            if (!methods.isEmpty()) {
                scanned.put(simpleName, methods);
            }
        }

        assertEquals(new TreeSet<>(COVERAGE.keySet()), new TreeSet<>(scanned.keySet()),
                "Coverage list must exactly match the filesystem-scanned non-empty INopMeta*Biz set.\n"
                        + "Coverage only: " + diff(COVERAGE.keySet(), scanned.keySet()) + "\n"
                        + "Scan only (new interface must be registered in COVERAGE): "
                        + diff(scanned.keySet(), COVERAGE.keySet()));
        for (Map.Entry<String, Set<String>> e : COVERAGE.entrySet()) {
            assertEquals(new TreeSet<>(e.getValue()), scanned.get(e.getKey()),
                    "Method set of " + e.getKey() + " must match coverage list.\n"
                            + "Coverage only: " + diff(e.getValue(), scanned.get(e.getKey())) + "\n"
                            + "Scan only (new custom method must be registered in COVERAGE): "
                            + diff(scanned.get(e.getKey()), e.getValue()));
        }
        // ≥N sanity：N = live 2026-08-16 重扫实数
        assertTrue(COVERAGE.size() >= 14,
                "non-empty I*Biz coverage must be >= 14 (live rescan 2026-08-16), but was " + COVERAGE.size());
    }

    /**
     * 定位 nop-metadata-dao 的 biz 接口源目录（surefire 工作目录 = service 模块 basedir 时取
     * {@code ../nop-metadata-dao/...}；仓库根执行时回退 {@code nop-metadata/nop-metadata-dao/...}；
     * dao 模块内执行时取 {@code src/main/java/...}）。
     */
    private static java.io.File locateBizInterfaceSourceDir() {
        String rel = "src/main/java/io/nop/metadata/biz";
        String[] candidates = {
                "../nop-metadata-dao/" + rel,
                "nop-metadata/nop-metadata-dao/" + rel,
                rel
        };
        for (String c : candidates) {
            java.io.File dir = new java.io.File(c);
            if (dir.exists() && dir.isDirectory()) {
                return dir;
            }
        }
        throw new IllegalStateException(
                "cannot locate io.nop.metadata.biz source dir (tried ../nop-metadata-dao/, "
                        + "nop-metadata/nop-metadata-dao/, ./ relative to user.dir="
                        + System.getProperty("user.dir") + ")");
    }

    private static String diff(Set<?> expected, Set<?> actual) {
        Set<?> d = new TreeSet<>(expected);
        d.removeAll(actual);
        return d.toString();
    }

    /**
     * 验证 {@link INopMetaTableBiz} 在 IoC 容器中可被注入到 {@code NopMetaReconciliationConfigBizModel}，
     * 因为它通过 {@code @Inject INopMetaTableBiz tableBizModel} 跨模块调用 {@code queryTableData}。
     *
     * <p>接线验证：plan Phase 1 维度07-02 要求 3 处直接 @Inject BizModel 改为接口注入。本测试覆盖
     * NopMetaReconciliationConfigBizModel case（其它 2 处因 cron 触发事务语义保留 raw impl 注入，
     * 已在源码 javadoc 中显式裁定）。
     */
    @Test
    public void testINopMetaTableBizInjectableIntoReconciliationConfigBizModel() throws Exception {
        // 通过反射验证字段类型为接口
        Class<?> reconConfigBizModelClass = Class.forName(
                "io.nop.metadata.service.entity.NopMetaReconciliationConfigBizModel");
        boolean foundInterfaceInjection = false;
        for (java.lang.reflect.Field f : reconConfigBizModelClass.getDeclaredFields()) {
            if (f.getName().equals("tableBizModel")) {
                foundInterfaceInjection = true;
                assertTrue(f.getType().equals(INopMetaTableBiz.class),
                        "tableBizModel field must be typed as INopMetaTableBiz (interface), but was: " + f.getType());
            }
        }
        assertTrue(foundInterfaceInjection, "tableBizModel field must exist on NopMetaReconciliationConfigBizModel");
    }

    /**
     * R2.2 单一事实源裁定 = 保留层 XPL：NopMetaDataContract 的 approve/reject 不再声明在
     * Java 接口（已删除），改由保留层 NopMetaDataContract.xbiz 的 XPL mutation 承载。
     * 本测试程序化钉死 XPL 事实源存在（替换被删除的接口断言，非顺手删除）。
     */
    @Test
    public void testDataContractApproveRejectDeclaredInRetentionXbiz() throws Exception {
        java.io.File f = new java.io.File("src/main/resources/_vfs/nop/metadata/model/NopMetaDataContract/NopMetaDataContract.xbiz");
        if (!f.exists()) {
            f = new java.io.File("nop-metadata/nop-metadata-service/" + f.getPath());
        }
        assertTrue(f.exists(), "retention xbiz file must exist: " + f);
        String content = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(content.contains("<mutation name=\"approve\""),
                "retention NopMetaDataContract.xbiz must declare approve mutation");
        assertTrue(content.contains("<mutation name=\"reject\""),
                "retention NopMetaDataContract.xbiz must declare reject mutation");
        assertTrue(content.contains("/nop/wf/base/approval-support.xbiz"),
                "retention NopMetaDataContract.xbiz must keep approval-support in extends chain");
    }

    private void assertDeclaresMethod(Class<?> iface, String methodName, int paramCount) {
        assertNotNull(iface, "interface class must not be null");
        Method found = null;
        for (Method m : iface.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                found = m;
                break;
            }
        }
        assertNotNull(found, iface.getSimpleName() + " must declare method: " + methodName);
        // IServiceContext 是末参；其余业务参数 + selection（如有）
        assertTrue(found.getParameterCount() >= paramCount,
                iface.getSimpleName() + "." + methodName + " must have at least " + paramCount
                        + " params, but has " + found.getParameterCount());
    }
}
