/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text;

import io.nop.api.core.exceptions.NopException;

import java.io.IOException;

public class CodeBuilder implements Appendable {
    private final Appendable out;
    private final int lineLength;

    private int indentLevel;

    private int lastLinePos;
    private int outputLength;

    private String indentStr = "  ";

    private String lineBreak = "\n";

    public CodeBuilder(Appendable out, int lineLength) {
        this.out = out;
        this.lineLength = lineLength;
    }

    public CodeBuilder(int lineLength) {
        this(new StringBuilder(), lineLength);
    }

    public CodeBuilder() {
        this(120);
    }

    public String toString() {
        return out.toString();
    }

    public CodeBuilder lineBreak(String lineBreak) {
        this.lineBreak = lineBreak;
        return this;
    }

    public CodeBuilder indentStr(String indentStr) {
        this.indentStr = indentStr;
        return this;
    }

    public CodeBuilder indentLevel(int indentLevel) {
        this.indentLevel = indentLevel;
        return this;
    }

    public CodeBuilder incIndent() {
        indentLevel++;
        return this;
    }

    public CodeBuilder decIndent() {
        indentLevel--;
        return this;
    }

    @Override
    public CodeBuilder append(CharSequence csq, int start, int end) {
        int len = end - start;

        if (outputLength + len - lastLinePos > lineLength) {
            indent();
        }
        try {
            out.append(csq, start, end);
            outputLength += 1;
            return this;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public CodeBuilder append(char c) {
        if (outputLength + 1 - lastLinePos > lineLength) {
            indent();
        }
        try {
            out.append(c);
            outputLength += 1;
            return this;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public CodeBuilder append(CharSequence str) {
        if (outputLength + str.length() - lastLinePos > lineLength) {
            indent();
        }

        try {
            out.append(str);
            outputLength += str.length();
            return this;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public CodeBuilder line() {
        try {
            out.append(lineBreak);
            outputLength += lineBreak.length();
            lastLinePos = outputLength;
            return this;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public CodeBuilder indent() {
        return line().printIndent();
    }

    public CodeBuilder printIndent() {
        if (indentLevel > 0) {
            try {
                for (int i = 0; i < indentLevel; i++) {
                    out.append(indentStr);
                }
                outputLength += indentLevel * indentStr.length();
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
        return this;
    }

    public CodeBuilder line(String format, Object... args) {
        printIndent();
        return append(String.format(format, args)).line();
    }
}