package io.nop.metadata.service.lineage;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMeasureLineage extends LineageTestBase {

    @Test
    public void testExtractMeasureLineageSuccessFlatCollect() {
        String moduleId = ensureModule("mod-measure-success");
        String entityId = saveEntity(moduleId, "MeasureSuccessEnt", "A", "B", "C", "D");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_OK", entityId);

        saveMeasure(tableId, "M1", "A + B", _NopMetadataCoreConstants.AGG_FUNC_SUM);
        saveMeasure(tableId, "M2", "C + D", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");
        assertFalse(resp.hasError(), "extractMeasureLineage should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=4"),
                "flat-collect: 4 edges (A->M1, B->M1, C->M2, D->M2), strict ==4: " + data);

        assertEquals(4L, countMeasureParseEdges(tableId),
                "exactly 4 measure_parse self-loop edges persisted");

        NopMetaLineageEdge aToM1 = findColumnEdge(tableId, tableId, "A", "M1");
        assertNotNull(aToM1, "A->M1 self-loop edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE, aToM1.getLineageSource());
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED, aToM1.getTransformType(),
                "aggFunc non-null -> transformType=aggregated (D4)");
        assertEquals(tableId, aToM1.getSourceTableId(), "self-loop: sourceTableId == targetTableId");
        assertEquals(tableId, aToM1.getTargetTableId());

        NopMetaLineageEdge dToM2 = findColumnEdge(tableId, tableId, "D", "M2");
        assertNotNull(dToM2, "D->M2 self-loop edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED, dToM2.getTransformType());
    }

    @Test
    public void testExtractMeasureLineageRecallByDirectEdgeQuery() {
        String moduleId = ensureModule("mod-measure-recall");
        String entityId = saveEntity(moduleId, "MeasureRecallEnt", "A", "B");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_RECALL", entityId);
        saveMeasure(tableId, "M_RECALL", "A + B", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        execute("mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");

        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceColumn, "A"));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE));
        List<NopMetaLineageEdge> edges = dao.findAllByQuery(q);
        assertEquals(1, edges.size(), "recall: A affects exactly 1 measure: " + edges);
        assertEquals("M_RECALL", edges.get(0).getTargetColumn(),
                "targetColumn == measureName (D2 recall integrity)");

        QueryBean q2 = new QueryBean();
        q2.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, tableId));
        q2.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceColumn, "B"));
        q2.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE));
        List<NopMetaLineageEdge> edges2 = dao.findAllByQuery(q2);
        assertEquals(1, edges2.size(), "recall: B affects exactly 1 measure: " + edges2);
        assertEquals("M_RECALL", edges2.get(0).getTargetColumn(),
                "D2 recall integrity for column B");
    }

    @Test
    public void testExtractMeasureLineagePerMeasureIsolation() {
        String moduleId = ensureModule("mod-measure-iso");
        String entityId = saveEntity(moduleId, "MeasureIsoEnt", "A", "B");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_ISO", entityId);
        saveMeasure(tableId, "M_OK", "A + B", _NopMetadataCoreConstants.AGG_FUNC_SUM);
        saveMeasure(tableId, "M_BAD", "DROP", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");
        assertFalse(resp.hasError(), "per-measure failure must not break the whole action: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=2"),
                "M_OK produces 2 edges (A->M_OK, B->M_OK), M_BAD produces none: " + data);
        assertTrue(data.contains("M_BAD"),
                "errors must contain M_BAD measureName (per-measure isolation label): " + data);

        assertEquals(2L, countMeasureParseEdges(tableId),
                "M_OK edges persisted (per-measure isolation): 2 edges");
        assertNotNull(findColumnEdge(tableId, tableId, "A", "M_OK"),
                "A->M_OK edge must persist despite M_BAD failure");
    }

    @Test
    public void testExtractMeasureLineageBfsNotPolluted() {
        String moduleId = ensureModule("mod-measure-bfs");
        String entityId = saveEntity(moduleId, "MeasureBfsEnt", "A", "B");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_BFS", entityId);
        saveMeasure(tableId, "M_BFS", "A + B", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        execute("mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");

        assertEquals(2L, countMeasureParseEdges(tableId), "self-loop edges persisted");

        List<String> downstream = lineageBiz.getDownstream(tableId, svcCtx);
        assertFalse(downstream.contains(tableId),
                "BFS semantic isolation: self-loop edge not reachable in getDownstream: " + downstream);

        List<String> impact = lineageBiz.getImpactAnalysis(tableId, null, svcCtx);
        assertFalse(impact.contains(tableId),
                "BFS semantic isolation: self-loop edge not reachable in getImpactAnalysis: " + impact);
        List<String> impactByCol = lineageBiz.getImpactAnalysis(tableId, "A", svcCtx);
        assertFalse(impactByCol.contains(tableId),
                "BFS semantic isolation by column: self-loop edge not reachable: " + impactByCol);
    }

    @Test
    public void testExtractMeasureLineageDictValueEffective() {
        String moduleId = ensureModule("mod-measure-dict");
        String entityId = saveEntity(moduleId, "MeasureDictEnt", "X", "Y");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_DICT", entityId);
        saveMeasure(tableId, "M_DICT", "X + Y", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        execute("mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");

        NopMetaLineageEdge e = findColumnEdge(tableId, tableId, "X", "M_DICT");
        assertNotNull(e, "X->M_DICT edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE, e.getLineageSource(),
                "lineageSource == measure_parse (Phase 1 常量生成 + action 正确使用)");
        assertEquals("measure_parse", e.getLineageSource(),
                "lineageSource string value must be 'measure_parse'");
    }

    @Test
    public void testExtractMeasureLineageReplaceIdempotent() {
        String moduleId = ensureModule("mod-measure-replace");
        String entityId = saveEntity(moduleId, "MeasureReplaceEnt", "A", "B", "C", "D");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_REP", entityId);
        saveMeasure(tableId, "M_REP1", "A + B", _NopMetadataCoreConstants.AGG_FUNC_SUM);
        saveMeasure(tableId, "M_REP2", "C + D", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        execute("mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");
        assertEquals(4L, countMeasureParseEdges(tableId), "initial total: 4 edges");
        assertNotNull(findColumnEdge(tableId, tableId, "A", "M_REP1"),
                "A->M_REP1 edge exists initially");

        updateMeasureExpression(tableId, "M_REP1", "B + C");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");
        assertFalse(resp.hasError(), "re-extract should not error: " + resp);

        assertNull(findColumnEdge(tableId, tableId, "A", "M_REP1"),
                "D6 replace: stale A->M_REP1 edge must be deleted");
        assertNotNull(findColumnEdge(tableId, tableId, "B", "M_REP1"),
                "new B->M_REP1 edge inserted after replace");
        assertNotNull(findColumnEdge(tableId, tableId, "C", "M_REP1"),
                "new C->M_REP1 edge inserted after replace");
        assertEquals(4L, countMeasureParseEdges(tableId),
                "D6 replace: total edges == sum of all measure deps after re-extract: 4");
        execute("mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");
        assertEquals(4L, countMeasureParseEdges(tableId),
                "D6 replace idempotent: second re-extract keeps total at 4, no duplicates");
    }

    @Test
    public void testExtractMeasureLineageAggFuncNullDerived() {
        String moduleId = ensureModule("mod-measure-derived");
        String entityId = saveEntity(moduleId, "MeasureDerivedEnt", "P", "Q");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_DERIVED", entityId);
        saveMeasure(tableId, "M_DERIVED", "P + Q", null);

        execute("mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");

        NopMetaLineageEdge e = findColumnEdge(tableId, tableId, "P", "M_DERIVED");
        assertNotNull(e, "P->M_DERIVED edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED, e.getTransformType(),
                "D4 boundary: aggFunc null -> transformType=derived");
    }

    @Test
    public void testExtractMeasureLineageResolverTableLevelFailure() {
        String moduleId = ensureModule("mod-measure-resolver-fail");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_NO_ENTITY", null);
        saveMeasure(tableId, "M_RESOLVER_FAIL", "A + B", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");
        assertTrue(resp.hasError(),
                "table-level pre-condition failure (baseEntityId null) must fast-fail "
                        + "(not silent empty, not per-measure isolation): " + resp);
        String errorCode = resp.getErrorCode();
        assertNotNull(errorCode, "GraphQL response must carry errorCode extension: " + resp);
        assertTrue(errorCode.contains("field-resolve-base-entity-null"),
                "error must be ERR_FIELD_RESOLVE_BASE_ENTITY_NULL (table-level pre-condition failure), "
                        + "got: " + errorCode);
        assertEquals(0L, countMeasureParseEdges(tableId),
                "table-level pre-condition failure must not produce any edges");
    }
}
