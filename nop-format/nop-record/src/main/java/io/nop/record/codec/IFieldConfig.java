package io.nop.record.codec;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.bytes.EndianKind;
import io.nop.commons.type.StdDataType;

import java.nio.charset.Charset;

public interface IFieldConfig extends ISourceLocationGetter {
    String getName();

    int getLength();

    Charset getCharsetObj();

    String getFormat();

    ByteString getPadding();

    boolean isLeftPad();

    boolean isTrim();

    Integer getScale();

    StdDataType getStdDataType();

    EndianKind getEndian();
}