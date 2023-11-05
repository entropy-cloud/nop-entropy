/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml.handler;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.CDataText;
import io.nop.commons.text.RawText;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.xml.XNode;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import static io.nop.core.CoreConfigs.CFG_XML_FORMAT_MAX_CHARS_PER_LINE;
import static io.nop.core.CoreErrors.ERR_EVAL_OUTPUT_TEXT_FAIL;
import static io.nop.core.CoreErrors.ERR_XML_INVALID_INSTRUCTION;

public class CollectXmlHandler extends XNodeHandlerAdapter {
    private final int LINE_MAX_CHARS = CFG_XML_FORMAT_MAX_CHARS_PER_LINE.get();

    private final Appendable out;
    private int indentLevel;
    private boolean indent = true;
    private boolean forHtml = false;
    private boolean dumpSourceLocation = false;
    private boolean indentRoot = true;

    private SourceLocation loc;

    private OutputType prevType = null;

    enum OutputType {
        COMMENT, NODE, VALUE
    }

    public CollectXmlHandler(Appendable out) {
        this.out = out;
    }

    public CollectXmlHandler indentRoot(boolean indentRoot) {
        this.indentRoot = indentRoot;
        return this;
    }

    public CollectXmlHandler indent(boolean indent) {
        this.indent = indent;
        return this;
    }

    public CollectXmlHandler forHtml(boolean forHtml) {
        this.forHtml = forHtml;
        return this;
    }

    public CollectXmlHandler dumpSourceLocation(boolean dumpSourceLocation) {
        this.dumpSourceLocation = dumpSourceLocation;
        return this;
    }

    void instruction(String instruction) {
        if (instruction.contains("?>"))
            throw new NopException(ERR_XML_INVALID_INSTRUCTION);
        try {
            out.append("<?");
            out.append(instruction);
            out.append("?>\n");
        } catch (IOException e) {
            throw new NopException(ERR_EVAL_OUTPUT_TEXT_FAIL, e);
        }
    }

    void appendIndent(int indentLevel) throws IOException {
        for (int i = 0; i < indentLevel; i++) {
            out.append("    ");
        }
    }

    void doIndent() throws IOException {
        if (!indent)
            return;

        if (indentLevel > 0 || prevType == OutputType.NODE) {
            out.append('\n');
        }

        appendIndent(indentLevel);
    }

    @Override
    public void comment(String comment) {
        if (comment == null || comment.isEmpty())
            return;

        try {
            if (prevType != null && indent)
                out.append('\n');
            doIndent();
            out.append("<!--");
            // XML注释中不能含有连续的-，例如 <!-- -- -->和 <!---- A-->都不可以
            comment = StringHelper.replace(comment, "--", "- - ");
            comment = StringHelper.replace(comment, "\r\n", "\n");
            boolean multiline = comment.indexOf('\n') >= 0;
            if (indent && multiline) {
                writeMultilineComment(comment, StringHelper.repeat("    ", indentLevel + 1));
                appendIndent(indentLevel);
            } else {
                out.append(comment);
            }
            out.append("-->");
            if (indentLevel == 0)
                out.append('\n');
            prevType = OutputType.COMMENT;
        } catch (IOException e) {
            throw new NopException(ERR_EVAL_OUTPUT_TEXT_FAIL, e);
        }
    }

    void writeMultilineComment(String comment, String indent) throws IOException {
        String[] parts = StringHelper.splitToArray(comment, '\n');
        // 增加一个空行
        if (parts[0].length() > 0) {
            out.append('\n');
        }
        for (int i = 0, n = parts.length; i < n; i++) {
            String part = parts[i];
            if (part.length() > 0) {
                out.append(indent).append(part);
            }
            // 忽略最后一个空行
            if (i != n - 1 || part.length() > 0)
                out.append('\n');
        }
    }

    void dumpLoc(SourceLocation loc, Map<String, ValueWithLocation> attrs) throws IOException {
        if (this.dumpSourceLocation) {
            boolean dumpNodeLoc = false;
            if (loc != null) {
                if (this.loc != null && (!this.loc.getPath().equals(loc.getPath()))) {
                    out.append("\n<!--LOC:");
                    StringHelper.escapeXmlValueTo(loc.toString(), out);
                    dumpNodeLoc = true;
                }
                this.loc = loc;
            }

            boolean hasAttr = false;

            String prevAttrPath = null;
            for (Map.Entry<String, ValueWithLocation> attr : attrs.entrySet()) {
                if (attr.getValue().getLocation() != null) {
                    SourceLocation attrLoc = attr.getValue().getLocation();
                    if (this.loc != null && !Objects.equals(prevAttrPath, attrLoc.getPath())
                            && !this.loc.getPath().equals(attrLoc.getPath())) {
                        if (!dumpNodeLoc) {
                            out.append("\n<!--LOC:");
                            StringHelper.escapeXmlValueTo(this.loc.toString(), out);
                            dumpNodeLoc = true;
                        }

                        out.append("\n @").append(attr.getKey()).append("=");
                        StringHelper.escapeXmlAttrTo(attrLoc.toString(), out);
                        hasAttr = true;

                        prevAttrPath = attrLoc.getPath();
                    }
                }
            }

            if (dumpNodeLoc) {
                if (hasAttr)
                    out.append('\n');
                out.append("-->");
                // 如果是根节点
                if (prevType == null)
                    out.append('\n');
            }
        }
    }

    void _beginNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) throws IOException {
        if (indent && indentRoot) {
            // 根结点的子节点多输出一个分隔行
            boolean htmlInline = forHtml && CoreConstants.HTML_INLINE_TAG_NAMES.contains(tagName);
            if (!htmlInline) {
                if (indentLevel == 1 && prevType != OutputType.COMMENT) {
                    out.append('\n');
                }
            }
        }
        dumpLoc(loc, attrs);

        doIndent();
        prevType = OutputType.NODE;

        int nChar = tagName.length() + 1 + indentLevel * 4;

        out.append('<');
        out.append(tagName);
        if (!attrs.isEmpty()) {
            for (Map.Entry<String, ValueWithLocation> entry : attrs.entrySet()) {
                String name = entry.getKey();
                String value = ConvertHelper.toString(entry.getValue().getValue());
                if (value != null) {
                    value = StringHelper.escapeXmlAttr(value);
                }

                if (indent) {
                    int n = name.length() + 4 + value.length();
                    if (nChar + n > LINE_MAX_CHARS) {
                        doIndent();
                        printBlank(tagName.length() + 1);
                        nChar = indentLevel * 4 + tagName.length() + 1;
                    }

                    nChar += n;
                }

                out.append(' ');
                out.append(name);
                out.append("=\"");
                out.append(value);
                out.append('"');
            }
        }
    }

    void printBlank(int n) throws IOException {
        for (int i = 0; i < n; i++) {
            out.append(' ');
        }
    }

    @Override
    public void simpleNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
        try {
            _beginNode(loc, tagName, attrs);

            boolean complex = forHtml && !CoreConstants.HTML_SHORT_TAG_NAMES.contains(tagName);
            if (complex) {
                out.append("></").append(tagName).append(">");
            } else {
                out.append("/>");
            }
            prevType = OutputType.NODE;
        } catch (IOException e) {
            throw new NopException(ERR_EVAL_OUTPUT_TEXT_FAIL, e).loc(loc);
        }
    }

    @Override
    public void beginNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
        try {
            _beginNode(loc, tagName, attrs);
            out.append('>');
            indentLevel++;
        } catch (IOException e) {
            throw new NopException(ERR_EVAL_OUTPUT_TEXT_FAIL, e).loc(loc);
        }
    }

    @Override
    public void value(SourceLocation loc, Object value) {
        if (value == null)
            return;
        try {
            if (value instanceof CDataText) {
                String s = value.toString();
                out.append("<![CDATA[");
                s = StringHelper.replace(s, "]]>", "]]]]><![CDATA[>");
                out.append(s);
                out.append("]]>");
            }else if(value instanceof RawText){
                out.append(((RawText) value).getText());
            } else {
                String s = value.toString();
                StringHelper.escapeXmlValueTo(s, out);
            }
            prevType = OutputType.VALUE;
        } catch (IOException e) {
            throw new NopException(ERR_EVAL_OUTPUT_TEXT_FAIL, e).loc(loc);
        }
    }

    @Override
    public void text(SourceLocation loc, String text) {
        if (text == null)
            return;
        try {
            StringHelper.escapeXmlValueTo(text, out);
            prevType = OutputType.VALUE;
        } catch (IOException e) {
            throw new NopException(ERR_EVAL_OUTPUT_TEXT_FAIL, e).loc(loc);
        }
    }

    @Override
    public void endNode(String tagName) {
        try {
            indentLevel--;
            if (prevType != OutputType.VALUE && indent) {
                out.append('\n');

                appendIndent(indentLevel);
            }
            out.append("</");
            out.append(tagName);
            out.append('>');
            prevType = OutputType.NODE;
        } catch (IOException e) {
            throw new NopException(ERR_EVAL_OUTPUT_TEXT_FAIL, e);
        }
    }

    @Override
    public void beginDoc(String encoding, String docType, String instruction) {
        try {
            out.append("<?xml version=\"1.0\"");
            if (encoding == null) {
                encoding = "UTF-8";
            }
            out.append(" encoding=\"").append(encoding).append("\" ?>\n");
        } catch (IOException e) {
            throw new NopException(ERR_EVAL_OUTPUT_TEXT_FAIL, e);
        }
    }

    @Override
    public XNode endDoc() {
        return null;
    }
}