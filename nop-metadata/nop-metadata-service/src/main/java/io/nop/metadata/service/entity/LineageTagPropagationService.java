
package io.nop.metadata.service.entity;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
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

import static io.nop.metadata.service.NopMetadataErrors.ARG_ENTITY_TYPE;
import static io.nop.metadata.service.NopMetadataErrors.ERR_PROPAGATE_UNSUPPORTED_ENTITY_TYPE;




public class LineageTagPropagationService {

    private static final Logger LOG = LoggerFactory.getLogger(LineageTagPropagationService.class);

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
            throw new NopException(ERR_PROPAGATE_UNSUPPORTED_ENTITY_TYPE)
                    .param(ARG_ENTITY_TYPE, entityType);
        }

        if (daoProvider == null) {
            LOG.error("daoProvider is null in LineageTagPropagationService");
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
                LOG.error("propagation failed for edge edgeId={}", edge.getLineageEdgeId(), e);
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
            return null;
        } catch (Exception e) {
            LOG.error("Failed to save propagated TagLabel for entityId={} tagId={}", targetEntityId, tagId, e);
            return null;
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
