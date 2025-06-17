package io.nop.record.codegen;

import io.nop.api.core.util.Symbol;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.text.CodeBuilder;
import io.nop.commons.text.SimpleTextTemplate;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;

import java.util.Map;

public class RecordSerializerCodeGenerator {

    public String generateSerializer(RecordObjectMeta recordMeta, String packageName, String className) {
        CodeBuilder cb = new CodeBuilder();

        // 生成包声明和导入
        generatePackageAndImports(cb, packageName);

        // 生成类声明
        cb.line("public class {} extends AbstractRecordSerializer<{}> {",
                className, getOutputType(recordMeta));
        cb.incIndent();

        // 生成主要的writeObject方法
        generateWriteObjectMethod(cb, recordMeta);

        // 生成字段写入方法
        generateFieldWriteMethods(cb, recordMeta);

        // 生成辅助方法
        generateHelperMethods(cb, recordMeta);

        cb.decIndent();
        cb.line("}");

        return cb.toString();
    }

    private void generatePackageAndImports(CodeBuilder cb, String packageName) {
        cb.line("package {};", packageName);
        cb.line();
        cb.line("import io.nop.record.codec.IFieldCodecContext;");
        cb.line("import io.nop.record.writer.IDataWriterBase;");
        cb.line("import io.nop.commons.collections.bit.IBitSet;");
        cb.line("import java.io.IOException;");
        cb.line("import java.util.Collection;");
        cb.line();
    }

    private void generateWriteObjectMethod(CodeBuilder cb, RecordObjectMeta recordMeta) {
        String recordType = getRecordJavaType(recordMeta);

        cb.line("@Override");
        cb.line("public boolean writeObject({} out, {} record, IFieldCodecContext context) throws IOException {",
                getOutputType(recordMeta), recordType);
        cb.incIndent();

        // 生成writeWhen检查
        if (recordMeta.getWriteWhen() != null) {
            cb.line("if (!checkWriteWhen_{0}(record, context)) {", recordMeta.getName());
            cb.incIndent();
            cb.line("return false;");
            cb.decIndent();
            cb.line("}");
            cb.line();
        }

        // 生成beforeWrite调用
        if (recordMeta.getBeforeWrite() != null) {
            cb.line("beforeWrite_{0}(out, record, context);", recordMeta.getName());
        }

        // 生成基类写入
        if (recordMeta.getResolvedBaseType() != null) {
            cb.line("writeBaseType_{0}(out, record, context);",
                    recordMeta.getResolvedBaseType().getName());
        }

        // 生成tags写入
        cb.line("IBitSet tags = writeTags_{0}(out, record, context);", recordMeta.getName());

        // 生成字段写入
        generateFieldsWriteCode(cb, recordMeta);

        // 生成afterWrite调用
        if (recordMeta.getAfterWrite() != null) {
            cb.line("afterWrite_{0}(out, record, context);", recordMeta.getName());
        }

        cb.line("return true;");
        cb.decIndent();
        cb.line("}");
        cb.line();
    }

    private void generateFieldsWriteCode(CodeBuilder cb, RecordObjectMeta fields) {
        if (fields.getNormalizedTemplate() != null) {
            generateTemplateWriteCode(cb, fields);
        } else if (!fields.getFields().isEmpty()) {
            generateDirectFieldsWriteCode(cb, fields);
        } else {
            cb.line("throw new RuntimeException(\"No fields defined for type: {}\");",
                    fields.getName());
        }
    }

    private void generateTemplateWriteCode(CodeBuilder cb, RecordObjectMeta fields) {
        SimpleTextTemplate template = fields.getNormalizedTemplate();
        cb.line("// Template-based field writing");

        for (int i = 0; i < template.getParts().size(); i++) {
            Object part = template.getParts().get(i);
            if (part instanceof Symbol) {
                String fieldName = ((Symbol) part).getText();
                RecordFieldMeta field = fields.requireField(fieldName);

                cb.line("if (isFieldMatch_{0}(tags)) {", fieldName);
                cb.incIndent();
                generateSingleFieldWrite(cb, field, fields);
                cb.decIndent();
                cb.line("}");
            } else {
                cb.line("writeStaticString_{0}(out, context);", i);
            }
        }
    }

    private void generateDirectFieldsWriteCode(CodeBuilder cb, RecordObjectMeta fields) {
        cb.line("// Direct field writing");
        for (RecordFieldMeta field : fields.getFields()) {
            cb.line("if (isFieldMatch_{0}(tags)) {", field.getName());
            cb.incIndent();
            generateSingleFieldWrite(cb, field, fields);
            cb.decIndent();
            cb.line("}");
        }
    }

    private void generateSingleFieldWrite(CodeBuilder cb, RecordFieldMeta field, RecordObjectMeta recordMeta) {
        String fieldName = field.getName();
        String recordType = getRecordJavaType(recordMeta);

        cb.line("context.enterField(\"{}\");", fieldName);
        cb.line("try {");
        cb.incIndent();

        // 生成offset处理
        if (field.getOffset() > 0) {
            cb.line("writeOffset(out, {}, context);", field.getOffset());
        }

        // 生成beforeWrite
        if (field.getBeforeWrite() != null) {
            cb.line("beforeWrite_{0}(out, record, context);", fieldName);
        }

        // 生成主要字段写入逻辑
        if (field.getRepeatKind() != null) {
            generateCollectionFieldWrite(cb, field);
        } else {
            generateSingleValueFieldWrite(cb, field);
        }

        // 生成afterWrite
        if (field.getAfterWrite() != null) {
            cb.line("afterWrite_{0}(out, record, context);", fieldName);
        }

        cb.decIndent();
        cb.line("} finally {");
        cb.incIndent();
        cb.line("context.exitField();");
        cb.decIndent();
        cb.line("}");
    }

    private void generateSingleValueFieldWrite(CodeBuilder cb, RecordFieldMeta field) {
        String fieldName = field.getName();
        String javaType = getFieldJavaType(field);

        // 生成值获取
        cb.line("{} value = get{}(record);", javaType, StringHelper.capitalize(fieldName));

        // 生成类型切换逻辑
        if (field.getSwitchOnField() != null || field.getTypeRef() != null) {
            generateTypeSwitchWrite(cb, field);
        } else {
            cb.line("writeField_{0}(out, value, context);", fieldName);
        }
    }

    private void generateCollectionFieldWrite(CodeBuilder cb, RecordFieldMeta field) {
        String fieldName = field.getName();
        String elementType = getFieldElementType(field);

        cb.line("Collection<{}> collection = get{}(record);", elementType, StringHelper.capitalize(fieldName));
        cb.line("writeCollection_{0}(out, collection, context);", fieldName);
    }

    private void generateTypeSwitchWrite(CodeBuilder cb, RecordFieldMeta field) {
        if (field.getSwitchOnField() != null) {
            cb.line("String switchValue = get{}(record);",
                    StringHelper.capitalize(field.getSwitchOnField()));
            cb.line("switch(switchValue) {");
            cb.incIndent();

            for (Map.Entry<String, String> entry : field.getSwitchTypeMap().entrySet()) {
                cb.line("case \"{}\":", entry.getKey());
                cb.incIndent();
                cb.line("write{}Type(out, value, context);",
                        StringHelper.capitalize(entry.getValue()));
                cb.line("break;");
                cb.decIndent();
            }

            cb.line("default:");
            cb.incIndent();
            cb.line("throw new RuntimeException(\"Unknown case value: \" + switchValue);");
            cb.decIndent();

            cb.decIndent();
            cb.line("}");
        } else if (field.getTypeRef() != null) {
            cb.line("write{}Type(out, value, context);",
                    StringHelper.capitalize(field.getTypeRef()));
        }
    }

    private void generateFieldWriteMethods(CodeBuilder cb, RecordObjectMeta recordMeta) {
        for (RecordFieldMeta field : recordMeta.getFields()) {
            generateFieldWriteMethod(cb, field, recordMeta);
            generateFieldGetterMethod(cb, field, recordMeta);
        }
    }

    private void generateFieldWriteMethod(CodeBuilder cb, RecordFieldMeta field, RecordObjectMeta recordMeta) {
        String fieldName = field.getName();
        String javaType = getFieldJavaType(field);

        cb.line("private void writeField_{0}({1} out, {2} value, IFieldCodecContext context) throws IOException {",
                fieldName, getOutputType(recordMeta), javaType);
        cb.incIndent();

        // 生成具体的字段写入逻辑，根据字段类型
        generateFieldTypeSpecificWrite(cb, field);

        cb.decIndent();
        cb.line("}");
        cb.line();
    }

    private void generateFieldGetterMethod(CodeBuilder cb, RecordFieldMeta field, RecordObjectMeta recordMeta) {
        String fieldName = field.getName();
        String javaType = getFieldJavaType(field);
        String recordType = getRecordJavaType(recordMeta);

        cb.line("private {} get{}({} record) {", javaType, StringHelper.capitalize(fieldName), recordType);
        cb.incIndent();

        if (field.getContent() != null) {
            cb.line("return {};", genLiteral(field.getContent()));
        } else if (field.getExportExpr() != null) {
            cb.line("// Expression-based field - needs runtime evaluation");
            cb.line("return ({}) exportExpr_{}.call1(null, record, context.getEvalScope());",
                    javaType, fieldName);
        } else {
            String propName = field.getPropOrFieldName();
            if (field.isVirtual()) {
                cb.line("return ({}) record;", javaType);
            } else {
                cb.line("return record.get{}();", StringHelper.capitalize(propName));
            }
        }

        cb.decIndent();
        cb.line("}");
        cb.line();
    }

    private String genLiteral(ByteString content) {
        return null;
    }

    private void generateFieldTypeSpecificWrite(CodeBuilder cb, RecordFieldMeta field) {
        // 根据字段的具体类型生成写入代码
        if (field.getStdDataType() != null) {
            StdDataType handler = field.getStdDataType();
            switch (handler.getName()) {
                case "string":
                    cb.line("writeString(out, value, getCharset(), context);");
                    break;
                case "int":
                    cb.line("writeInt(out, value, context);");
                    break;
                case "long":
                    cb.line("writeLong(out, value, context);");
                    break;
                case "double":
                    cb.line("writeDouble(out, value, context);");
                    break;
                case "boolean":
                    cb.line("writeBoolean(out, value, context);");
                    break;
                default:
                    cb.line("writeGeneric(out, value, context);");
            }
        } else {
            cb.line("writeGeneric(out, value, context);");
        }
    }

    private void generateHelperMethods(CodeBuilder cb, RecordObjectMeta recordMeta) {
        // 生成各种辅助方法
        generateWriteWhenMethods(cb, recordMeta);
        generateTagMethods(cb, recordMeta);
        generateBeforeAfterMethods(cb, recordMeta);
    }

    private void generateWriteWhenMethods(CodeBuilder cb, RecordObjectMeta recordMeta) {
        if (recordMeta.getWriteWhen() != null) {
            cb.line("private boolean checkWriteWhen_{0}({1} record, IFieldCodecContext context) {",
                    recordMeta.getName(), getRecordJavaType(recordMeta));
            cb.incIndent();
            cb.line("// Generated from writeWhen expression");
            cb.line("return true; // TODO: implement expression evaluation");
            cb.decIndent();
            cb.line("}");
            cb.line();
        }
    }

    private void generateTagMethods(CodeBuilder cb, RecordObjectMeta recordMeta) {
        cb.line("private IBitSet writeTags_{0}({1} out, {2} record, IFieldCodecContext context) throws IOException {",
                recordMeta.getName(), getOutputType(recordMeta), getRecordJavaType(recordMeta));
        cb.incIndent();
        cb.line("// TODO: implement tag writing logic");
        cb.line("return null;");
        cb.decIndent();
        cb.line("}");
        cb.line();
    }

    private void generateBeforeAfterMethods(CodeBuilder cb, RecordObjectMeta recordMeta) {
        if (recordMeta.getBeforeWrite() != null) {
            cb.line("private void beforeWrite_{0}({1} out, {2} record, IFieldCodecContext context) {",
                    recordMeta.getName(), getOutputType(recordMeta), getRecordJavaType(recordMeta));
            cb.incIndent();
            cb.line("// TODO: implement beforeWrite logic");
            cb.decIndent();
            cb.line("}");
            cb.line();
        }

        if (recordMeta.getAfterWrite() != null) {
            cb.line("private void afterWrite_{0}({1} out, {2} record, IFieldCodecContext context) {",
                    recordMeta.getName(), getOutputType(recordMeta), getRecordJavaType(recordMeta));
            cb.incIndent();
            cb.line("// TODO: implement afterWrite logic");
            cb.decIndent();
            cb.line("}");
            cb.line();
        }
    }

    // 辅助方法
    private String getRecordJavaType(RecordObjectMeta recordMeta) {
        return null;
    }

    private String getFieldJavaType(RecordFieldMeta field) {
        if (field.getStdDataType() != null) {
            return field.getStdDataType().getJavaTypeName();
        }
        return "Object";
    }

    private String getFieldElementType(RecordFieldMeta field) {
        // 获取集合元素类型
        return getFieldJavaType(field);
    }

    private String getOutputType(RecordObjectMeta recordMeta) {
        // 根据记录类型确定输出类型
        return "IDataWriterBase"; // 可以根据具体需求调整
    }
}