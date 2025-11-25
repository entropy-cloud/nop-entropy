package io.nop.record_mapping.md;

import io.nop.commons.text.SourceCodeBlock;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.record_mapping.model.RecordFieldMappingConfig;
import io.nop.record_mapping.model.RecordMappingConfig;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.nop.record_mapping.RecordMappingConstants.FORMAT_CODE;
import static io.nop.record_mapping.RecordMappingConstants.FORMAT_TABLE;
import static io.nop.record_mapping.RecordMappingConstants.VAR_MD_FORMAT;
import static io.nop.record_mapping.RecordMappingConstants.VAR_MD_TITLE_FIELD;

/**
 * 基于映射配置的Markdown生成器
 * 与MappingBasedMarkdownParser完全双向兼容
 */
public class MappingBasedMarkdownGenerator implements ITextTemplateOutput {
    private final RecordMappingConfig mapping;
    private final Object obj;

    public MappingBasedMarkdownGenerator(RecordMappingConfig mapping, Object obj) {
        this.mapping = mapping;
        this.obj = obj;
    }

    @Override
    public void generateToWriter(Writer out, IEvalContext context) throws IOException {
        generateObject(out, obj, mapping, 0);
    }

    /**
     * 核心方法：将对象生成为Markdown章节
     *
     * @param out     输出流
     * @param obj     数据对象
     * @param mapping 映射配置
     * @param level   标题层级（0=顶级，1=二级标题##）
     */
    private void generateObject(Writer out, Object obj,
                                RecordMappingConfig mapping, int level) throws IOException {
        // 1. 生成章节标题（如果配置了titleField）
        String titleField = getTitleFieldName(mapping);
        if (titleField != null) {
            writeHeader(out, level, getObjProp(obj, titleField));
        }

        // 2. 先处理所有列表项字段
        boolean hasListItems = generateListItemFields(out, obj, mapping);

        // 3. 再处理所有子章节字段
        boolean hasSections = generateSectionFields(out, obj, mapping, level);

        // 4. 格式化：添加适当的空行
        if (hasListItems && hasSections) {
            out.write("\n"); // 列表项和子章节之间添加空行
        }
    }

    /**
     * 生成列表项字段（简单值）
     */
    private boolean generateListItemFields(Writer out, Object obj,
                                           RecordMappingConfig mapping) throws IOException {
        boolean hasListItems = false;

        for (RecordFieldMappingConfig field : mapping.getFields()) {
            String fieldName = field.getFromOrName();
            String format = (String) field.prop_get(VAR_MD_FORMAT);

            // 判断是否为列表项字段（简单值）
            if (isListItemField(field, format)) {
                Object value = getObjProp(obj, fieldName);
                if (value == null)
                    continue;

                hasListItems = true;
                out.write("- " + encodeKey(fieldName) + ": " + encodeValue(value) + "\n");
            }
        }

        if (hasListItems) {
            out.write("\n");
        }

        return hasListItems;
    }

    /**
     * 生成子章节字段（复杂结构）
     */
    private boolean generateSectionFields(Writer out, Object obj,
                                          RecordMappingConfig mapping, int level) throws IOException {
        boolean hasSections = false;

        for (RecordFieldMappingConfig field : mapping.getFields()) {
            String fieldName = field.getFromOrName();

            String format = (String) field.prop_get(VAR_MD_FORMAT);

            // 判断是否为子章节字段
            if (isSectionField(field, format)) {
                Object value = getObjProp(obj, fieldName);
                if (value == null)
                    continue;

                hasSections = true;
                writeHeader(out, level + 1, encodeKey(fieldName));

                if (FORMAT_TABLE.equals(format) && value instanceof List) {
                    // 表格 → 子章节
                    generateTable(out, field.getResolvedItemMapping(), value);
                } else if (FORMAT_CODE.equals(format)) {
                    // 代码块 → 子章节
                    generateCodeBlock(out, value);
                } else if (field.getResolvedMapping() != null) {
                    // 嵌套对象 → 子章节
                    generateObject(out, value, field.getResolvedMapping(), level + 1);
                } else if (field.getResolvedItemMapping() != null) {
                    // 对象列表 → 子章节列表
                    generateListAsSections(out, field.getResolvedItemMapping(), (List<Object>) value, level + 1);
                }
            }
        }

        return hasSections;
    }

    /**
     * 判断是否为列表项字段
     */
    private boolean isListItemField(RecordFieldMappingConfig field, String format) {
        // 简单值字段：不是复杂结构，也不是特殊格式
        return field.getResolvedMapping() == null &&
                field.getResolvedItemMapping() == null &&
                !FORMAT_TABLE.equals(format) &&
                !FORMAT_CODE.equals(format);
    }

    /**
     * 判断是否为子章节字段
     */
    private boolean isSectionField(RecordFieldMappingConfig field, String format) {
        // 嵌套对象、对象列表、表格格式、代码块格式都作为子章节
        return (field.getResolvedMapping() != null) ||
                (field.getResolvedItemMapping() != null) ||
                (FORMAT_TABLE.equals(format)) ||
                FORMAT_CODE.equals(format);
    }

    /**
     * 生成对象列表作为子章节
     */
    private void generateListAsSections(Writer out, RecordMappingConfig itemMapping,
                                        List<Object> list, int level) throws IOException {
        if (list == null || list.isEmpty()) return;

        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof Map) {
                Map<String, Object> itemMap = (Map<String, Object>) item;
                generateObject(out, itemMap, itemMapping, level);
                // 列表项之间添加空行（除了最后一个）
                if (i < list.size() - 1) {
                    out.write("\n");
                }
            } else {
                // 简单值列表项（作为当前章节的列表）
                out.write("- " + encodeValue(item) + "\n");
            }
        }
    }

    /**
     * 生成Markdown表格
     * 使用手动拼接确保与Parser完全兼容
     */
    private void generateTable(Writer out, RecordMappingConfig itemMapping, Object value) throws IOException {
        if (value == null)
            return;

        List<?> records = (List<?>) value;

        out.write('|');
        for (RecordFieldMappingConfig field : itemMapping.getFields()) {
            String name = field.getFrom();
            out.append(StringHelper.escapeMarkdown(name));
            out.append('|');
        }
        out.write('\n');
        // 2. 分隔线
        out.write("| ");
        for (RecordFieldMappingConfig field : itemMapping.getFields()) {
            out.write("--- | ");
        }
        out.write("\n");

        // 3. 数据行
        for (Object row : records) {
            out.write("| ");
            for (RecordFieldMappingConfig field : itemMapping.getFields()) {
                String from = field.getFrom();
                Object fieldValue = null;
                if (from != null)
                    fieldValue = getObjProp(row, from);
                out.write(encodeValue(fieldValue) + " | ");
            }
            out.write("\n");
        }

        out.write("\n");
    }

    /**
     * 生成代码块
     */
    private void generateCodeBlock(Writer out, Object value) throws IOException {
        if (value instanceof SourceCodeBlock) {
            ((SourceCodeBlock) value).writeAsMarkdown(out);
        } else {
            String code = StringHelper.toString(value, null);
            SourceCodeBlock.writeAsMarkdown(null, code, out);
        }
    }

    /**
     * 写入章节标题
     */
    private void writeHeader(Writer out, int level, Object text) throws IOException {
        // level=0 → # 一级标题，level=1 → ## 二级标题
        out.write("#".repeat(Math.max(1, level + 1)) + " " + encodeKey(StringHelper.toString(text, null)) + "\n\n");
    }

    /**
     * 获取标题字段名
     */
    private String getTitleFieldName(RecordMappingConfig mapping) {
        String titleField = (String) mapping.prop_get(VAR_MD_TITLE_FIELD);
        if (titleField != null) {
            RecordFieldMappingConfig field = mapping.getField(titleField);
            if (field != null) {
                String name = field.getFrom();
                return name != null ? name : field.getName();
            }
        }
        return null;
    }

    protected Object getObjProp(Object obj, String propName) {
        return BeanTool.getComplexProperty(obj, propName);
    }

    /**
     * 编码键：移除样式标记，trim处理
     * 与Parser的decodeKey对称
     */
    protected String encodeKey(String str) {
        if (str == null) return "";
        // 包含冒号、换行、引号时需转义
        if (str.contains(":") || str.contains("\n") || str.contains("\"") || str.contains("|")) {
            // 双引号内部的双引号需要转义
            return StringHelper.quote(str);
        }
        return str;
    }

    /**
     * 编码值：对特殊字符进行引用处理
     * 与Parser的decodeValue对称
     */
    protected String encodeValue(Object value) {
        if (value == null) return "";
        String str;
        if (value instanceof Collection) {
            str = StringHelper.join((Collection<?>) value, ",");
        } else {
            str = String.valueOf(value).trim();
        }

        // 如果值为空，直接返回空字符串
        if (str.isEmpty()) return "";

        // 包含冒号、换行、引号时需转义
        if (str.contains(":") || str.contains("\n") || str.contains("\"") || str.contains("|")) {
            // 双引号内部的双引号需要转义
            return StringHelper.quote(str);
        }

        return str;
    }
}