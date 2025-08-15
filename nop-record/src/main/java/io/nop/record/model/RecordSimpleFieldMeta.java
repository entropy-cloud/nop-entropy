package io.nop.record.model;

import io.nop.commons.type.StdDataType;
import io.nop.core.type.IGenericType;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldConfig;
import io.nop.record.codec.IFieldTextCodec;
import io.nop.record.model._gen._RecordSimpleFieldMeta;
import io.nop.xlang.xmeta.ISchema;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class RecordSimpleFieldMeta extends _RecordSimpleFieldMeta implements IFieldConfig {
    private IFieldTextCodec resolvedTextCodec;
    private IFieldBinaryCodec resolvedBinaryCodec;

    private RecordObjectMeta objectMeta;

    private Charset charsetObj;

    public RecordSimpleFieldMeta() {

    }


    public String getRecordObjectName() {
        return objectMeta == null ? null : objectMeta.getName();
    }

    public RecordObjectMeta getObjectMeta() {
        return objectMeta;
    }

    public void setObjectMeta(RecordObjectMeta objectMeta) {
        this.objectMeta = objectMeta;
    }


    public IFieldTextCodec getResolvedTextCodec() {
        return resolvedTextCodec;
    }

    public void setResolvedTextCodec(IFieldTextCodec resolvedTextCodec) {
        this.resolvedTextCodec = resolvedTextCodec;
    }

    public IFieldBinaryCodec getResolvedBinaryCodec() {
        return resolvedBinaryCodec;
    }

    public void setResolvedBinaryCodec(IFieldBinaryCodec resolvedBinaryCodec) {
        this.resolvedBinaryCodec = resolvedBinaryCodec;
    }

    public String getPropOrFieldName() {
        String propName = getProp();
        if (propName == null)
            propName = getName();
        return propName;
    }

    @Override
    public Charset getCharsetObj() {
        if (charsetObj == null) {
            String charset = getCharset();
            if (charset != null) {
                charsetObj = Charset.forName(charset);
            } else {
                charsetObj = StandardCharsets.UTF_8;
            }
        }
        return charsetObj;
    }

    @Override
    public StdDataType getStdDataType() {
        IGenericType type = getType();
        return type == null ? StdDataType.ANY : type.getStdDataType();
    }

    @Override
    public Integer getScale() {
        ISchema schema = getSchema();
        return schema == null ? null : schema.getScale();
    }


    public int safeGetMaxLen() {
        ISchema schema = getSchema();
        if (schema != null && schema.getMaxLength() != null)
            return schema.getMaxLength();
        return getLength();
    }

    public int safeGetMinLen() {
        ISchema schema = getSchema();
        if (schema != null && schema.getMinLength() != null)
            return schema.getMinLength();
        return getLength();
    }
}
