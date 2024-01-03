/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.text.tokenizer;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.util.StringHelper;

public class SimpleTextReader implements ISourceLocationGetter {
    private final SourceLocation baseLoc;
    private final String str;
    private int pos;

    private StringBuilder sb = new StringBuilder();
    private MutableIntArray lineFeedPos;

    public SimpleTextReader(SourceLocation baseLoc, String str) {
        this.baseLoc = baseLoc;
        this.str = str;
    }

    @Override
    public SourceLocation getLocation() {
        if (baseLoc == null)
            return SourceLocation.fromLine("<text>", getLine());
        return baseLoc.offset(getLine(), 0);
    }

    public int getLine() {
        int cnt = 0;
        for (int i = 0; i < pos; i++) {
            if (str.charAt(i) == '\n')
                cnt++;
        }
        return cnt;
    }

    public String toString() {
        return "SimpleTokenizer[len=" + str.length() + ",current=" + StringHelper.shortText(str, pos, 30);
    }

    public SimpleTextReader(String str) {
        this(null, str);
    }

    public boolean isEnd() {
        return pos >= str.length();
    }

    public void next() {
        pos++;
    }

    public void next(int delta) {
        pos += delta;
    }

    public void move(int delta) {
        pos += delta;
        this.skipBlank();
    }

    public int pos() {
        return pos;
    }

    public void moveTo(int pos) {
        this.pos = pos;
    }

    public SimpleTextReader skipBlank() {
        for (int n = str.length(); pos < n; pos++) {
            char c = str.charAt(pos);
            if (!StringHelper.isWhitespace(c))
                break;
        }
        return this;
    }

    public SimpleTextReader skipLine() {
        for (int n = str.length(); pos < n; pos++) {
            char c = str.charAt(pos);
            if (c == '\n') {
                pos++;
                break;
            }
        }
        return this;
    }

    public SimpleTextReader skipUntilChar(char matchChar) {
        for (int n = str.length(); pos < n; pos++) {
            char c = str.charAt(pos);
            if (c == matchChar) {
                break;
            }
        }
        return this;
    }

    public SimpleTextReader skipChars(String chars) {
        for (int n = str.length(); pos < n; pos++) {
            char c = str.charAt(pos);
            if (chars.indexOf(c) < 0) {
                break;
            }
        }
        return this;
    }

    public SourceLocation location() {
        if (lineFeedPos == null) {
            lineFeedPos = new MutableIntArray();
            for (int i = 0, n = str.length(); i < n; i++) {
                char c = str.charAt(i);
                if (c == '\n') {
                    lineFeedPos.add(i);
                } else if (c == '\r') {
                    if (i < n - 1 && str.charAt(i + 1) != '\n') {
                        lineFeedPos.add(i);
                    }
                }
            }
        }

        int line = lineFeedPos.size();
        if (!isEnd()) {
            for (int i = 0, n = lineFeedPos.size(); i < n; i++) {
                int lf = lineFeedPos.get(i);
                if (lf > pos) {
                    line = i + 1;
                    break;
                }
            }
        }
        if (baseLoc != null)
            return baseLoc.offset(line - 1, 0);
        return SourceLocation.fromLine("<text>", line, 0);
    }

    public boolean startsWith(String prefix) {
        if (isEnd())
            return false;
        return str.regionMatches(pos, prefix, 0, prefix.length());
    }

    public boolean tryMatch(String prefix) {
        if (startsWith(prefix)) {
            next(prefix.length());
            skipBlank();
            return true;
        }
        return false;
    }

    public boolean startsWithIgnoreCase(String prefix) {
        if (isEnd())
            return false;
        return str.regionMatches(true, pos, prefix, 0, prefix.length());
    }

    public int find(String sub) {
        return str.indexOf(sub, pos);
    }

    public String substring(int beginIndex, int endIndex) {
        return str.substring(beginIndex, endIndex);
    }

    public String substring(int beginIndex) {
        return str.substring(beginIndex);
    }

    public String readLine() {
        sb.setLength(0);
        for (int n = str.length(); pos < n; pos++) {
            char c = str.charAt(pos);
            if (c == '\r') {
                pos++;
                if (pos < n - 1) {
                    if (str.charAt(pos) == '\n') {
                        pos++;
                    }
                }
                break;
            }
            if (c == '\n') {
                pos++;
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public String nextDupEscape(char sep) {
        sb.setLength(0);
        for (int i = pos, n = str.length(); i < n; i++) {
            char c2 = str.charAt(i);
            if (c2 == sep) {
                if (i < n - 1) {
                    // 重复两遍，因此是转义字符
                    if (str.charAt(i + 1) == sep) {
                        sb.append(sep);
                        i++;
                        continue;
                    }
                }
                pos = i;
                return sb.toString();
            } else {
                sb.append(c2);
            }
        }
        pos = str.length();
        return sb.toString();
    }
}
