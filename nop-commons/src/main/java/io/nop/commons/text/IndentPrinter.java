/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.text;

import io.nop.api.core.exceptions.NopException;

import java.io.IOException;

public class IndentPrinter implements Appendable {
    private final Appendable out;
    private final int lineLength;

    private int indentLevel;

    private int lastLinePos;
    private int outputLength;

    private String indentStr = "    ";

    private String lineBreak = "\n";

    public IndentPrinter(Appendable out, int lineLength) {
        this.out = out;
        this.lineLength = lineLength;
    }

    public IndentPrinter(int lineLength) {
        this(new StringBuilder(), lineLength);
    }

    public String toString() {
        return out.toString();
    }

    public IndentPrinter lineBreak(String lineBreak) {
        this.lineBreak = lineBreak;
        return this;
    }

    public IndentPrinter indentStr(String indentStr) {
        this.indentStr = indentStr;
        return this;
    }

    public IndentPrinter indentLevel(int indentLevel) {
        this.indentLevel = indentLevel;
        return this;
    }

    public void incIndent() {
        indentLevel++;
    }

    public void decIndent() {
        indentLevel--;
    }

    @Override
    public IndentPrinter append(CharSequence csq, int start, int end) {
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
    public IndentPrinter append(char c) {
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
    public IndentPrinter append(CharSequence str) {
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

    public IndentPrinter br() {
        try {
            out.append(lineBreak);
            outputLength += lineBreak.length();
            lastLinePos = outputLength;
            return this;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public void indent() {
        br();

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
    }
}