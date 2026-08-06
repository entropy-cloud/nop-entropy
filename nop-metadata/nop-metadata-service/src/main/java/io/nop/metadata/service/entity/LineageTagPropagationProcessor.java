
package io.nop.metadata.service.entity;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataException;
import io.nop.biz.api.IBizObjectManager;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.nop.metadata.service.NopMetadataErrors.ARG_ENTITY_ID;
import static io.nop.metadata.service.NopMetadataErrors.ARG_ENTITY_TYPE;
import static io.nop.metadata.service.NopMetadataErrors.ARG_TAG_ID;
import static io.nop.metadata.service.NopMetadataErrors.ERR_PROPAGATE_UNSUPPORTED_ENTITY_TYPE;
import static io.nop.metadata.service.NopMetadataErrors.ERR_TAG_LABEL_SAVE_FAILED;




public class LineageTagPropagationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(LineageTagPropagationProcessor.class);

    private static final int MAX_DEPTH = 3;
    private static final String ENTITY_TYPE_NOP_META_TABLE = "NopMetaTable";
    private static final String SOURCE_LINEAGE_PROPAGATION = "lineage-propagation";
    private static final String LABEL_TYPE_PROPAGATED = "Propagated";
    private static final String STATE_SUGGESTED = "Suggested";

    private IDaoProvider daoProvider;
    private IBizObjectManager bizObjectManager;

    @jakarta.inject.Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @jakarta.inject.Inject
    public void setBizObjectManager(IBizObjectManager bizObjectManager) {
        this.bizObjectManager = bizObjectManager;
    }

    public List<NopMetaTagLabel> propagateTags(String entityType, String entityId, String tagId,
                                                  IServiceContext context) {
        if (!ENTITY_TYPE_NOP_META_TABLE.equals(entityType)) {
            throw new NopMetadataException(ERR_PROPAGATE_UNSUPPORTED_ENTITY_TYPE)
                    .param(ARG_ENTITY_TYPE, entityType);
        }

        if (daoProvider == null) {
            LOG.error("daoProvider is null in LineageTagPropagationProcessor");
            return Collections.emptyList();
        }

        IEntityDao<NopMetaTagLabel> tagLabelDao = daoProvider.daoFor(NopMetaTagLabel.class);
        IEntityDao<NopMetaLineageEdge> edgeDao = daoProvider.daoFor(NopMetaLineageEdge.class);

        List<NopMetaTagLabel> sourceLabels = getSourceLabels(tagLabelDao, entityType, entityId, tagId);
        if (sourceLabels.isEmpty()) {
            return Collections.emptyList();
        }

        List<NopMetaLineageEdge> edges = findDirectEdges(edgeDao, entityId);
        if (edges.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> visited = new HashSet<>();
        visited.add(entityType + "#" + entityId);

        List<NopMetaTagLabel> allCreated = new ArrayList<>();
        for (NopMetaLineageEdge edge : edges) {
            propagateEdge(tagLabelDao, edge, sourceLabels, 0, visited, allCreated, context);
        }

        return allCreated;
    }

    private List<NopMetaTagLabel> getSourceLabels(IEntityDao<NopMetaTagLabel> dao,
                                                    String entityType, String entityId,
                                                    String tagId) {
        if (tagId != null && !tagId.isEmpty()) {
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, entityType));
            q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, entityId));
            q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_tagId, tagId));
            q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Manual"));
            NopMetaTagLabel label = dao.findFirstByQuery(q);
            return label != null ? Collections.singletonList(label) : Collections.emptyList();
        }

        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, entityType));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, entityId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Manual"));
        return dao.findAllByQuery(q);
    }

    private List<NopMetaLineageEdge> findDirectEdges(IEntityDao<NopMetaLineageEdge> dao, String sourceTableId) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceTableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_transformType, "DIRECT"));
        return dao.findAllByQuery(q);
    }

    private void propagateEdge(IEntityDao<NopMetaTagLabel> tagLabelDao,
                                NopMetaLineageEdge edge,
                                List<NopMetaTagLabel> sourceLabels,
                                int depth,
                                Set<String> visited,
                                List<NopMetaTagLabel> results,
                                IServiceContext context) {
        if (depth >= MAX_DEPTH) {
            LOG.warn("Lineage propagation depth exceeded max ({}) at edgeId={} sourceTableId={} targetTableId={}",
                    MAX_DEPTH, edge.getLineageEdgeId(), edge.getSourceTableId(), edge.getTargetTableId());
            return;
        }

        String targetId = edge.getTargetTableId();
        String visitKey = ENTITY_TYPE_NOP_META_TABLE + "#" + targetId;
        if (!visited.add(visitKey)) {
            return;
        }

        for (NopMetaTagLabel sourceLabel : sourceLabels) {
            try {
                NopMetaTagLabel created = doCreatePropagatedLabel(tagLabelDao, targetId,
                        sourceLabel.getTagId(), context);
                if (created != null) {
                    results.add(created);
                }

                IEntityDao<NopMetaLineageEdge> edgeDao = daoProvider.daoFor(NopMetaLineageEdge.class);
                List<NopMetaLineageEdge> nextEdges = findDirectEdges(edgeDao, targetId);
                for (NopMetaLineageEdge nextEdge : nextEdges) {
                    if (visited.contains(ENTITY_TYPE_NOP_META_TABLE + "#" + nextEdge.getTargetTableId())) {
                        continue;
                    }
                    propagateEdge(tagLabelDao, nextEdge,
                            Collections.singletonList(sourceLabel),
                            depth + 1, visited, results, context);
                }
            } catch (Exception e) {
                // AR-21（plan 2026-08-06-1228-1 Phase 3）：后台传播路径保留 per-edge 隔离（单边失败不中断
                // 整条血缘传播），但内层不再静默返回 null（内层抛错）——此处 LOG.error 含完整上下文留证，
                // 失败可观测（显式裁定语义：传播失败可观测但不中断批处理）。
                LOG.error("propagation failed for edge edgeId={} sourceTableId={} targetTableId={} tagId={}",
                        edge.getLineageEdgeId(), edge.getSourceTableId(), edge.getTargetTableId(),
                        sourceLabel.getTagId(), e);
            }
        }
    }

    private NopMetaTagLabel doCreatePropagatedLabel(IEntityDao<NopMetaTagLabel> tagLabelDao,
                                                      String targetEntityId,
                                                      String tagId,
                                                      IServiceContext context) {
        if (hasExistingPropagatedLabel(tagLabelDao, ENTITY_TYPE_NOP_META_TABLE, targetEntityId, tagId)) {
            return null;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("tagLabelId", UUID.randomUUID().toString().replace("-", ""));
        data.put("source", SOURCE_LINEAGE_PROPAGATION);
        data.put("tagId", tagId);
        data.put("labelType", LABEL_TYPE_PROPAGATED);
        data.put("state", STATE_SUGGESTED);
        data.put("entityType", ENTITY_TYPE_NOP_META_TABLE);
        data.put("entityId", targetEntityId);

        try {
            Object result = bizObjectManager.getBizObject("NopMetaTagLabel")
                    .invoke("save", Map.of("data", data), null, context);
            if (result instanceof NopMetaTagLabel) {
                return (NopMetaTagLabel) result;
            }
            // AR-21：invoke 返回非实体（极低概率边缘）不静默无日志——显式留证后按空结果处理（残余登记 plan）。
            LOG.warn("Propagated TagLabel save invoke returned non-entity result for entityId={} tagId={} resultType={}",
                    targetEntityId, tagId, result == null ? "null" : result.getClass().getName());
            return null;
        } catch (NopException e) {
            // AR-21：已带错误码的异常原样上抛（外层 propagateEdge 按后台路径隔离语义 LOG.error 留证并继续），
            // 不再被 catch-all 吞掉静默返回 null。
            throw e;
        } catch (Exception e) {
            LOG.warn("Propagated TagLabel save failed for entityId={} tagId={}, fail-loud (no silent drop)",
                    targetEntityId, tagId, e);
            throw new NopMetadataException(ERR_TAG_LABEL_SAVE_FAILED, e)
                    .param(ARG_ENTITY_TYPE, ENTITY_TYPE_NOP_META_TABLE)
                    .param(ARG_ENTITY_ID, targetEntityId)
                    .param(ARG_TAG_ID, tagId);
        }
    }

    private boolean hasExistingPropagatedLabel(IEntityDao<NopMetaTagLabel> dao,
                                                 String entityType, String entityId, String tagId) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, entityType));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, entityId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_tagId, tagId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_source, SOURCE_LINEAGE_PROPAGATION));
        return dao.findFirstByQuery(q) != null;
    }
}
