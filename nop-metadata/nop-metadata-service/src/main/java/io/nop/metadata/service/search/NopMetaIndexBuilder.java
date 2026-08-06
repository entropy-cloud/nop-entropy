package io.nop.metadata.service.search;

import io.nop.dao.api.IDaoProvider;
import io.nop.metadata.api.dto.IndexResult;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.metadata.service.NopMetadataHelper;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchHit;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class NopMetaIndexBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaIndexBuilder.class);

    /**
     * AR-23②（R8.4b）：部分重建类型级清理的枚举上限。SearchRequest 无分页——枚举空 query + tags 过滤
     * 的 search 被 limit 截断；设为显式上限（沿 {@code CrossDbConfigHolder.maxCrossDbRows} 10000 常量先例），
     * 超限部分（真实场景类型文档数远小于上限）为 watch-only residual（裁定记录于 plan + arm-index）。
     */
    private static final int PURGE_ENUMERATION_LIMIT = 10000;

    @Inject
    @Nullable
    @Named("nopSearchEngine")
    protected ISearchEngine searchEngine;

    @Inject
    protected IDaoProvider daoProvider;

    // truncate and join moved to NopMetadataHelper

    public List<IndexResult> buildFullIndex(List<String> entityTypes) {
        if (searchEngine == null) {
            IndexResult result = new IndexResult();
            result.setEntityType("ALL");
            result.setFailed(1);
            result.setErrors(Collections.singletonList("searchEngine not available"));
            return Collections.singletonList(result);
        }

        // AR-23②（R8.4b）清理粒度裁定：全量重建（entityTypes == null，默认全部 6 类型）用 removeTopic 循环前
        // 一次清理整个 topic（不 per-type 循环内多次——避免真实引擎清掉前一个类型刚写入的文档）；显式传类型子集
        // 走类型级枚举清理（效果等价，调用面不同；显式传全部 6 类型也归入类型级路径，与 null 默认路径语义一致）。
        boolean fullRebuild = entityTypes == null;
        if (entityTypes == null) {
            entityTypes = List.of("Classification", "Tag", "GlossaryTerm", "MetaTable", "MetaEntity", "MetaEntityField");
        }

        String topic = NopMetaSearchProcessor.TOPIC;
        List<IndexResult> results = new ArrayList<>();
        boolean topicPurgeFailed = false;

        if (fullRebuild) {
            try {
                searchEngine.removeTopic(topic);
            } catch (Exception e) {
                // 清理失败显式反映（不吞掉）：topic 清理失败时陈旧文档残留 + 新文档叠加——每个类型行都标记失败
                LOG.warn("Failed to purge topic before full index rebuild", e);
                topicPurgeFailed = true;
            }
        }

        for (String entityType : entityTypes) {
            IndexResult result = new IndexResult();
            result.setEntityType(entityType);
            result.setIndexed(0);
            result.setFailed(0);

            List<SearchableDoc> docs = new ArrayList<>();

            try {
                switch (entityType) {
                    case "Classification":
                        docs = buildClassificationDocs(result);
                        break;
                    case "Tag":
                        docs = buildTagDocs(result);
                        break;
                    case "GlossaryTerm":
                        docs = buildGlossaryTermDocs(result);
                        break;
                    case "MetaTable":
                        docs = buildMetaTableDocs(result);
                        break;
                    case "MetaEntity":
                        docs = buildMetaEntityDocs(result);
                        break;
                    case "MetaEntityField":
                        docs = buildMetaEntityFieldDocs(result);
                        break;
                    default:
                        result.setFailed(1);
                        result.setErrors(List.of("Unknown entity type: " + entityType));
                        results.add(result);
                        continue;
                }
            } catch (Exception e) {
                LOG.warn("Failed to process entity type={}", entityType, e);
                result.setFailed(result.getFailed() + 1);
                result.setErrors(List.of("Failed to process: " + entityType));
                results.add(result);
                continue;
            }

            if (fullRebuild) {
                // 全量重建：topic 级清理已在循环前执行；若清理失败，在每个类型行显式标记（failed += 1 + errors）
                if (topicPurgeFailed) {
                    result.setFailed(result.getFailed() + 1);
                    result.setErrors(List.of("Topic purge failed before rebuild"));
                }
            } else {
                // AR-23②：部分重建在 addDocs 前按类型级清理该类型现有 docId（空 query + tags 过滤枚举；
                // getDocsByTerm 是死路——只查 FIELD_CONTENT 分词不能按 tag 枚举，明确排除）
                purgeStaleDocsForType(topic, entityType, result);
            }

            if (!docs.isEmpty()) {
                try {
                    searchEngine.addDocs(topic, docs);
                } catch (Exception e) {
                    LOG.warn("Failed to index docs for type={}", entityType, e);
                    result.setFailed(result.getFailed() + docs.size());
                    result.setErrors(List.of("Batch add failed for type: " + entityType));
                    results.add(result);
                    continue;
                }

                try {
                    searchEngine.refreshBlocking(topic);
                } catch (Exception e) {
                    // AR-23③（R8.2）：refresh 失败不再仅 LOG.warn 静默——写入 IndexResult（failed += 1，
                    // 非 docs.size()：文档已 addDocs 成功），indexed 如实反映已 addDocs 数；搜索不再报"成功"却读陈旧索引
                    LOG.warn("Failed to refresh index for type={}", entityType, e);
                    result.setFailed(result.getFailed() + 1);
                    result.setErrors(List.of("Index refresh failed for type: " + entityType));
                }
            }

            result.setIndexed(docs.size());
            results.add(result);
        }

        return results;
    }

    /**
     * AR-23②（R8.4b）：部分重建的类型级清理——在 addDocs 前枚举该类型现有 docId 并 {@code removeDocs}，
     * 使被删除实体的陈旧文档在重建后不再残留（幽灵搜索结果）。
     *
     * <p>枚举机制（裁定）：{@code getDocsByTerm} 是死路——只查 FIELD_CONTENT 分词不能按 tag 枚举；
     * 唯一可行 = 空 query + tags 过滤的 search。SearchRequest 无分页，枚举被 {@link #PURGE_ENUMERATION_LIMIT}
     * 截断（超限部分 watch-only residual，真实场景类型文档数远小于上限）。
     *
     * <p>无静默跳过：清理失败显式反映在 IndexResult（failed += 1 + errors，沿 R8.2 AR-23③ 先例），不吞掉。
     */
    private void purgeStaleDocsForType(String topic, String entityType, IndexResult result) {
        SearchRequest request = new SearchRequest();
        request.setTopic(topic);
        request.setQuery("");
        request.setTags(Collections.singleton(entityType));
        request.setLimit(PURGE_ENUMERATION_LIMIT);
        try {
            SearchResponse response = searchEngine.search(request);
            List<String> docIds = new ArrayList<>();
            if (response != null && response.getItems() != null) {
                for (SearchHit hit : response.getItems()) {
                    if (hit.getId() != null) {
                        docIds.add(hit.getId());
                    }
                }
            }
            if (!docIds.isEmpty()) {
                searchEngine.removeDocs(topic, docIds);
            }
        } catch (Exception e) {
            LOG.warn("Failed to purge stale docs for type={}", entityType, e);
            result.setFailed(result.getFailed() + 1);
            result.setErrors(List.of("Stale doc purge failed for type: " + entityType));
        }
    }

    private List<SearchableDoc> buildClassificationDocs(IndexResult result) {
        List<NopMetaClassification> entities = daoProvider.daoFor(NopMetaClassification.class).findAll();
        List<SearchableDoc> docs = new ArrayList<>();
        for (NopMetaClassification e : entities) {
            try {
                String name = e.getName();
                String displayName = e.getDisplayName();
                String description = e.getDescription();
                SearchableDoc doc = new SearchableDoc();
                doc.setId(e.getClassificationId());
                doc.setName(name);
                doc.setTitle(displayName);
                doc.setSummary(NopMetadataHelper.truncate(description, 500));
                doc.setContent(NopMetadataHelper.join(" ", name, displayName, description));
                doc.setTagSet(Set.of("Classification"));
                docs.add(doc);
            } catch (Exception ex) {
                LOG.warn("Failed to convert Classification doc", ex);
                result.setFailed(result.getFailed() + 1);
            }
        }
        return docs;
    }

    private List<SearchableDoc> buildTagDocs(IndexResult result) {
        List<NopMetaTag> entities = daoProvider.daoFor(NopMetaTag.class).findAll();
        List<SearchableDoc> docs = new ArrayList<>();
        for (NopMetaTag e : entities) {
            try {
                String name = e.getName() != null ? e.getName() : e.getFullyQualifiedName();
                String displayName = e.getDisplayName();
                String description = e.getDescription();
                SearchableDoc doc = new SearchableDoc();
                doc.setId(e.getTagId());
                doc.setName(name);
                doc.setTitle(displayName);
                doc.setSummary(NopMetadataHelper.truncate(description, 500));
                doc.setContent(NopMetadataHelper.join(" ", name, e.getFullyQualifiedName(), displayName, description));
                doc.setTagSet(Set.of("Tag"));
                docs.add(doc);
            } catch (Exception ex) {
                LOG.warn("Failed to convert Tag doc", ex);
                result.setFailed(result.getFailed() + 1);
            }
        }
        return docs;
    }

    private List<SearchableDoc> buildGlossaryTermDocs(IndexResult result) {
        List<NopMetaGlossaryTerm> entities = daoProvider.daoFor(NopMetaGlossaryTerm.class).findAll();
        List<SearchableDoc> docs = new ArrayList<>();
        for (NopMetaGlossaryTerm e : entities) {
            try {
                String name = e.getName() != null ? e.getName() : e.getFullyQualifiedName();
                String displayName = e.getDisplayName();
                String description = e.getDescription();
                SearchableDoc doc = new SearchableDoc();
                doc.setId(e.getGlossaryTermId());
                doc.setName(name);
                doc.setTitle(displayName);
                doc.setSummary(NopMetadataHelper.truncate(description, 500));
                doc.setContent(NopMetadataHelper.join(" ", name, e.getFullyQualifiedName(), displayName, description, e.getSynonyms()));
                doc.setTagSet(Set.of("GlossaryTerm"));
                docs.add(doc);
            } catch (Exception ex) {
                LOG.warn("Failed to convert GlossaryTerm doc", ex);
                result.setFailed(result.getFailed() + 1);
            }
        }
        return docs;
    }

    private List<SearchableDoc> buildMetaTableDocs(IndexResult result) {
        List<NopMetaTable> entities = daoProvider.daoFor(NopMetaTable.class).findAll();
        List<SearchableDoc> docs = new ArrayList<>();
        for (NopMetaTable e : entities) {
            try {
                String displayName = e.getDisplayName();
                String description = e.getDescription();
                SearchableDoc doc = new SearchableDoc();
                doc.setId(e.getMetaTableId());
                doc.setName(e.getTableName());
                doc.setTitle(displayName);
                doc.setSummary(NopMetadataHelper.truncate(description, 500));
                doc.setContent(NopMetadataHelper.join(" ", e.getTableName(), displayName, description));
                doc.setTagSet(Set.of("MetaTable"));
                docs.add(doc);
            } catch (Exception ex) {
                LOG.warn("Failed to convert MetaTable doc", ex);
                result.setFailed(result.getFailed() + 1);
            }
        }
        return docs;
    }

    private List<SearchableDoc> buildMetaEntityDocs(IndexResult result) {
        List<NopMetaEntity> entities = daoProvider.daoFor(NopMetaEntity.class).findAll();
        List<SearchableDoc> docs = new ArrayList<>();
        for (NopMetaEntity e : entities) {
            try {
                String name = e.getEntityName() != null ? e.getEntityName() : e.getClassName();
                String displayName = e.getDisplayName();
                String remark = e.getRemark();
                SearchableDoc doc = new SearchableDoc();
                doc.setId(e.getMetaEntityId());
                doc.setName(name);
                doc.setTitle(displayName);
                doc.setSummary(NopMetadataHelper.truncate(remark, 500));
                doc.setContent(NopMetadataHelper.join(" ", e.getEntityName(), e.getClassName(), displayName, e.getTagSet(), remark));
                doc.setTagSet(Set.of("MetaEntity"));
                docs.add(doc);
            } catch (Exception ex) {
                LOG.warn("Failed to convert MetaEntity doc", ex);
                result.setFailed(result.getFailed() + 1);
            }
        }
        return docs;
    }

    private List<SearchableDoc> buildMetaEntityFieldDocs(IndexResult result) {
        List<NopMetaEntityField> entities = daoProvider.daoFor(NopMetaEntityField.class).findAll();
        List<SearchableDoc> docs = new ArrayList<>();
        for (NopMetaEntityField e : entities) {
            try {
                String name = e.getFieldName() != null ? e.getFieldName() : e.getColumnCode();
                String displayName = e.getDisplayName();
                String comment = e.getComment();
                SearchableDoc doc = new SearchableDoc();
                doc.setId(e.getEntityFieldId());
                doc.setName(name);
                doc.setTitle(displayName);
                doc.setSummary(NopMetadataHelper.truncate(comment, 500));
                doc.setContent(NopMetadataHelper.join(" ", e.getFieldName(), e.getColumnCode(), displayName, comment));
                doc.setTagSet(Set.of("MetaEntityField"));
                docs.add(doc);
            } catch (Exception ex) {
                LOG.warn("Failed to convert MetaEntityField doc", ex);
                result.setFailed(result.getFailed() + 1);
            }
        }
        return docs;
    }

}
