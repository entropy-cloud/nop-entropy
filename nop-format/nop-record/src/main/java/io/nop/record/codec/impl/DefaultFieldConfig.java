package io.nop.record.codec.impl;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.bytes.EndianKind;
import io.nop.commons.type.StdDataType;
import io.nop.record.codec.IFieldConfig;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class DefaultFieldConfig implements IFieldConfig {
    private String name;
    private int length;
    private Charset charsetObj = StandardCharsets.UTF_8;
    private String format;
    private ByteString padding;
    private boolean leftPad;
    private boolean trim;
    private Integer scale;
    private StdDataType stdDataType;
    private EndianKind endian;
    private SourceLocation location;

    public DefaultFieldConfig(String name, StdDataType dataType, int length) {
        this.setName(name);
        this.setStdDataType(dataType);
        this.setLength(length);
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public Charset getCharsetObj() {
        return charsetObj;
    }

    public void setCharsetObj(Charset charsetObj) {
        this.charsetObj = charsetObj;
    }

    @Override
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    @Override
    public ByteString getPadding() {
        return padding;
    }

    public void setPadding(ByteString padding) {
        this.padding = padding;
    }

    @Override
    public boolean isLeftPad() {
        return leftPad;
    }

    public void setLeftPad(boolean leftPad) {
        this.leftPad = leftPad;
    }

    @Override
    public boolean isTrim() {
        return trim;
    }

    public void setTrim(boolean trim) {
        this.trim = trim;
    }

    @Override
    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    @Override
    public StdDataType getStdDataType() {
        return stdDataType;
    }

    public void setStdDataType(StdDataType stdDataType) {
        this.stdDataType = stdDataType;
    }

    @Override
    public EndianKind getEndian() {
        return endian;
    }

    public void setEndian(EndianKind endian) {
        this.endian = endian;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }
}
