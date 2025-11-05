/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.handler.XNodeHandlerAdapter;
import io.nop.core.model.table.CellPosition;
import io.nop.ooxml.common.IOfficePackagePart;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class CommentsPart {
    private final Map<CellPosition, Comment> comments = new LinkedHashMap<>();

    public static CommentsPart parse(IOfficePackagePart part) {
        CommentsPart ret = new CommentsPart();
        ret.parseFrom(part);
        return ret;
    }

    public void forEachComment(BiConsumer<CellPosition, Comment> action) {
        comments.forEach(action);
    }

    public Set<CellPosition> getCellAddresses() {
        return comments.keySet();
    }

    private void parseFrom(IOfficePackagePart part) {
        part.processXml(new ParseHandler(), null);
    }

    class ParseHandler extends XNodeHandlerAdapter {
        private StringBuilder buf = new StringBuilder();

        private CellPosition cellPos;

        @Override
        public void beginNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
            if (tagName.equals("comment")) {
                cellPos = CellPosition.fromABString(getAttr(attrs, "ref"));
                buf.setLength(0);
            }
        }

        @Override
        public void text(SourceLocation loc, String value) {
            buf.append(value);
        }

        @Override
        public void endNode(String tagName) {
            if (tagName.endsWith("comment")) {
                Comment comment = new Comment();
                comment.setComment(buf.toString());
                addComment(cellPos, comment);
            }
        }
    }

    public Comment getComment(CellPosition pos) {
        Comment comment = comments.get(pos);
        return comment;
    }

    public String getCommentText(CellPosition pos) {
        Comment comment = getComment(pos);
        return comment == null ? null : comment.getComment();
    }

    public void addComment(CellPosition pos, Comment comment) {
        this.comments.put(pos, comment);
    }
}
