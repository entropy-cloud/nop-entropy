package io.nop.record_mapping.md;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.model.table.impl.BaseTable;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.model.MarkdownListItem;
import io.nop.markdown.model.MarkdownSection;
import io.nop.markdown.simple.MarkdownCodeBlockParser;
import io.nop.markdown.simple.MarkdownListParser;
import io.nop.markdown.table.MarkdownTableParser;
import io.nop.markdown.utils.MarkdownHelper;
import io.nop.record_mapping.IRecordMapping;
import io.nop.record_mapping.RecordMappingContext;
import io.nop.record_mapping.impl.RecordMappingTool;
import io.nop.record_mapping.model.RecordFieldMappingConfig;
import io.nop.record_mapping.model.RecordMappingConfig;

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

public class MappingBasedMarkdownParser implements IRecordMapping {
    private final RecordMappingConfig mapping;
    private final RecordMappingTool tool;

    public MappingBasedMarkdownParser(RecordMappingConfig mapping) {
        this.mapping = mapping;
        this.tool = RecordMappingTool.DEFAULT;
    }

    public Object parseMarkdown(MarkdownSection doc) {
        return map(doc, new RecordMappingContext());
    }

    @Override
    public Object newTarget() {
        return mapping.newTarget();
    }

    @Override
    public void map(Object source, Object target, RecordMappingContext ctx) {
        mapSectionAsObject(mapping, getSection(source), target, ctx);
    }

    protected MarkdownSection getSection(Object source) {
        if (source instanceof MarkdownDocument)
            return ((MarkdownDocument) source).getRootSection();
        return (MarkdownSection) source;
    }

    protected void mapSectionAsObject(RecordMappingConfig mapping, MarkdownSection section,
                                      Object target, RecordMappingContext ctx) {
        tool.executeForObject(mapping, section, target, ctx, () -> {
            // 设置标题字段
            mapTitleField(mapping, section, target, ctx);

            // 处理内容
            mapSectionContent(mapping, section, target, ctx);
        });
    }

    protected void mapTitleField(RecordMappingConfig mapping, MarkdownSection section,
                                 Object target, RecordMappingContext ctx) {
        RecordFieldMappingConfig titleField = getTitleField(mapping);
        if (titleField != null) {
            tool.executeForField(mapping, titleField, section, target, ctx, field -> {
                Object titleValue = tool.processFieldValue(mapping, field, section.getTitle(), ctx);
                tool.setTargetValue(field, target, titleValue, ctx);
            });
        }
    }

    protected void mapSectionContent(RecordMappingConfig mapping, MarkdownSection section,
                                     Object target, RecordMappingContext ctx) {
        // 处理列表项
        List<MarkdownListItem> items = MarkdownListParser.NESTED.parseAllListItems(
                section.getContentLocation(), section.getContent());
        if (items != null && !items.isEmpty()) {
            mapListItems(mapping, items, target, ctx);
        }

        // 处理子章节
        if (section.hasChild()) {
            mapSectionChildren(mapping, section.getChildren(), target, ctx);
        }
    }

    protected void mapListItems(RecordMappingConfig mapping, List<MarkdownListItem> items,
                                Object target, RecordMappingContext ctx) {
        for (MarkdownListItem item : items) {
            Pair<String, String> pair = parseNameValuePair(item.getContent());
            String fromName = pair.getKey();

            // 查找匹配的字段配置
            RecordFieldMappingConfig field = mapping.requireFieldByFrom(item.getLocation(), fromName);
            tool.executeForField(mapping, field, item, target, ctx, f -> {
                mapListItemField(mapping, f, item, pair, target, ctx);
            });
        }
    }

    protected void mapListItemField(RecordMappingConfig mapping,
                                    RecordFieldMappingConfig field, MarkdownListItem item,
                                    Pair<String, String> pair, Object target, RecordMappingContext ctx) {
        if (field.getResolvedMapping() != null) {
            // 映射到对象
            Object fieldValue = tool.makeTargetObject(field, item, target, ctx);
            mapListItems(field.getResolvedMapping(), item.getChildren(), fieldValue, ctx);
        } else if (field.getResolvedItemMapping() != null) {
            // 映射到列表
            Object fieldValue = tool.makeTargetCollection(field, item, target, ctx);
            mapListItemCollection(field, item.getChildren(), item, fieldValue, ctx);
        } else {
            // 简单值映射
            if (!StringHelper.isEmpty(pair.getValue())) {
                throw new NopException(ERR_RECORD_MD_LIST_ITEM_NOT_SIMPLE_VALUE)
                        .source(item)
                        .param(ARG_FIELD_NAME, field.getName())
                        .param(ARG_FROM_NAME, pair.getKey());
            }

            Object value = tool.processFieldValue(mapping, field, pair.getValue(), ctx);
            tool.setTargetValue(field, target, value, ctx);
        }
    }

    protected void mapListItemCollection(RecordFieldMappingConfig field, List<MarkdownListItem> items,
                                         Object source, Object target, RecordMappingContext ctx) {
        tool.mapCollectionField(field, items, source, target, ctx, (fromItem, toItem) -> {
            mapListItemAsObject(field.getResolvedItemMapping(), (MarkdownListItem) fromItem, toItem, ctx);
        });
    }

    protected void mapListItemAsObject(RecordMappingConfig itemMapping, MarkdownListItem item,
                                       Object target, RecordMappingContext ctx) {
        tool.executeForObject(itemMapping, item, target, ctx, () -> {
            // 设置标题字段
            RecordFieldMappingConfig titleField = getTitleField(itemMapping);
            if (titleField != null) {
                tool.executeForField(itemMapping, titleField, item, target, ctx, field -> {
                    Object titleValue = tool.processFieldValue(itemMapping, field, item.getContent(), ctx);
                    tool.setTargetValue(field, target, titleValue, ctx);
                });
            }

            // 处理子项
            mapListItems(itemMapping, item.getChildren(), target, ctx);
        });
    }

    protected void mapSectionChildren(RecordMappingConfig mapping, List<MarkdownSection> children,
                                      Object target, RecordMappingContext ctx) {
        if (children == null || children.isEmpty()) return;

        for (MarkdownSection child : children) {
            String key = decodeKey(child.getTitle());

            // 查找匹配的字段配置
            RecordFieldMappingConfig field = mapping.requireFieldByFrom(child.getLocation(), key);

            tool.executeForField(mapping, field, child, target, ctx, f -> {
                mapSectionField(mapping, f, child, target, ctx);
            });
        }
    }

    protected void mapSectionField(RecordMappingConfig mapping,
                                   RecordFieldMappingConfig field, MarkdownSection section,
                                   Object target, RecordMappingContext ctx) {
        String format = (String) field.prop_get(VAR_MD_FORMAT);

        if (FORMAT_TABLE.equals(format)) {
            mapTableField(mapping, field, section, target, ctx);
        } else if (field.getResolvedMapping() != null) {
            mapObjectField(field, section, target, ctx);
        } else if (field.getResolvedItemMapping() != null) {
            mapCollectionField(field, section, target, ctx);
        } else {
            mapSimpleField(mapping, field, section, format, target, ctx);
        }
    }

    protected void mapTableField(RecordMappingConfig mapping,
                                 RecordFieldMappingConfig field, MarkdownSection section,
                                 Object target, RecordMappingContext ctx) {
        if (section.hasChild()) {
            throw new NopException(ERR_RECORD_MD_SECTION_NOT_ALLOW_SUB_SECTION)
                    .source(section)
                    .param(ARG_MD_FORMAT, FORMAT_TABLE);
        }

        Object tableData = parseContentTable(section);
        Object value = tool.processFieldValue(mapping, field, tableData, ctx);
        tool.setTargetValue(field, target, value, ctx);
    }

    protected void mapObjectField(RecordFieldMappingConfig field, MarkdownSection section,
                                  Object target, RecordMappingContext ctx) {
        Object fieldValue = tool.makeTargetObject(field, section, target, ctx);
        mapSectionAsObject(field.getResolvedMapping(), section, fieldValue, ctx);
    }

    protected void mapCollectionField(RecordFieldMappingConfig field, MarkdownSection section,
                                      Object target, RecordMappingContext ctx) {
        Object fieldValue = tool.makeTargetCollection(field, section, target, ctx);
        mapSectionCollection(field, section, fieldValue, ctx);
    }

    protected void mapSectionCollection(RecordFieldMappingConfig field, MarkdownSection section,
                                        Object target, RecordMappingContext ctx) {
        List<MarkdownListItem> items = MarkdownListParser.NESTED.parseAllListItems(
                section.getContentLocation(), section.getContent());

        if (items != null && !items.isEmpty()) {
            if (section.hasChild()) {
                throw new NopException(ERR_RECORD_MD_SECTION_NOT_ALLOW_SUB_SECTION)
                        .source(section);
            }
            mapListItemCollection(field, items, null, target, ctx);
            return;
        }

        RecordMappingConfig itemMapping = field.getResolvedItemMapping();
        tool.mapCollectionField(field, section.getChildren(), section, target, ctx, (fromItem, toItem) -> {
            mapSectionAsObject(itemMapping, (MarkdownSection) fromItem, toItem, ctx);
        });
    }

    protected void mapSimpleField(RecordMappingConfig mapping,
                                  RecordFieldMappingConfig field, MarkdownSection section,
                                  String format, Object target, RecordMappingContext ctx) {
        if (section.hasChild()) {
            throw new NopException(ERR_RECORD_MD_SECTION_NOT_ALLOW_SUB_SECTION)
                    .source(section)
                    .param(ARG_MD_FORMAT, format);
        }

        Object contentValue = parseSectionContent(field, format, section);
        Object value = tool.processFieldValue(mapping, field, contentValue, ctx);
        tool.setTargetValue(field, target, value, ctx);
    }

    RecordFieldMappingConfig getTitleField(RecordMappingConfig mapping) {
        String titleField = (String) mapping.prop_get(VAR_MD_TITLE_FIELD);
        if (titleField != null) {
            return mapping.requireField(titleField);
        }
        return null;
    }

    Pair<String, String> parseNameValuePair(String text) {
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

    protected Object parseSectionContent(RecordFieldMappingConfig field,
                                         String format, MarkdownSection section) {
        if (StringHelper.isBlank(section.getContent()))
            return null;

        if (FORMAT_CODE.equals(format)) {
            return MarkdownCodeBlockParser.INSTANCE.parseCodeBlockForLang(section.getContentLocation(),
                    section.getContent(), null);
        } else {
            return section.getContent();
        }
    }

    protected Object parseContentTable(MarkdownSection section) {
        if (StringHelper.isBlank(section.getContent()))
            return null;

        int pos = MarkdownHelper.findTable(section.getContent());
        if (pos < 0 || !StringHelper.isBlank(section.getContent().substring(0, pos)))
            throw new NopException(ERR_RECORD_MD_SECTION_CONTENT_NOT_TABLE)
                    .loc(section.getContentLocation());

        SourceLocation loc = section.getContentLocation();
        BaseTable table = MarkdownTableParser.parseTable(loc, section.getContent());
        return MarkdownHelper.toRecordList(table);
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