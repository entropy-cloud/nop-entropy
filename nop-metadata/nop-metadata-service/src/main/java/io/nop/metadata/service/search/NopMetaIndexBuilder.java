package io.nop.metadata.service.search;

import io.nop.dao.api.IDaoProvider;
import io.nop.metadata.core.dto.IndexResult;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.search.api.ISearchEngine;
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

    @Inject
    @Nullable
    @Named("nopSearchEngine")
    protected ISearchEngine searchEngine;

    @Inject
    protected IDaoProvider daoProvider;

    private static String truncate(String s, int maxLen) {
        if (s == null)
            return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    public List<IndexResult> buildFullIndex(List<String> entityTypes) {
        if (searchEngine == null) {
            IndexResult result = new IndexResult();
            result.setEntityType("ALL");
            result.setFailed(1);
            result.setErrors(Collections.singletonList("searchEngine not available"));
            return Collections.singletonList(result);
        }

        if (entityTypes == null) {
            entityTypes = List.of("Classification", "Tag", "GlossaryTerm", "MetaTable", "MetaEntity", "MetaEntityField");
        }

        String topic = NopMetaSearchService.TOPIC;
        List<IndexResult> results = new ArrayList<>();

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
                    LOG.warn("Failed to refresh index for type={}", entityType, e);
                }
            }

            result.setIndexed(docs.size());
            results.add(result);
        }

        return results;
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
                doc.setSummary(truncate(description, 500));
                doc.setContent(join(" ", name, displayName, description));
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
                doc.setSummary(truncate(description, 500));
                doc.setContent(join(" ", name, e.getFullyQualifiedName(), displayName, description));
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
                doc.setSummary(truncate(description, 500));
                doc.setContent(join(" ", name, e.getFullyQualifiedName(), displayName, description, e.getSynonyms()));
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
                doc.setSummary(truncate(description, 500));
                doc.setContent(join(" ", e.getTableName(), displayName, description));
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
                doc.setSummary(truncate(remark, 500));
                doc.setContent(join(" ", e.getEntityName(), e.getClassName(), displayName, e.getTagSet(), remark));
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
                doc.setSummary(truncate(comment, 500));
                doc.setContent(join(" ", e.getFieldName(), e.getColumnCode(), displayName, comment));
                doc.setTagSet(Set.of("MetaEntityField"));
                docs.add(doc);
            } catch (Exception ex) {
                LOG.warn("Failed to convert MetaEntityField doc", ex);
                result.setFailed(result.getFailed() + 1);
            }
        }
        return docs;
    }

    private static String join(String delimiter, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                if (sb.length() > 0)
                    sb.append(delimiter);
                sb.append(part);
            }
        }
        return sb.toString();
    }
}
