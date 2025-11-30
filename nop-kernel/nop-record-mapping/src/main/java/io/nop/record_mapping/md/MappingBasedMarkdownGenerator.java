package io.nop.record_mapping.md;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.mutable.MutableBoolean;
import io.nop.commons.text.SourceCodeBlock;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.record_mapping.RecordMappingContext;
import io.nop.record_mapping.impl.RecordMappingTool;
import io.nop.record_mapping.model.RecordFieldMappingConfig;
import io.nop.record_mapping.model.RecordMappingConfig;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private final RecordMappingTool tool;
    private final RecordMappingContext ctx;

    public MappingBasedMarkdownGenerator(RecordMappingConfig mapping, Object obj, IEvalScope scope) {
        this.mapping = mapping;
        this.obj = obj;
        this.tool = RecordMappingTool.DEFAULT;
        this.ctx = new RecordMappingContext(scope);
    }

    @Override
    public void generateToWriter(Writer out, IEvalContext context) throws IOException {
        // 使用MutableIntArray记录嵌套层次，初始化为空数组
        MutableIntArray levelCounters = new MutableIntArray();
        generateObject(out, obj, mapping, levelCounters);
    }

    /**
     * 核心方法：将对象生成为Markdown章节
     *
     * @param out           输出流
     * @param obj           数据对象
     * @param mapping       映射配置
     * @param levelCounters 标题层级计数器数组
     */
    private void generateObject(Writer out, Object obj,
                                RecordMappingConfig mapping, MutableIntArray levelCounters) {
        tool.executeForObject(mapping, obj, out, ctx, () -> {
            try {
                Set<String> processedFields = new HashSet<>();

                // 1. 生成章节标题（如果配置了titleField）
                RecordFieldMappingConfig titleField = getTitleField(mapping);
                if (titleField != null) {
                    processedFields.add(titleField.getName());
                    writeHeader(out, levelCounters, getObjProp(titleField, obj, out));
                }

                // 2. 先处理所有列表项字段
                boolean hasListItems = generateListItemFields(out, obj, mapping, processedFields);

                // 3. 再处理所有子章节字段
                boolean hasSections = generateSectionFields(out, obj, mapping, levelCounters, processedFields);

                // 4. 格式化：添加适当的空行
                if (hasListItems || hasSections) {
                    out.write("\n"); // 列表项和子章节之间添加空行
                }
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        });

    }

    /**
     * 生成列表项字段（简单值）
     */
    private boolean generateListItemFields(Writer out, Object obj,
                                           RecordMappingConfig mapping,
                                           Set<String> processedFields) throws IOException {
        MutableBoolean hasListItems = new MutableBoolean();

        for (RecordFieldMappingConfig field : mapping.getFields()) {
            String fieldName = field.getName();
            String format = (String) field.prop_get(VAR_MD_FORMAT);

            // 判断是否为列表项字段（简单值）
            if (isListItemField(field, format)) {
                if (!processedFields.add(fieldName))
                    continue;

                tool.executeForField(mapping, field, obj, out, ctx, f -> {
                    Object value = getObjProp(field, obj, out);
                    if (value == null)
                        return;

                    hasListItems.set(true);
                    try {
                        out.write("- " + encodeKey(fieldName) + ": " + encodeValue(value) + "\n");
                    } catch (IOException e) {
                        throw NopException.adapt(e);
                    }
                });
            }
        }

        if (hasListItems.get()) {
            out.write("\n");
        }

        return hasListItems.get();
    }

    /**
     * 生成子章节字段（复杂结构）
     */
    private boolean generateSectionFields(Writer out, Object obj,
                                          RecordMappingConfig mapping, MutableIntArray levelCounters,
                                          Set<String> processedFields) throws IOException {
        MutableBoolean hasSections = new MutableBoolean();

        levelCounters.add(0);

        try {
            for (RecordFieldMappingConfig field : mapping.getFields()) {
                String fieldName = field.getName();
                if (processedFields.contains(field.getName()))
                    continue;

                String format = (String) field.prop_get(VAR_MD_FORMAT);

                tool.executeForField(mapping, field, obj, out, ctx, f -> {

                    Object value = getObjProp(field, obj, out);

                    hasSections.set(true);
                    // 为子章节增加层级计数
                    levelCounters.setLast(levelCounters.last() + 1); // 新层级从1开始

                    try {
                        writeHeader(out, levelCounters, encodeKey(fieldName));

                        // 即使没有值section也要写一个header
                        if (value == null) {
                            return;
                        }

                        if (FORMAT_TABLE.equals(format) && field.getResolvedItemMapping() != null) {
                            // 表格 → 子章节
                            generateTable(out, field.getResolvedItemMapping(), value);
                        } else if (FORMAT_CODE.equals(format)) {
                            // 代码块 → 子章节
                            generateCodeBlock(out, value);
                        } else if (field.getResolvedMapping() != null) {
                            // 嵌套对象 → 子章节
                            generateObject(out, value, field.getResolvedMapping(), levelCounters);
                        } else if (field.getResolvedItemMapping() != null) {
                            // 对象列表 → 子章节列表
                            generateListAsSections(out, field.getResolvedItemMapping(), (List<Object>) value, levelCounters);
                        }
                    } catch (IOException e) {
                        throw NopException.adapt(e);
                    }
                });
            }
        } finally {
            levelCounters.pop();
        }

        return hasSections.get();
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
     * 生成对象列表作为子章节
     */
    private void generateListAsSections(Writer out, RecordMappingConfig itemMapping,
                                        List<Object> list, MutableIntArray levelCounters) throws IOException {
        if (list == null || list.isEmpty()) return;

        levelCounters.add(0);
        try {
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                // 为列表中的每个项目设置序号（替换最后一个层级的计数）
                levelCounters.set(levelCounters.size() - 1, i + 1);
                generateObject(out, item, itemMapping, levelCounters);
            }
        } finally {
            levelCounters.pop();
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
            String name = field.getName();
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
                Object fieldValue = getObjProp(field, row, out);
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
    private void writeHeader(Writer out, MutableIntArray levelCounters, Object text) throws IOException {
        // 第一层不用标号，从第二层开始使用数字标号
        StringBuilder headerBuilder = new StringBuilder();

        // 添加标题标记
        headerBuilder.append("#".repeat(levelCounters.size() + 1));
        headerBuilder.append(" ");

        // 从第二层开始添加数字标号
        if (!levelCounters.isEmpty()) {
            for (int i = 0; i < levelCounters.size(); i++) {
                if (i > 0) headerBuilder.append(".");
                headerBuilder.append(levelCounters.get(i));
            }
            headerBuilder.append(" ");
        }

        headerBuilder.append(encodeKey(StringHelper.toString(text, null)));
        headerBuilder.append("\n\n");

        out.write(headerBuilder.toString());
    }

    /**
     * 获取标题字段名
     */
    private RecordFieldMappingConfig getTitleField(RecordMappingConfig mapping) {
        String titleField = (String) mapping.prop_get(VAR_MD_TITLE_FIELD);
        if (titleField != null) {
            return mapping.requireField(titleField);
        }
        return null;
    }

    protected Object getObjProp(RecordFieldMappingConfig field, Object obj, Writer out) {
        return tool.getFromValue(field, obj, out, ctx);
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