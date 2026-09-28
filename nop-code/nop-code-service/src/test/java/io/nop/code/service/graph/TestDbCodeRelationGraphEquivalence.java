package io.nop.code.service.graph;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.graph.CodeRelationGraph;
import io.nop.code.dao.entity.NopCodeAnnotationUsage;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeInheritance;
import io.nop.code.dao.entity.NopCodeSemanticEdge;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graph.api.Edge;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * N6.2 equivalence proof: the DB-backed point-query graph must produce exactly the same
 * per-node edges as the in-memory backend (full loader + CodeRelationGraph) over the same
 * indexed data. All four edge families are seeded directly so the comparison covers every
 * family deterministically. Also pins the bounded-traversal contract (visited cycle safety,
 * depth bound, explicit failure on depth <= 0).
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestDbCodeRelationGraphEquivalence extends JunitAutoTestCase {

    @Inject
    IDaoProvider daoProvider;

    private static final String INDEX_ID = "n62_db_graph_eq";

    // call family
    private static final String CALLER = "sym.A.handle";
    private static final String CALLEE = "sym.B.run";

    // inheritance family
    private static final String SUB = "sym.Impl";
    private static final String SUPER = "sym.Base";

    // annotation family
    private static final String ANNOTATED = "sym.Bean";
    private static final String ANNOTATION_TYPE = "sym.Deprecated";

    // semantic family (directed + weight + confidence 20)
    private static final String SEM_SOURCE = "sym.A.handle";
    private static final String SEM_TARGET = "sym.Impl";

    private void seedFamilies() {
        IEntityDao<NopCodeCall> callDao = daoProvider.daoFor(NopCodeCall.class);
        NopCodeCall call = callDao.newEntity();
        call.setIndexId(INDEX_ID);
        call.setId("call-1");
        call.setCallerId(CALLER);
        call.setCalleeId(CALLEE);
        call.setProvenance("SYMBOL_SOLVER");
        call.setFileId("file-seed-1");
        call.setLine(1);
        call.setColumn(0);
        callDao.saveEntity(call);

        // exact-duplicate pair: dedup must collapse it identically in both backends
        // (same attrs, so first-occurrence selection is order-independent)
        NopCodeCall duplicate = callDao.newEntity();
        duplicate.setIndexId(INDEX_ID);
        duplicate.setId("call-2");
        duplicate.setCallerId(CALLER);
        duplicate.setCalleeId(CALLEE);
        duplicate.setProvenance("SYMBOL_SOLVER");
        duplicate.setFileId("file-seed-1");
        duplicate.setLine(2);
        duplicate.setColumn(0);
        callDao.saveEntity(duplicate);

        IEntityDao<NopCodeInheritance> inhDao = daoProvider.daoFor(NopCodeInheritance.class);
        NopCodeInheritance inh = inhDao.newEntity();
        inh.setIndexId(INDEX_ID);
        inh.setId("inh-1");
        inh.setSubTypeId(SUB);
        inh.setSuperTypeId(SUPER);
        inh.setRelationType("IMPLEMENTS");
        inh.setProvenance("AST_EXTRACTION");
        inhDao.saveEntity(inh);

        IEntityDao<NopCodeAnnotationUsage> annDao = daoProvider.daoFor(NopCodeAnnotationUsage.class);
        NopCodeAnnotationUsage ann = annDao.newEntity();
        ann.setIndexId(INDEX_ID);
        ann.setId("ann-1");
        ann.setAnnotatedSymbolId(ANNOTATED);
        ann.setAnnotationTypeId(ANNOTATION_TYPE);
        ann.setProvenance("AST_EXTRACTION");
        annDao.saveEntity(ann);

        IEntityDao<NopCodeSemanticEdge> semDao = daoProvider.daoFor(NopCodeSemanticEdge.class);
        NopCodeSemanticEdge sem = semDao.newEntity();
        sem.setIndexId(INDEX_ID);
        sem.setId("sem-1");
        sem.setSourceSymbolId(SEM_SOURCE);
        sem.setTargetSymbolId(SEM_TARGET);
        sem.setRelationType("SEMANTICALLY_SIMILAR_TO");
        sem.setConfidence(20);
        sem.setConfidenceScore(0.8);
        sem.setDirected(Boolean.TRUE);
        sem.setProvenance("AST_EXTRACTION");
        semDao.saveEntity(sem);

        // undirected semantic row: still placed by source/target on both sides (directed is
        // only an attr), covering the directed=false branch of the equivalence
        NopCodeSemanticEdge undirected = semDao.newEntity();
        undirected.setIndexId(INDEX_ID);
        undirected.setId("sem-2");
        undirected.setSourceSymbolId(SUB);
        undirected.setTargetSymbolId(ANNOTATION_TYPE);
        undirected.setRelationType("SEMANTICALLY_SIMILAR_TO");
        undirected.setConfidence(10);
        undirected.setDirected(Boolean.FALSE);
        undirected.setProvenance("AST_EXTRACTION");
        semDao.saveEntity(undirected);
    }

    private static Set<String> edgeTriples(List<Edge> edges) {
        Set<String> out = new TreeSet<>();
        for (Edge edge : edges) {
            Object relation = edge.getAttrs() != null ? edge.getAttrs().get("relationType") : null;
            out.add(edge.getSourceId() + "->" + edge.getTargetId() + "|" + edge.getType()
                    + "|" + relation);
        }
        return out;
    }

    private static Set<String> attrKeys(List<Edge> edges) {
        Set<String> out = new TreeSet<>();
        for (Edge edge : edges) {
            if (edge.getAttrs() != null) {
                out.addAll(edge.getAttrs().keySet());
            }
        }
        return out;
    }

    @Test
    void testDbGraphEquivalentToInMemoryBackendOverAllNodes() {
        seedFamilies();

        CodeRelationGraph inMemory = CodeRelationGraphLoader.load(INDEX_ID, daoProvider, null);
        DbCodeRelationGraph dbGraph = new DbCodeRelationGraph(INDEX_ID, daoProvider, null);

        List<String> allNodes = new ArrayList<>(inMemory.nodeIds());
        assertTrue(allNodes.contains(CALLER), "fixture must produce the caller node");
        assertTrue(allNodes.size() >= 5, "fixture must cover all four families' endpoints");

        for (String nodeId : allNodes) {
            assertEquals(edgeTriples(inMemory.getInEdges(nodeId)), edgeTriples(dbGraph.getInEdges(nodeId)),
                    "in-edges must be identical at " + nodeId);
            assertEquals(edgeTriples(inMemory.getOutEdges(nodeId)), edgeTriples(dbGraph.getOutEdges(nodeId)),
                    "out-edges must be identical at " + nodeId);
            assertEquals(attrKeys(inMemory.getOutEdges(nodeId)), attrKeys(dbGraph.getOutEdges(nodeId)),
                    "attr keys must be identical at " + nodeId);
        }

        // non-node id: both backends return empty
        assertTrue(dbGraph.getOutEdges("nonexistent").isEmpty());
        assertTrue(dbGraph.getInEdges("nonexistent").isEmpty());
        assertEquals(0, inMemory.getOutEdges("nonexistent").size());

        // duplicate call rows (call-2, same pair different provenance) must survive in BOTH
        // backends identically: same pair, different confidence attr (provenance-derived)
        assertEquals(new HashSet<>(inMemory.getOutEdges(CALLER)).size(),
                new HashSet<>(dbGraph.getOutEdges(CALLER)).size());
    }

    @Test
    void testPointQueriesAreIndexScoped() {
        seedFamilies();
        // a second index sharing the same node ids must not bleed its edges into this graph
        IEntityDao<NopCodeCall> callDao = daoProvider.daoFor(NopCodeCall.class);
        NopCodeCall foreign = callDao.newEntity();
        foreign.setIndexId("other-index");
        foreign.setId("call-foreign");
        foreign.setCallerId(CALLER);
        foreign.setCalleeId(CALLEE);
        foreign.setFileId("foreign-file");
        foreign.setLine(1);
        foreign.setColumn(0);
        callDao.saveEntity(foreign);

        DbCodeRelationGraph dbGraph = new DbCodeRelationGraph(INDEX_ID, daoProvider, null);
        CodeRelationGraph inMemory = CodeRelationGraphLoader.load(INDEX_ID, daoProvider, null);
        assertEquals(edgeTriples(inMemory.getOutEdges(CALLER)), edgeTriples(dbGraph.getOutEdges(CALLER)),
                "foreign-index edges must not leak into this graph");
        assertEquals(inMemory.getOutEdges(CALLER).size(), dbGraph.getOutEdges(CALLER).size(),
                "only this index's edges are visible (call + semantic), foreign excluded");
    }

    @Test
    void testBoundedTraversalCycleSafeAndDepthBound() {
        seedFamilies();
        DbCodeRelationGraph dbGraph = new DbCodeRelationGraph(INDEX_ID, daoProvider, null);

        // cycle: add a back edge B.handle -> A.handle via call family so traversal must not loop
        IEntityDao<NopCodeCall> callDao = daoProvider.daoFor(NopCodeCall.class);
        NopCodeCall back = callDao.newEntity();
        back.setIndexId(INDEX_ID);
        back.setId("call-back");
        back.setCallerId(CALLEE);
        back.setCalleeId(CALLER);
        back.setFileId("file-seed-1");
        back.setLine(3);
        back.setColumn(0);
        callDao.saveEntity(back);

        Set<String> visited = dbGraph.traverseBounded(List.of(CALLER), 1, DbCodeRelationGraph.Direction.OUT);
        // A.handle's out-edges: call to B.run + semantic edge to Impl (families are traversed
        // uniformly per the IGraph contract)
        assertEquals(new HashSet<>(List.of(CALLER, CALLEE, SEM_TARGET)), visited,
                "depth 1 out-traversal covers all direct out-endpoints across families");

        // depth 5: the A.handle→Impl call-edge cycle is visited once; multi-family successors
        // discovered deeper (Impl→Base via inheritance, Impl→Deprecated via semantic)
        visited = dbGraph.traverseBounded(List.of(CALLER), 5, DbCodeRelationGraph.Direction.OUT);
        assertEquals(new HashSet<>(List.of(CALLER, CALLEE, SEM_TARGET, SUPER, ANNOTATION_TYPE)), visited,
                "cycle must be visited-once, multi-family successors must be discovered");

        Set<String> inVisited = dbGraph.traverseBounded(List.of(SUPER), 3, DbCodeRelationGraph.Direction.IN);
        assertTrue(inVisited.contains(SUB), "in-traversal must reach the subtype");

        assertThrows(IllegalArgumentException.class,
                () -> dbGraph.traverseBounded(List.of(CALLER), 0, DbCodeRelationGraph.Direction.OUT),
                "depth <= 0 must fail explicitly");
    }
}
