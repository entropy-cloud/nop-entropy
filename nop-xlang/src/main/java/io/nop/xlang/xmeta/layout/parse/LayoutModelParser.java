/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.layout.parse;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.ICell;
import io.nop.xlang.xmeta.layout.LayoutCellModel;
import io.nop.xlang.xmeta.layout.LayoutGroupModel;
import io.nop.xlang.xmeta.layout.LayoutModel;
import io.nop.xlang.xmeta.layout.LayoutTableModel;

import java.util.ArrayList;
import java.util.List;

import static io.nop.xlang.XLangErrors.ERR_LAYOUT_INVALID_LINE;
import static io.nop.xlang.XLangErrors.ERR_LAYOUT_UNSUPPORTED_LAYER_NUMBER;

public class LayoutModelParser {

    private GroupLine nextGroup;
    private int groupIndex;

    public LayoutModel parseFromText(SourceLocation loc, String text) {
        TextScanner sc = TextScanner.fromString(loc, text);
        LayoutModel ret = new LayoutModel();
        ret.setLocation(loc);
        List<LayoutTableModel> groups = parseGroups(sc, 0);
        ret.setGroups(groups);
        return ret;
    }

    private List<LayoutTableModel> parseGroups(TextScanner sc, int normalizeLevel) {
        List<LayoutTableModel> groups = new ArrayList<>();

        int line = sc.line;
        do {
            sc.skipBlank();
            LayoutTableModel group = parseGroup(sc, normalizeLevel);
            groups.add(group);
            Guard.checkState(line != sc.line);
            line = sc.line;
        } while (!sc.isEnd());

        return groups;
    }

    private LayoutTableModel parseGroup(TextScanner sc, int normalizedLevel) {
        LayoutTableModel group = new LayoutTableModel();
        group.setLocation(sc.location());
        GroupLine line = consumeGroupLine(sc);
        if (line != null) {
            group.setFoldable(line.foldable);
            group.setFolded(line.folded);
            group.setId(line.id);
            group.setLabel(line.label);
            group.setLevel(line.level);
        }

        if (group.getId() == null) {
            group.setId("group-" + groupIndex++);
            group.setAutoId(true);
        }

        parseRows(sc, group, normalizedLevel);
        group.setLevel(normalizedLevel);

        sc.skipBlank();
        return group;
    }

    private GroupLine consumeGroupLine(TextScanner sc) {
        if (this.nextGroup != null) {
            GroupLine ret = this.nextGroup;
            this.nextGroup = null;
            return ret;
        }

        if (sc.cur == '~' || sc.cur == '=') {
            return parseGroupLine(sc);
        }
        return null;
    }

    // ~~~=====>###id[label](rowSpan,colSpan)==========~~~
    private GroupLine parseGroupLine(TextScanner sc) {
        boolean folded = false;
        boolean foldable = false;
        boolean headContinue = false;
        boolean tailContinue = false;
        int mergeAcross = 0;
        int mergeDown = 0;

        if (sc.cur == '=') {
            sc.nextUntil(s -> s.cur != '=', null);
            sc.skipBlankInLine();
            if (sc.cur == '\r' || sc.cur == '\n') {
                sc.skipBlank();
                GroupLine line = new GroupLine();
                line.endOfGroup = true;
                return line;
            }
        } else if (sc.cur == '~') {
            sc.nextUntil(s -> s.cur != '~', null);
            sc.consume('=');
            sc.nextUntil(s -> s.cur != '=', null);
            sc.skipBlankInLine();
        }

        // 兼容此前的写法。^不需要进行XML转义，使用更方便一些
        if (sc.cur == '^' || sc.cur == '<') {
            sc.next();
            foldable = true;
            folded = true;
            sc.skipBlankInLine();
        } else if (sc.cur == '>') {
            sc.next();
            foldable = true;
            folded = false;
            sc.skipBlankInLine();
        }

        int level = 0;
        while (sc.cur == '#') {
            if (level == 5)
                throw sc.newError(ERR_LAYOUT_UNSUPPORTED_LAYER_NUMBER);
            sc.next();
            level++;
        }

        sc.skipBlankInLine();
        String id = null;
        String label = null;
        if (StringHelper.isJavaIdentifierStart(sc.cur)) {
            MutableString buf = sc.getReusableBuffer();
            sc.nextUntil(s-> s.cur == '[' || s.cur == '=' || s.cur == '(', sc::appendToBuf);
            id = buf.trim().toString();
            sc.skipBlankInLine();

            if(id.indexOf(' ') >= 0){
                label = id;
                id = null;
            }
        }

        if (sc.cur == '[') {
            sc.next();
            label = StringHelper.unescapeJava(sc.nextUntil(']', false).trim().toString());
            sc.consumeInline(']');
        }

        if (sc.cur == '(') {
            sc.consumeInline('(');
            mergeAcross = sc.nextInt() - 1;
            sc.skipBlankInLine();
            if (sc.cur != ')') {
                mergeDown = mergeAcross;
                sc.consumeInline(',');
                mergeAcross = sc.nextInt();
                sc.skipBlankInLine();
            }
            sc.consumeInline(')');
        }

        sc.consume('=');
        sc.nextUntil(s -> s.cur != '=', null);
        if (sc.cur == '~') {
            tailContinue = true;
            sc.nextUntil(s -> s.cur != '~', null);
        }

        consumeEndOfLine(sc);

        GroupLine line = new GroupLine();
        line.level = level;
        line.headContinue = headContinue;
        line.tailContinue = tailContinue;
        line.label = label;
        line.id = id;
        line.foldable = foldable;
        line.folded = folded;
        if (mergeAcross > 0)
            line.mergeAcross = mergeAcross;
        if (mergeDown > 0)
            line.mergeDown = mergeDown;
        return line;
    }

    private void parseRows(TextScanner sc, LayoutTableModel group, int normalizedLevel) {
        int rowIndex = 0;
        while (!isGroupEnd(sc, group)) {
            parseRow(sc, group, rowIndex, normalizedLevel);
            sc.skipBlank();
            rowIndex++;
        }
    }

    private boolean isGroupEnd(TextScanner sc, LayoutTableModel group) {
        if (sc.isEnd())
            return true;
        peekGroupLine(sc);
        if (this.nextGroup != null) {
            if (this.nextGroup.endOfGroup) {
                this.nextGroup = null;
                return true;
            }
            if (this.nextGroup.level <= group.getLevel())
                return true;
        }
        return false;
    }

    private void parseRow(TextScanner sc, LayoutTableModel table, int rowIndex, int normalizedLevel) {
        // divider row
        if (sc.cur == '-') {
            sc.nextUntil(s -> sc.cur != '-', null);
            consumeEndOfLine(sc);
            table.makeRow(rowIndex);
            return;
        }

        do {
            GroupLine groupLine = peekGroupLine(sc);
            if (groupLine != null) {
                if (groupLine.endOfGroup)
                    return;
                if (groupLine.level <= table.getLevel())
                    return;
                LayoutTableModel cellTable = parseGroup(sc, normalizedLevel + 1);
                LayoutGroupModel group = new LayoutGroupModel();
                cellTable.setLevel(normalizedLevel + 1);
                group.setLocation(cellTable.getLocation());
                group.setTable(cellTable);
                table.addToRow(rowIndex, group);
                if (!groupLine.tailContinue)
                    return;
            } else {
                int line = sc.line;
                do {
                    ICell cell = parseSimpleCell(sc);
                    table.addToRow(rowIndex, cell);
                    sc.skipBlank();
                } while (sc.line == line && !sc.isEnd());
                break;
            }
        } while (true);
    }

    private void consumeEndOfLine(TextScanner sc) {
        sc.skipBlankInLine();
        if (!sc.isEnd()) {
            if (sc.cur != '\r' && sc.cur != '\n') {
                throw sc.newError(ERR_LAYOUT_INVALID_LINE);
            }
            sc.skipBlank();
        }
    }

    private GroupLine peekGroupLine(TextScanner sc) {
        if (this.nextGroup != null) {
            return this.nextGroup;
        }

        if (sc.cur == '~' || sc.cur == '=') {
            this.nextGroup = parseGroupLine(sc);
            return this.nextGroup;
        }
        return null;
    }

    LayoutCellModel parseSimpleCell(TextScanner sc) {
        LayoutCellModel cell = new LayoutCellModel();
        cell.setLocation(sc.location());

        do {
            if (sc.tryConsume('*')) {
                cell.setMandatory(true);
            } else if (sc.tryConsume('@')) {
                cell.setReadonly(true);
            } else if (sc.tryConsume('!')) {
                cell.setHideLabel(true);
            } else {
                break;
            }
        } while (true);

        String id = sc.nextXmlName();
        if (sc.tryConsume('[')) {
            String label = sc.nextUntil(']', false).trim().toString();
            label = StringHelper.unescapeJava(label);
            sc.consume(']');
            cell.setLabel(label);
        }
        cell.setId(id);

        if (sc.tryConsume('(')) {
            int colSpan = sc.nextInt();
            sc.skipBlankInLine();
            int rowSpan = 0;
            if (sc.cur != ')') {
                sc.consumeInline(',');
                rowSpan = colSpan;
                colSpan = sc.nextInt();
                sc.skipBlankInLine();
            }
            sc.consumeInline(')');
            cell.setMergeAcross(colSpan - 1);
            cell.setMergeDown(rowSpan - 1);
        }

        return cell;
    }

    static class GroupLine {
        int level;
        boolean headContinue;
        boolean tailContinue;
        String id;
        String label;
        boolean folded;
        boolean foldable;
        int mergeDown;
        int mergeAcross;
        boolean endOfGroup;
    }
}