/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.annotations.graphql.GraphQLScalar;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.IJsonString;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

import static io.nop.api.core.ApiErrors.ARG_PART;
import static io.nop.api.core.ApiErrors.ARG_VALUE;
import static io.nop.api.core.ApiErrors.ERR_INVALID_SOURCE_LOCATION_STRING;

/**
 * 描述文本文件中的一段文本的所在位置。
 *
 * @author canonical_entropy@163.com
 */
@DataBean
@ImmutableBean
@GraphQLScalar
public class SourceLocation implements Serializable, IJsonString {
    private static final long serialVersionUID = -5678978218084960219L;

    public static SourceLocation UNKNOWN = SourceLocation.fromPath("<unknown>");

    static final String PROP_PATH = "path";
    static final String PROP_LEN = "len";
    static final String PROP_LINE = "line";
    static final String PROP_COL = "col";
    static final String PROP_SHEET = "sheet";
    static final String PROP_CELL = "cell";
    static final String PROP_REF = "ref";
    static final String PROP_POS = "pos";

    private final String path;

    /**
     * 从1开始计数
     */
    private final int line;

    /**
     * 从1开始计数
     */
    private final int col;


    private final int len;

    private final int pos;

    /**
     * 用于在Excel文件中定位。如果sheet和cell非空，则line和col对应于Excel单元格文本中的位置
     */
    private final String sheet;
    private final String cell;

    /**
     * 调试用的附加信息
     */
    private final String ref;

    private String text;

    /**
     * path表示文件路径，sheet+cell表示文件内的单元路径。它们加在一起后形成复杂文件内的定位路径。line是在此路径基础上的按行定位
     */
    private String cellPath;

    @JsonCreator
    public SourceLocation(@JsonProperty(PROP_PATH) String path,
                          @JsonProperty(PROP_LINE) int line,
                          @JsonProperty(PROP_COL) int col,
                          @JsonProperty(PROP_LEN) int length,
                          @JsonProperty(PROP_POS) int pos,
                          @JsonProperty(PROP_SHEET) String sheet,
                          @JsonProperty(PROP_CELL) String cell,
                          @JsonProperty(PROP_REF) String ref) {
        this.path = Guard.notEmpty(path, "empty path");
        this.line = line;
        this.col = col;
        this.len = length;
        this.pos = pos;
        this.sheet = sheet;
        this.cell = cell;
        this.ref = ref;
    }

    public static SourceLocation getLocation(Object obj) {
        if (obj instanceof ISourceLocationGetter)
            return ((ISourceLocationGetter) obj).getLocation();
        return null;
    }

    public static SourceLocation fromLine(String path, int line, int col) {
        return fromLine(path, line, col, 0);
    }

    public static SourceLocation fromLine(String path, int line) {
        return fromLine(path, line, 0);
    }

    public static SourceLocation fromLine(String path, int line, int col, int len) {
        return new SourceLocation(path, line, col, len, 0, null, null, null);
    }

    public static SourceLocation fromClass(Class clazz) {
        return fromPath("class:" + clazz);
    }

    public static SourceLocation fromPath(String path) {
        return fromLine(path, 1, 0, 0);
    }

    public String getPath() {
        return path;
    }

    @JsonIgnore
    public String getCellPath() {
        if (sheet == null && cell == null)
            return path;

        if (cellPath != null)
            return cellPath;

        StringBuilder sb = new StringBuilder();
        sb.append(path).append('?');
        if (sheet != null) {
            sb.append(sheet);
        }
        if (cell != null) {
            sb.append('!').append(cell);
        }
        cellPath = sb.toString();
        return cellPath;
    }

    public String toString() {
        if (text == null)
            text = buildText();
        return text;
    }

    public static SourceLocation fromMap(Map<String, Object> map) {
        String path = ConvertHelper.toString(map.get(PROP_PATH));
        int line = ConvertHelper.toPrimitiveInt(map.get(PROP_LINE), NopException::new);
        int col = ConvertHelper.toPrimitiveInt(map.get(PROP_COL), NopException::new);
        int len = ConvertHelper.toPrimitiveInt(map.get(PROP_LEN), NopException::new);
        int pos = ConvertHelper.toPrimitiveInt(map.get(PROP_POS), NopException::new);
        String sheet = ConvertHelper.toString(map.get(PROP_SHEET));
        String cell = ConvertHelper.toString(map.get(PROP_CELL));
        String ref = ConvertHelper.toString(map.get(PROP_REF));
        return new SourceLocation(path, line, col, len, pos, sheet, cell, ref);
    }

    /**
     * 解析sourceLocation文本表示。其格式为 [line:col:len:pos]path?sheet!cell#ref
     *
     * @param loc SourceLocation.toString()得到的文本
     */
    @StaticFactoryMethod
    public static SourceLocation parse(String loc) {
        if (ApiStringHelper.isEmpty(loc))
            return null;

        int posBracket = loc.indexOf(']');
        int pos2 = ApiStringHelper.indexOfInRange(loc, ':', 1, posBracket);
        if (posBracket < 0 || pos2 < 0 || loc.charAt(0) != '[')
            throw newCheckError(loc, "line");

        int pos3 = ApiStringHelper.indexOfInRange(loc, ':', pos2 + 1, posBracket);
        if (pos3 < 0)
            throw newCheckError(loc, "col");

        int posX = ApiStringHelper.indexOfInRange(loc, ':', pos3 + 1, posBracket);
        if (posX < 0)
            throw newCheckError(loc, "pos");

        int pos4 = loc.indexOf('#', posBracket);
        int tagStart = pos4 < 0 ? loc.length() : pos4;
        int pos5 = loc.lastIndexOf('?', tagStart - 1);
        int pathEnd = pos5 > 0 ? pos5 : tagStart;

        int line = ConvertHelper.toInt(loc.substring(1, pos2), err -> newCheckError(loc, "line"));
        int col = ConvertHelper.toInt(loc.substring(pos2 + 1, pos3), err -> newCheckError(loc, "col"));
        int len = ConvertHelper.toInt(loc.substring(pos3 + 1, posX), err -> newCheckError(loc, "len"));
        int pos = ConvertHelper.toInt(loc.substring(posX + 1, posBracket), err -> newCheckError(loc, "pos"));

        String path = loc.substring(posBracket + 1, pathEnd);
        String tag = pos4 > 0 ? loc.substring(pos4 + 1) : null;
        String sheet = null, cell = null;

        if (line < 0)
            throw newCheckError(loc, "line");

        if (pos5 > 0) {
            int pos6 = ApiStringHelper.indexOfInRange(loc, '!', pos5 + 1, tagStart);
            if (pos6 > 0) {
                sheet = loc.substring(pos5 + 1, pos6);
                cell = loc.substring(pos6 + 1, tagStart);
            } else {
                sheet = loc.substring(pos5 + 1, tagStart);
            }
        }

        return new SourceLocation(path, line, col, len, pos, sheet, cell, tag);
    }

    static private NopException newCheckError(String loc, String part) {
        return new NopException(ERR_INVALID_SOURCE_LOCATION_STRING).param(ARG_VALUE, loc).param(ARG_PART, part);
    }

    String buildText() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(line).append(':').append(col).append(':').append(len).append(':').append(pos);
        sb.append(']');
        sb.append(path);
        if (sheet != null) {
            sb.append('?');
            sb.append(sheet);
        }

        if (cell != null) {
            sb.append('!').append(cell);
        }

        if (ref != null)
            sb.append('#').append(ref);
        return sb.toString();
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o instanceof SourceLocation) {
            return toString().equals(o.toString());
        }
        return false;
    }

    public int getPos() {
        return pos;
    }

    public int getLine() {
        return line;
    }

    public int getCol() {
        return col;
    }

    public int getLen() {
        return len;
    }

    @JsonInclude(Include.NON_EMPTY)
    public String getSheet() {
        return sheet;
    }

    @JsonInclude(Include.NON_EMPTY)
    public String getCell() {
        return cell;
    }

    @JsonInclude(Include.NON_EMPTY)
    public String getRef() {
        return ref;
    }

    public boolean isSameLine(SourceLocation loc) {
        if (line == loc.line)
            return Objects.equals(path, loc.path)
                    && Objects.equals(sheet, loc.sheet)
                    && Objects.equals(cell, loc.cell);
        return false;
    }

    public SourceLocation addRef(String ref) {
        if (ApiStringHelper.isEmpty(ref))
            return this;

        String mergedRef = ref;
        if (this.ref != null)
            mergedRef = this.ref + "|" + ref;
        return new SourceLocation(path, line, col, len, pos, sheet, cell, mergedRef);
    }

    public SourceLocation position(int line, int col, int len) {
        return new SourceLocation(path, line, col, len, 0, sheet, cell, ref);
    }

    public SourceLocation offset(int line, int col) {
        return position(this.line + line, this.col + col, 0);
    }

    private String externalPath;

    /**
     * 为了优化保留的临时缓存，对应于IResource.getExternalPath
     */
    @JsonIgnore
    public String getExternalPath() {
        return externalPath;
    }

    public void setExternalPath(String externalPath) {
        this.externalPath = externalPath;
    }
}