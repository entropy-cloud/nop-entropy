package io.nop.code.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeGraphMetric;
import io.nop.code.api.dto.CommunityDetectionResultDTO;
import io.nop.code.api.dto.GraphDiffDTO;
import io.nop.dao.api.IDaoProvider;
import io.nop.graph.api.CommunityResult;
import io.nop.orm.IOrmTemplate;
import io.nop.dao.txn.ITransactionTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.6-W-04 守卫面（plan 2530 Phase 2 组 B / 设计裁定③；CS-R2-001 吸收面 + C5-R2-007
 * 收敛载体——6.3 侧登记的同一物理站点集，红② 即其验收面，不设独立红钉）。测试类落
 * impl 包（io.nop.code.service.impl，TestShardBuildCoordinator 先例）以获得包内构造器与
 * 包内可见缝；类名 W_04 归属 5.6 单元（与 6.3-W-04 同号异站防混：5.6-W-04 = CS-001 族）。
 *
 * <p>spy 零 mockito（R1 Minor-4——两模块 test classpath 无 mockito）：手工代理形态——
 * 计数 CallGraph 子类覆写 {@code getCallees}（CodeCallGraph.getOutEdges 的唯一委托通道，
 * 即 LeidenDetector.detect 图转换的唯一图访问入口），经包内 CodeCacheManager 子类（覆写
 * getOrRebuildCallGraph/getOrRebuildSymbolTable）注入；materializer 面以既有包内可见
 * communityRows 捕获（门下计数缝，Major-2 不变式——禁覆写 materialize 本身）。
 *
 * <p>三驱动器（裁定④ 红② 行）：(1) sink 面——runCommunityDetection 守卫下沉（4 个无守卫
 * 调用面 :121/:476/:495/:521 一处封死）：超 10⁴ 节点图不进入 Leiden（getCallees 计数 == 0）
 * + degrade 形态（totalCommunities==0 空结果）；(2) materializer 面——detectCommunities
 * 守卫返回 null（COMMUNITY 缺行走既有 null 链）；(3) diffGraph 双侧——target 侧对称早退
 * 返回空 GraphDiffDTO + baseline 侧既有早退零回退锚。
 *
 * <p>红跑成本口径（R1 Minor-5）：pre-fix 红跑真实进入 Leiden（60s 配置上界为兜底非预期
 * 时长，链式合成图实际秒级完成）——时长登记 _tmp/red-green-2530-summary.md。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestCommunityDetectionGuard_W_04 extends JunitAutoTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    ITransactionTemplate transactionTemplate;

    @Inject
    IOrmTemplate ormTemplate;

    static final int OVER_MAX_NODES = 10001;

    /**
     * 计数 CallGraph：getCallees 是 CodeCallGraph.getOutEdges 的唯一委托通道，即
     * LeidenDetector.detect 图转换循环（:62-74）的唯一图访问入口——守卫生效则零调用。
     */
    static class CountingCallGraph extends CallGraph {
        final AtomicInteger callees = new AtomicInteger();

        @Override
        public synchronized List<String> getCallees(String nodeId) {
            callees.incrementAndGet();
            return super.getCallees(nodeId);
        }
    }

    /** 包内 CodeCacheManager 子类：按 indexId 注入纯内存夹具（零真实索引成本）。 */
    static class FixtureCacheManager extends CodeCacheManager {
        final Map<String, CallGraph> graphs = new HashMap<>();
        final Map<String, SymbolTable> tables = new HashMap<>();

        @Override
        CallGraph getOrRebuildCallGraph(String indexId, IDaoProvider daoProvider,
                                        BiConsumer<CallGraph, NopCodeCall> edgeConsumer) {
            CallGraph fixture = graphs.get(indexId);
            return fixture != null ? fixture : super.getOrRebuildCallGraph(indexId, daoProvider, edgeConsumer);
        }

        @Override
        SymbolTable getOrRebuildSymbolTable(String indexId, IDaoProvider daoProvider,
                                            java.util.function.Function<io.nop.code.dao.entity.NopCodeSymbol, CodeSymbol> converter) {
            SymbolTable fixture = tables.get(indexId);
            return fixture != null ? fixture : super.getOrRebuildSymbolTable(indexId, daoProvider, converter);
        }
    }

    /**
     * materializer 计数代理：覆写既有包内可见 communityRows（单飞门之下的下游方法，
     * Major-2 不变式）捕获社区结果后抛探针短路——replaceRows 前截断，零 DB 写。
     */
    static class CapturingMaterializer extends GraphMetricMaterializer {
        CommunityResult captured;
        final RuntimeException probeStop = new RuntimeException("w04-probe-stop");

        CapturingMaterializer(IDaoProvider daoProvider, CodeCacheManager cacheManager,
                              ITransactionTemplate transactionTemplate, IOrmTemplate ormTemplate) {
            super(daoProvider, cacheManager, transactionTemplate, ormTemplate);
        }

        @Override
        List<NopCodeGraphMetric> communityRows(String indexId, CommunityResult result, long computedAt) {
            captured = result;
            throw probeStop;
        }
    }

    static CallGraph buildChain(CallGraph g, int n) {
        for (int i = 0; i + 1 < n; i++) {
            g.addEdge("n" + i, "n" + (i + 1));
        }
        return g;
    }

    static SymbolTable symbolTable(int count) {
        SymbolTable table = new SymbolTable();
        for (int i = 0; i < count; i++) {
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId("sym" + i);
            symbol.setName("S" + i);
            symbol.setQualifiedName("demo.S" + i);
            table.add(symbol);
        }
        return table;
    }

    static SymbolTable symbolTableFor(CallGraph graph) {
        SymbolTable table = new SymbolTable();
        int i = 0;
        for (String nodeId : graph.getAllNodeIds()) {
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(nodeId);
            symbol.setName(nodeId);
            symbol.setQualifiedName("demo." + nodeId);
            table.add(symbol);
            i++;
            if (i >= 20000) {
                break;
            }
        }
        return table;
    }

    /**
     * (1) sink 面：detectCommunities 兜底路径（metricMaterializer=null 直达
     * runCommunityDetection）——超 10⁴ 节点图守卫先于 LeidenDetector 触碰图。
     */
    @Test
    void testW_04_sinkGuardSkipsLeidenOnOversizeGraph() {
        CountingCallGraph graph = new CountingCallGraph();
        buildChain(graph, OVER_MAX_NODES);
        FixtureCacheManager cacheManager = new FixtureCacheManager();
        cacheManager.graphs.put("w04_sink", graph);
        cacheManager.tables.put("w04_sink", symbolTable(1));
        CodeGraphService service = new CodeGraphService(daoProvider, cacheManager, null);

        CommunityDetectionResultDTO dto = service.detectCommunities("w04_sink");

        assertEquals(0, graph.callees.get(),
                "the size guard must precede LeidenDetector's graph touch (getCallees is "
                        + "detect's only graph access channel) - pre-fix the conversion loop "
                        + "drives >= 10001 calls (red)");
        assertNotNull(dto);
        assertEquals(0, dto.getTotalCommunities(),
                "skip-and-degrade form = empty community result (same shape as the <2 short-circuit)");
    }

    /**
     * (2) materializer 面：detectCommunities 守卫超限返回 null——COMMUNITY 缺行走既有
     * null 链（communityRows(null) 空行集），社区族降级不放大为整体物化失败。
     */
    @Test
    void testW_04_materializerGuardYieldsNullCommunityResult() {
        FixtureCacheManager cacheManager = new FixtureCacheManager();
        cacheManager.graphs.put("w04_mat", buildChain(new CallGraph(), OVER_MAX_NODES));
        cacheManager.tables.put("w04_mat", symbolTable(1));
        CapturingMaterializer materializer = new CapturingMaterializer(
                daoProvider, cacheManager, transactionTemplate, ormTemplate);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> materializer.materialize("w04_mat"));
        assertSame(materializer.probeStop, e, "the probe short-circuit must surface unchanged");

        assertNull(materializer.captured,
                "the guard must yield a null community result downstream of COMMUNITY rows "
                        + "(pre-fix detect returns a non-null result after burning real Leiden - red)");
    }

    /**
     * (3) diffGraph 双侧：target 侧对称早退（pre-fix 裸奔——构造性当前即红）+
     * baseline 侧既有早退守卫零回退锚。
     */
    @Test
    void testW_04_diffGraphTargetSideSymmetricGuard() {
        FixtureCacheManager cacheManager = new FixtureCacheManager();
        cacheManager.graphs.put("w04_diff_small", buildChain(new CallGraph(), 3));
        cacheManager.tables.put("w04_diff_small", symbolTable(3));
        cacheManager.graphs.put("w04_diff_big", buildChain(new CallGraph(), OVER_MAX_NODES));
        cacheManager.tables.put("w04_diff_big", symbolTableFor(cacheManager.graphs.get("w04_diff_big")));
        CodeGraphService service = new CodeGraphService(daoProvider, cacheManager, null);

        GraphDiffDTO targetOver = service.diffGraph("w04_diff_small", "w04_diff_big");
        assertTrue(isEmptyAddedNodes(targetOver),
                "oversize target must yield the empty diff (symmetric twin early-exit; "
                        + "pre-fix the target side detects communities without a guard and the "
                        + "diff carries >= 10001 addedNodes - red)");

        GraphDiffDTO baselineOver = service.diffGraph("w04_diff_big", "w04_diff_small");
        assertTrue(isEmptyAddedNodes(baselineOver),
                "existing baseline early-exit guard must be preserved unchanged (zero-regression anchor)");
    }

    /**
     * The empty-diff contract follows the existing baseline early-exit shape
     * ({@code new GraphDiffDTO()} leaves the sets null until convertGraphDiff populates
     * them) - null and empty both mean "no added nodes reported".
     */
    private static boolean isEmptyAddedNodes(GraphDiffDTO dto) {
        return dto.getAddedNodes() == null || dto.getAddedNodes().isEmpty();
    }
}
