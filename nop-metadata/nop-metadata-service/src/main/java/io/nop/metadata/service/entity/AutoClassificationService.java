
package io.nop.metadata.service.entity;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.biz.api.IBizObjectManager;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static io.nop.metadata.service.NopMetadataErrors.ARG_ENTITY_TYPE;
import static io.nop.metadata.service.NopMetadataErrors.ARG_TABLE_TYPE;
import static io.nop.metadata.service.NopMetadataErrors.ERR_AUTOCLASSIFY_UNSUPPORTED_ENTITY_TYPE;
import static io.nop.metadata.service.NopMetadataErrors.ERR_AUTOCLASSIFY_UNSUPPORTED_TABLE_TYPE;

public class AutoClassificationService {

    private static final Logger LOG = LoggerFactory.getLogger(AutoClassificationService.class);

    private static final String ENTITY_TYPE_NOP_META_TABLE = "NopMetaTable";
    private static final String SOURCE_AUTO_CLASSIFY = "auto-classify";
    private static final String LABEL_TYPE_AUTOMATED = "Automated";
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

    @SuppressWarnings("unchecked")
    public List<NopMetaTagLabel> suggestTags(String entityType, String entityId,
                                              IServiceContext context) {
        if (!ENTITY_TYPE_NOP_META_TABLE.equals(entityType)) {
            throw new NopException(ERR_AUTOCLASSIFY_UNSUPPORTED_ENTITY_TYPE)
                    .param(ARG_ENTITY_TYPE, entityType);
        }

        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable table = tableDao.getEntityById(entityId);
        if (table == null) {
            return Collections.emptyList();
        }

        if (!_NopMetadataCoreConstants.TABLE_TYPE_ENTITY.equals(table.getTableType())) {
            throw new NopException(ERR_AUTOCLASSIFY_UNSUPPORTED_TABLE_TYPE)
                    .param(ARG_TABLE_TYPE, table.getTableType());
        }

        String baseEntityId = table.getBaseEntityId();
        if (baseEntityId == null || baseEntityId.isEmpty()) {
            LOG.info("No entity mapping for table entityId={}", entityId);
            return Collections.emptyList();
        }

        NopMetaClassification classification = discoverClassification(entityId);
        if (classification == null) {
            return Collections.emptyList();
        }

        String configJson = classification.getAutoClassificationConfig();
        if (configJson == null || configJson.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> rules;
        try {
            Object parsed = JsonTool.parse(configJson);
            if (!(parsed instanceof List)) {
                return Collections.emptyList();
            }
            rules = (List<Map<String, Object>>) parsed;
        } catch (Exception e) {
            LOG.warn("Failed to parse autoClassificationConfig for classificationId={}",
                    classification.getClassificationId(), e);
            return Collections.emptyList();
        }

        if (rules.isEmpty()) {
            return Collections.emptyList();
        }

        List<NopMetaEntityField> fields = getEntityFields(baseEntityId);
        if (fields.isEmpty()) {
            return Collections.emptyList();
        }

        IEntityDao<NopMetaTag> tagDao = daoProvider.daoFor(NopMetaTag.class);
        IEntityDao<NopMetaTagLabel> tagLabelDao = daoProvider.daoFor(NopMetaTagLabel.class);

        List<MatchResult> matches = new ArrayList<>();
        for (NopMetaEntityField field : fields) {
            String fieldName = field.getFieldName();
            String stdDataType = field.getStdDataType();
            if (fieldName == null) continue;

            for (int i = 0; i < rules.size(); i++) {
                Map<String, Object> rule = rules.get(i);
                String pattern = (String) rule.get("pattern");
                String tagFQN = (String) rule.get("tagFQN");
                if (pattern == null || tagFQN == null) continue;

                Pattern compiled;
                try {
                    compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                } catch (Exception e) {
                    continue;
                }

                if (!compiled.matcher(fieldName).find()) continue;

                String fieldTypeFilter = (String) rule.get("fieldTypeFilter");
                if (fieldTypeFilter != null && !fieldTypeFilter.isEmpty()) {
                    if (stdDataType == null || !fieldTypeFilter.equalsIgnoreCase(stdDataType)) {
                        continue;
                    }
                }

                Number priorityNum = rule.get("priority") instanceof Number
                        ? (Number) rule.get("priority") : null;
                int priority = priorityNum != null ? priorityNum.intValue() : 0;

                NopMetaTag tag = findTagByFQN(tagDao, tagFQN, classification.getClassificationId());
                if (tag == null) continue;

                matches.add(new MatchResult(field, tag.getTagId(), priority, tagFQN, i));
            }
        }

        matches.sort(Comparator.<MatchResult, Integer>comparing(m -> m.ruleIndex)
                .thenComparing(m -> -m.priority));

        Map<String, MatchResult> bestByTagId = new HashMap<>();
        for (MatchResult m : matches) {
            bestByTagId.merge(m.tagId, m, (a, b) -> a.priority >= b.priority ? a : b);
        }

        List<NopMetaTagLabel> createdLabels = new ArrayList<>();
        for (MatchResult m : bestByTagId.values()) {
            try {
                NopMetaTagLabel created = doCreateAutomatedLabel(tagLabelDao, entityId, m.tagId, context);
                if (created != null) {
                    createdLabels.add(created);
                }
            } catch (Exception e) {
                LOG.warn("Failed to create automated TagLabel for entityId={} tagId={}", entityId, m.tagId, e);
            }
        }

        return createdLabels;
    }

    private NopMetaClassification discoverClassification(String metaTableId) {
        IEntityDao<NopMetaTagLabel> tagLabelDao = daoProvider.daoFor(NopMetaTagLabel.class);
        IEntityDao<NopMetaTag> tagDao = daoProvider.daoFor(NopMetaTag.class);
        IEntityDao<NopMetaClassification> clsDao = daoProvider.daoFor(NopMetaClassification.class);

        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, ENTITY_TYPE_NOP_META_TABLE));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, metaTableId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Manual"));
        List<NopMetaTagLabel> labels = tagLabelDao.findAllByQuery(q);

        NopMetaClassification best = null;
        String bestClsId = null;

        for (NopMetaTagLabel label : labels) {
            NopMetaTag tag = tagDao.getEntityById(label.getTagId());
            if (tag != null && tag.getClassificationId() != null) {
                if (bestClsId == null || tag.getClassificationId().compareTo(bestClsId) < 0) {
                    bestClsId = tag.getClassificationId();
                }
            }
        }

        if (bestClsId != null) {
            best = clsDao.getEntityById(bestClsId);
            if (best != null) {
                return best;
            }
        }

        QueryBean allClsQuery = new QueryBean();
        allClsQuery.addFilter(FilterBeans.eq(NopMetaClassification.PROP_NAME_disabled, (byte) 0));
        List<NopMetaClassification> allEnabled = clsDao.findAllByQuery(allClsQuery);
        if (allEnabled.isEmpty()) {
            return null;
        }
        allEnabled.sort(Comparator.comparing(NopMetaClassification::getClassificationId));
        return allEnabled.get(0);
    }

    private List<NopMetaEntityField> getEntityFields(String baseEntityId) {
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, baseEntityId));
        return fieldDao.findAllByQuery(q);
    }

    private NopMetaTag findTagByFQN(IEntityDao<NopMetaTag> tagDao, String fullyQualifiedName,
                                      String classificationId) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTag.PROP_NAME_fullyQualifiedName, fullyQualifiedName));
        q.addFilter(FilterBeans.eq(NopMetaTag.PROP_NAME_classificationId, classificationId));
        return tagDao.findFirstByQuery(q);
    }

    private NopMetaTagLabel doCreateAutomatedLabel(IEntityDao<NopMetaTagLabel> tagLabelDao,
                                                     String entityId, String tagId,
                                                     IServiceContext context) {
        if (hasExistingAutomatedLabel(tagLabelDao, ENTITY_TYPE_NOP_META_TABLE, entityId, tagId)) {
            return null;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("tagLabelId", UUID.randomUUID().toString().replace("-", ""));
        data.put("source", SOURCE_AUTO_CLASSIFY);
        data.put("tagId", tagId);
        data.put("labelType", LABEL_TYPE_AUTOMATED);
        data.put("state", STATE_SUGGESTED);
        data.put("entityType", ENTITY_TYPE_NOP_META_TABLE);
        data.put("entityId", entityId);

        try {
            Object result = bizObjectManager.getBizObject("NopMetaTagLabel")
                    .invoke("save", Map.of("data", data), null, context);
            if (result instanceof NopMetaTagLabel) {
                return (NopMetaTagLabel) result;
            }
            return null;
        } catch (Exception e) {
            LOG.error("Failed to save automated TagLabel for entityId={} tagId={}", entityId, tagId, e);
            return null;
        }
    }

    private boolean hasExistingAutomatedLabel(IEntityDao<NopMetaTagLabel> dao,
                                                String entityType, String entityId, String tagId) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, entityType));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, entityId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_tagId, tagId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_source, SOURCE_AUTO_CLASSIFY));
        return dao.findFirstByQuery(q) != null;
    }

    private static class MatchResult {
        final NopMetaEntityField field;
        final String tagId;
        final int priority;
        final String tagFQN;
        final int ruleIndex;

        MatchResult(NopMetaEntityField field, String tagId, int priority, String tagFQN, int ruleIndex) {
            this.field = field;
            this.tagId = tagId;
            this.priority = priority;
            this.tagFQN = tagFQN;
            this.ruleIndex = ruleIndex;
        }
    }
}
