package io.nop.code.service.graph;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.graph.CodeRelationGraph;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2532 组A 红①（6.3-W-01 / C5-001，TP-001 主面回归锚点）：loader 四表累计边数上界。
 *
 * <p>TP-001 原文「上界常量可注入以便测试不必造十万行数据」——本驱动器注入低上界（10）并播种
 * 25 行 call 边（DAO 播种形态，TestGraphMetricMaterialization / TestCascadeDeleteOrmLevel
 * autotest 载体先例），断言：返回边数 ≤ 注入上界、被动截断标志置位。
 *
 * <p>红证形态（设计裁定④）：pre-fix 编译期构造红——注入 setter（{@code setMaxTotalEdges}）、
 * 实例加载路径（{@code loadGraph}）与截断标志访问器（{@code CodeRelationGraph.isTruncated}）
 * 均不存在；post-fix 语义红 = 边数超上界或标志未置位。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestRelationGraphLoaderBound_W_01 extends JunitAutoTestCase {

    @Inject
    IDaoProvider daoProvider;

    private String unique(String prefix) {
        // hex (no sign char, <= 16 chars) keeps id/indexId within the 36-char codeId domain
        return prefix + Long.toHexString(System.nanoTime());
    }

    private void seedCalls(String indexId, int count) {
        IEntityDao<NopCodeCall> dao = daoProvider.daoFor(NopCodeCall.class);
        for (int i = 0; i < count; i++) {
            NopCodeCall call = dao.newEntity();
            call.setId(indexId + "-c" + i);
            call.setIndexId(indexId);
            call.setCallerId("caller-" + (i % 5));
            call.setCalleeId("callee-" + (i % 7));
            call.setFileId(indexId + "-f");
            call.setLine(i + 1);
            call.setProvenance("AST_EXTRACTION");
            dao.saveEntity(call);
        }
        dao.flushSession();
    }

    private int countEdges(CodeRelationGraph graph) {
        int total = 0;
        for (String nodeId : graph.nodeIds()) {
            total += graph.getOutEdges(nodeId).size();
        }
        return total;
    }

    @Test
    void testW_01_loaderCumulativeEdgeCapTruncatesAndFlags() {
        String indexId = unique("lb");
        seedCalls(indexId, 25);

        CodeRelationGraphLoader loader = new CodeRelationGraphLoader();
        loader.setMaxTotalEdges(10);
        CodeRelationGraph graph = loader.loadGraph(indexId, daoProvider, null);

        int edges = countEdges(graph);
        assertEquals(10, edges, "cumulative edge count must respect the injected cap");
        assertTrue(graph.isTruncated(), "truncation flag must be set once the cap is reached");
    }

    @Test
    void testW_01_belowCapLoadIsCompleteAndUnflagged() {
        String indexId = unique("lbb");
        seedCalls(indexId, 5);

        CodeRelationGraphLoader loader = new CodeRelationGraphLoader();
        loader.setMaxTotalEdges(10);
        CodeRelationGraph graph = loader.loadGraph(indexId, daoProvider, null);

        assertFalse(graph.isTruncated(), "no truncation below the cap");
        assertEquals(5, countEdges(graph), "all edges must load when under the cap");
    }
}
