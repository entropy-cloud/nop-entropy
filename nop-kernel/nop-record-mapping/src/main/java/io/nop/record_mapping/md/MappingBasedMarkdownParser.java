package io.nop.record_mapping.md;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.json.JObject;
import io.nop.core.model.table.impl.BaseTable;
import io.nop.markdown.model.MarkdownListItem;
import io.nop.markdown.model.MarkdownSection;
import io.nop.markdown.simple.MarkdownCodeBlockParser;
import io.nop.markdown.simple.MarkdownListParser;
import io.nop.markdown.table.MarkdownTableParser;
import io.nop.markdown.utils.MarkdownHelper;
import io.nop.record_mapping.model.RecordFieldMappingConfig;
import io.nop.record_mapping.model.RecordMappingConfig;

import java.util.ArrayList;
import java.util.List;

import static io.nop.record_mapping.RecordMappingConstants.FORMAT_CODE;
import static io.nop.record_mapping.RecordMappingConstants.FORMAT_TABLE;
import static io.nop.record_mapping.RecordMappingConstants.VAR_MD_FORMAT;
import static io.nop.record_mapping.RecordMappingConstants.VAR_MD_TITLE_FIELD;
import static io.nop.record_mapping.RecordMappingErrors.ARG_FIELD_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ARG_FROM_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ARG_MD_FORMAT;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_MD_LIST_ITEM_NOT_SIMPLE_VALUE;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_MD_SECTION_CONTENT_NOT_TABLE;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_MD_SECTION_NOT_ALLOW_SUB_SECTION;

public class MappingBasedMarkdownParser {
    private final RecordMappingConfig mapping;

    public MappingBasedMarkdownParser(RecordMappingConfig mapping) {
        this.mapping = mapping;
    }

    public Object parseMarkdown(MarkdownSection section) {
        return parseSectionAsObject(section);
    }

    JObject parseSectionAsObject(MarkdownSection section) {
        JObject obj = new JObject();
        obj.setLocation(section.getLocation());

        String name = getTitleFieldName(mapping);
        if (name != null) {
            obj.put(name, ValueWithLocation.of(section.getLocation(), section.getTitle()));
        }

        parseSectionAsProps(section, obj);
        return obj;
    }

    void parseSectionAsProps(MarkdownSection section, JObject obj) {
        List<MarkdownListItem> items = MarkdownListParser.NESTED.parseAllListItems(
                section.getContentLocation(), section.getContent());

        parseListItemsAsProps(mapping, items, obj);

        parseSectionChildrenAsProps(mapping, section.getChildren(), obj);
    }

    String getTitleFieldName(RecordMappingConfig mapping) {
        String titleField = (String) mapping.prop_get(VAR_MD_TITLE_FIELD);
        if (titleField != null) {
            RecordFieldMappingConfig field = mapping.requireField(titleField);
            return field.getFromOrName();
        }
        return null;
    }

    void parseListItemsAsProps(RecordMappingConfig mapping,
                               List<MarkdownListItem> items, JObject obj) {
        for (MarkdownListItem item : items) {
            Pair<String, String> pair = parseNameValuePair(item.getContent());
            RecordFieldMappingConfig field = mapping.requireFieldByFrom(item.getLocation(), pair.getKey());
            if (field.getResolvedMapping() != null || field.getResolvedItemMapping() != null) {
                if (!StringHelper.isEmpty(pair.getValue()))
                    throw new NopException(ERR_RECORD_MD_LIST_ITEM_NOT_SIMPLE_VALUE)
                            .source(item)
                            .param(ARG_FIELD_NAME, field.getName())
                            .param(ARG_FROM_NAME, pair.getKey());
            }

            if (field.getResolvedMapping() != null) {
                JObject itemObj = new JObject();
                itemObj.setLocation(item.getLocation());
                parseListItemsAsProps(field.getResolvedMapping(), item.getChildren(), obj);
                obj.put(pair.getKey(), itemObj);
            } else if (field.getResolvedItemMapping() != null) {
                List<Object> ret = parseListItemsAsList(field.getResolvedItemMapping(), item.getChildren());
                obj.put(pair.getKey(), ValueWithLocation.of(item.getLocation(), ret));
            } else {
                obj.put(pair.getKey(), ValueWithLocation.of(item.getLocation(), pair.getValue()));
            }
        }
    }

    Pair<String, String> parseNameValuePair(String text) {
        // name: value 这种形式
        String key, value;

        int pos = text.indexOf(':');
        if (pos >= 0) {
            key = text.substring(0, pos);
            value = text.substring(pos + 1);
        } else {
            key = text;
            value = null;
        }
        return Pair.of(decodeKey(key), decodeValue(value));
    }

    List<Object> parseListItemsAsList(RecordMappingConfig itemMapping, List<MarkdownListItem> items) {
        if (items == null)
            return null;

        List<Object> ret = new ArrayList<>(items.size());

        for (MarkdownListItem item : items) {
            JObject obj = new JObject();
            obj.setLocation(item.getLocation());

            String name = getTitleFieldName(mapping);
            if (name != null) {
                obj.put(name, ValueWithLocation.of(item.getLocation(), item.getContent()));
            }

            parseListItemsAsProps(itemMapping, item.getChildren(), obj);
            ret.add(obj);
        }
        return ret;
    }

    void parseSectionChildrenAsProps(RecordMappingConfig mapping,
                                     List<MarkdownSection> children, JObject obj) {
        if (children == null || children.isEmpty())
            return;

        for (MarkdownSection child : children) {
            String key = decodeKey(child.getTitle());
            RecordFieldMappingConfig field = mapping.requireFieldByFrom(child.getLocation(), key);
            String format = (String) field.prop_get(VAR_MD_FORMAT);
            if (FORMAT_TABLE.equals(format)) {
                ValueWithLocation value = parseContentTable(child);
                obj.put(key, value);
            } else if (field.getResolvedMapping() != null) {
                JObject subObj = new JObject();
                subObj.setLocation(child.getContentLocation());
                parseSectionAsProps(child, subObj);
            } else if (field.getResolvedItemMapping() != null) {
                List<Object> list = parseSectionChildrenAsList(field.getResolvedItemMapping(), child);
                obj.put(key, ValueWithLocation.of(child.getContentLocation(), list));
            } else {
                Object value = parseSectionContent(field, format, child);
                obj.put(key, ValueWithLocation.of(child.getContentLocation(), value));
            }
        }
    }

    List<Object> parseSectionChildrenAsList(RecordMappingConfig itemMapping, MarkdownSection section) {
        List<MarkdownListItem> items = MarkdownListParser.NESTED.parseAllListItems(
                section.getContentLocation(), section.getContent());

        if (items != null && !items.isEmpty()) {
            if (section.hasChild()) {
                throw new NopException(ERR_RECORD_MD_SECTION_NOT_ALLOW_SUB_SECTION)
                        .source(section);
            }
            return parseListItemsAsList(itemMapping, items);
        }

        return parseSectionChildrenAsList(itemMapping, section);
    }

    Object parseSectionContent(RecordFieldMappingConfig field, String format, MarkdownSection section) {
        if (section.hasChild())
            throw new NopException(ERR_RECORD_MD_SECTION_NOT_ALLOW_SUB_SECTION)
                    .source(section)
                    .param(ARG_MD_FORMAT, format);

        if (StringHelper.isBlank(section.getContent()))
            return null;

        if (FORMAT_CODE.equals(format)) {
            return MarkdownCodeBlockParser.INSTANCE.parseCodeBlockForLang(section.getContentLocation(), section.getContent(), null);
        } else {
            return section.getContent();
        }
    }

    ValueWithLocation parseContentTable(MarkdownSection section) {
        if (section.hasChild())
            throw new NopException(ERR_RECORD_MD_SECTION_NOT_ALLOW_SUB_SECTION)
                    .source(section)
                    .param(ARG_MD_FORMAT, FORMAT_TABLE);

        if (StringHelper.isBlank(section.getContent()))
            return null;

        int pos = MarkdownHelper.findTable(section.getContent());
        if (pos < 0 || !StringHelper.isBlank(section.getContent().substring(0, pos)))
            throw new NopException(ERR_RECORD_MD_SECTION_CONTENT_NOT_TABLE)
                    .loc(section.getContentLocation());

        SourceLocation loc = section.getContentLocation();
        BaseTable table = MarkdownTableParser.parseTable(loc, section.getContent());
        return ValueWithLocation.of(table.getLocation(), MarkdownHelper.toRecordList(table));
    }

    String decodeKey(String key) {
        key = key.trim();
        key = MarkdownHelper.removeStyle(key);
        return key;
    }

    String decodeValue(String text) {
        if (text == null || text.isEmpty())
            return null;
        text = text.trim();
        return StringHelper.unquote(text);
    }
}
