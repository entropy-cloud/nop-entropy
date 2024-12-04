package io.nop.record.model;

import io.nop.commons.type.StdDataType;
import io.nop.core.type.IGenericType;
import io.nop.record.codec.IFieldConfig;
import io.nop.record.model._gen._RecordSimpleFieldMeta;
import io.nop.xlang.xmeta.ISchema;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class RecordSimpleFieldMeta extends _RecordSimpleFieldMeta implements IFieldConfig {
    private Charset charsetObj;

    public RecordSimpleFieldMeta() {

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
}
