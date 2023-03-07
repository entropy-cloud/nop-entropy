/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef.impl;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;

import java.util.List;

public class XDefCommentParser {
    public XDefComment parseComment(SourceLocation loc, String str) {
        TextScanner sc = TextScanner.fromString(loc, str);
        sc.skipBlank();
        XDefComment ret = new XDefComment();
        Comment comment = parseComment(sc);
        if (comment != null) {
            ret.setMainDescription(comment.description);
            ret.setMainDisplayName(comment.displayName);
        }

        while (!sc.isEnd()) {
            parseSubComment(sc, ret);

        }
        return ret;
    }

    private boolean isSubComment(TextScanner sc) {
        return sc.cur == '@' && Character.isJavaIdentifierStart(sc.peek());
    }

    static class Comment {
        String displayName;
        String description;
    }

    private Comment parseComment(TextScanner sc) {
        int pos = sc.pos;
        while (!isSubComment(sc) && !sc.isEnd()) {
            sc.skipLine();
            sc.skipBlankInLine();
        }
        String s = sc.getBaseSequence().subSequence(pos, sc.pos).toString();
        String displayName = null;
        if (s.startsWith("[")) {
            int pos2 = s.indexOf(']');
            if (pos2 > 0) {
                displayName = s.substring(1, pos2);
                s = s.substring(pos2 + 1);
            }
        }
        List<String> list = StringHelper.stripedSplit(s, '\n');
        String description = StringHelper.join(list, "\n");

        Comment comment = new Comment();
        comment.displayName = displayName;
        comment.description = description;
        return comment;
    }

    void parseSubComment(TextScanner sc, XDefComment ret) {
        sc.consume('@');
        String name = sc.nextXmlName();
        sc.skipBlankInLine();
        Comment comment = parseComment(sc);
        if (name == null)
            return;
        if (comment.description != null)
            ret.addSubDescription(name, comment.description);
        if (comment.displayName != null) {
            ret.addSubDisplayName(name, comment.displayName);
        }
    }
}
