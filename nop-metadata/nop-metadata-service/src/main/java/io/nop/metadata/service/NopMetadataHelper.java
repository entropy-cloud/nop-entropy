package io.nop.metadata.service;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.search.api.SearchableDoc;

import java.util.Map;
import java.util.Set;

public final class NopMetadataHelper {

    private NopMetadataHelper() {
    }

    public static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    public static String join(String delimiter, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                if (sb.length() > 0) sb.append(delimiter);
                sb.append(part);
            }
        }
        return sb.toString();
    }

    public static String stringOf(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }

    public static String toErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg != null) return msg;
        if (e instanceof NopException) return ((NopException) e).getErrorCode();
        return e.getClass().getName();
    }

    public static SearchableDoc toSearchableDoc(NopMetaEntity entity) {
        SearchableDoc doc = new SearchableDoc();
        doc.setId(entity.getMetaEntityId());
        doc.setName(entity.getEntityName());
        doc.setTitle(entity.getDisplayName());
        doc.setSummary(truncate(entity.getRemark(), 500));
        doc.setContent(join(" ", entity.getEntityName(), entity.getClassName(), entity.getDisplayName(), entity.getTagSet(), entity.getRemark()));
        doc.setTagSet(Set.of("MetaEntity"));
        return doc;
    }

    public static SearchableDoc toSearchableDoc(NopMetaTable entity) {
        SearchableDoc doc = new SearchableDoc();
        doc.setId(entity.getMetaTableId());
        doc.setName(entity.getTableName());
        doc.setTitle(entity.getDisplayName());
        doc.setSummary(truncate(entity.getDescription(), 500));
        doc.setContent(join(" ", entity.getTableName(), entity.getDisplayName(), entity.getDescription()));
        doc.setTagSet(Set.of("MetaTable"));
        return doc;
    }

    public static SearchableDoc toSearchableDoc(NopMetaEntityField entity) {
        SearchableDoc doc = new SearchableDoc();
        doc.setId(entity.getEntityFieldId());
        doc.setName(entity.getFieldName());
        doc.setTitle(entity.getDisplayName());
        doc.setSummary(truncate(entity.getComment(), 500));
        doc.setContent(join(" ", entity.getFieldName(), entity.getColumnCode(), entity.getDisplayName(), entity.getComment()));
        doc.setTagSet(Set.of("MetaEntityField"));
        return doc;
    }
}
