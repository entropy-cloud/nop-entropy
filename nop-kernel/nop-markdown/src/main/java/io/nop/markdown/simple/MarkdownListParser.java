package io.nop.markdown.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.markdown.model.MarkdownListItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

/**
 * Markdown列表解析器 (修正版, 输出内容不含Tab)
 * 支持有序列表和无序列表
 * 可通过 supportNested 属性控制是否支持嵌套列表
 */
public class MarkdownListParser {
    // Tab的制表位宽度
    private static final int TAB_STOP_WIDTH = 4;

    private boolean supportNested = false;

    public MarkdownListParser() {
    }

    public MarkdownListParser(boolean supportNested) {
        this.supportNested = supportNested;
    }

    public boolean isSupportNested() {
        return supportNested;
    }

    public void setSupportNested(boolean supportNested) {
        this.supportNested = supportNested;
    }

    /**
     * 解析所有列表项
     * @param loc 源码位置信息，可为null
     * @param text 要解析的文本。假设是连续的列表块。
     * @return 列表项集合。如果 supportNested=true，返回顶层项，子项通过 children 访问；否则返回扁平列表
     */
    public List<MarkdownListItem> parseAllListItems(SourceLocation loc, String text) {
        List<MarkdownListItem> allItems = new ArrayList<>();
        int pos = 0;
        
        while (pos < text.length()) {
            MarkdownListItem item = parseNextListItem(loc, text, pos);
            if (item == null) {
                break;
            }
            allItems.add(item);
            pos = item.getEndPos();
        }
        
        if (supportNested && !allItems.isEmpty()) {
            return buildTreeAndSetMetadata(allItems);
        }
        
        if (!allItems.isEmpty()) {
            int currentNumber = 1;
            for (MarkdownListItem item : allItems) {
                item.setListLevel(0);
                if (item.isOrdered()) {
                    item.setItemIndex(currentNumber++);
                }
            }
        }
        
        return allItems;
    }
    
    // ... 其他公共方法 (parseAllListItemsImmutable, forEachListItem) 保持不变 ...
    
    public List<MarkdownListItem> parseAllListItemsImmutable(SourceLocation loc, String text) {
        List<MarkdownListItem> items = parseAllListItems(loc, text);
        for (MarkdownListItem item : items) {
            item.freeze();
        }
        return items;
    }
    
    public void forEachListItem(SourceLocation loc, String text, Consumer<MarkdownListItem> consumer) {
        List<MarkdownListItem> items = parseAllListItems(loc, text);
        if (supportNested) {
            for (MarkdownListItem item : items) {
                traverseItem(item, consumer);
            }
        } else {
            items.forEach(consumer);
        }
    }
    
    private void traverseItem(MarkdownListItem item, Consumer<MarkdownListItem> consumer) {
        consumer.accept(item);
        if (item.hasChildren()) {
            for (MarkdownListItem child : item.getChildren()) {
                traverseItem(child, consumer);
            }
        }
    }


    /**
     * 解析Markdown文本中的下一个列表项
     */
    public MarkdownListItem parseNextListItem(SourceLocation loc, String text, int start) {
        if (text == null || start < 0 || start > text.length()) {
            return null;
        }

        int pos = skipEmptyLines(text, start);
        if (pos >= text.length()) return null;

        int listStart = findListItemStart(text, pos);
        if (listStart < 0) return null;
        
        int lineStart = text.lastIndexOf('\n', listStart) + 1;
        int indent = calculateColumnWidth(text, lineStart, listStart);

        ListMarker marker = parseListMarker(text, listStart);
        if (marker == null) return null;

        int requiredContentIndent = indent + marker.markerWidth;
        
        int contentEnd = supportNested ? 
                findListItemEnd(text, marker.contentStart, indent, requiredContentIndent) :
                findListItemEndSimple(text, marker.contentStart);

        String content = supportNested ?
                extractContent(text, marker.contentStart, contentEnd, requiredContentIndent) :
                expandTabs(text.substring(marker.contentStart, contentEnd).trim());

        MarkdownListItem item = new MarkdownListItem();
        if (loc != null) {
            item.setLocation(loc.offset(listStart, 0));
        }
        item.setStartPos(listStart);
        item.setEndPos(contentEnd);
        item.setOrdered(marker.ordered);
        item.setContent(content);
        item.setRawIndent(indent); 

        return item;
    }
    
    // ... buildTreeAndSetMetadata 方法保持不变 ...
    List<MarkdownListItem> buildTreeAndSetMetadata(List<MarkdownListItem> flatItems) {
        if (flatItems.isEmpty()) {
            return flatItems;
        }

        List<MarkdownListItem> rootItems = new ArrayList<>();
        Stack<MarkdownListItem> stack = new Stack<>();

        for (MarkdownListItem item : flatItems) {
            int currentIndent = item.getRawIndent();

            while (!stack.isEmpty() && stack.peek().getRawIndent() >= currentIndent) {
                stack.pop();
            }

            if (stack.isEmpty()) {
                item.setListLevel(0);
                rootItems.add(item);
                if (item.isOrdered()) {
                    long orderCount = rootItems.stream().filter(MarkdownListItem::isOrdered).count();
                    item.setItemIndex((int)orderCount);
                }
            } else {
                MarkdownListItem parent = stack.peek();
                item.setListLevel(parent.getListLevel() + 1);
                parent.addChild(item);
                if (item.isOrdered()) {
                    long orderCount = parent.getChildren().stream().filter(MarkdownListItem::isOrdered).count();
                    item.setItemIndex((int)orderCount);
                }
            }
            stack.push(item);
        }

        return rootItems;
    }

    // ========== 辅助方法 ==========

    /**
     * [新增] 将字符串中的Tab转换为空格
     */
    private static String expandTabs(String line) {
        if (line == null || !line.contains("\t")) {
            return line;
        }
        StringBuilder sb = new StringBuilder();
        int column = 0;
        for (char c : line.toCharArray()) {
            if (c == '\t') {
                int spacesToAdd = TAB_STOP_WIDTH - (column % TAB_STOP_WIDTH);
                for (int i = 0; i < spacesToAdd; i++) {
                    sb.append(' ');
                }
                column += spacesToAdd;
            } else {
                sb.append(c);
                column++;
            }
        }
        return sb.toString();
    }

    /**
     * [修改] 提取内容，并确保输出内容不含Tab
     */
    String extractContent(String text, int start, int end, int requiredIndentWidth) {
        if (start >= end) return "";

        StringBuilder result = new StringBuilder();
        int currentPos = start;

        // 处理第一行
        int firstLineEnd = text.indexOf('\n', currentPos);
        if (firstLineEnd == -1 || firstLineEnd >= end) {
            // 单行内容
            String singleLineContent = text.substring(currentPos, end);
            return expandTabs(singleLineContent).trim();
        }
        // 多行内容的第一行
        String firstContentLine = text.substring(currentPos, firstLineEnd);
        result.append(expandTabs(firstContentLine));
        currentPos = firstLineEnd + 1;

        // 处理后续行
        while (currentPos < end) {
            result.append('\n');
            int lineStart = currentPos;
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd == -1 || lineEnd >= end) {
                lineEnd = end;
            }
            
            String rawContentLine = text.substring(lineStart, lineEnd);
            String indentedContent = removeIndent(rawContentLine, requiredIndentWidth);
            result.append(expandTabs(indentedContent));
            
            currentPos = lineEnd + 1;
        }

        return result.toString().trim();
    }
    
    // ... 其他辅助方法 (calculateColumnWidth, removeIndent, findListItemEnd, etc.) 保持不变 ...

    int calculateColumnWidth(String text, int start, int end) {
        int width = 0;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '\t') {
                width += TAB_STOP_WIDTH - (width % TAB_STOP_WIDTH);
            } else {
                width++;
            }
        }
        return width;
    }

    String removeIndent(String line, int indentWidthToRemove) {
        int width = 0;
        int pos = 0;
        while (pos < line.length() && width < indentWidthToRemove) {
            char c = line.charAt(pos);
            if (c == ' ') {
                width++;
            } else if (c == '\t') {
                width += TAB_STOP_WIDTH - (width % TAB_STOP_WIDTH);
            } else {
                break;
            }
            pos++;
        }
        return line.substring(pos);
    }

    int findListItemEnd(String text, int pos, int baseIndent, int requiredContentIndent) {
        int lineStart = pos;
        while (lineStart < text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd == -1) {
                return text.length();
            }

            int nextLineStart = lineEnd + 1;

            int checkPos = skipWhitespace(text, nextLineStart);
            if (checkPos >= text.length() || text.charAt(checkPos) == '\n' || text.charAt(checkPos) == '\r') {
                return nextLineStart;
            }

            int nextIndent = calculateColumnWidth(text, nextLineStart, checkPos);

            // 遇到任何列表项标记都结束当前项
            if (isListItemStart(text, checkPos)) {
                return nextLineStart;
            }

            // 非列表项的行：缩进不足则结束
            if (nextIndent < requiredContentIndent) {
                return nextLineStart;
            }

            lineStart = nextLineStart;
        }
        return text.length();
    }

    int findListItemEndSimple(String text, int pos) {
        int lineStart = pos;
        while (lineStart < text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd == -1) {
                return text.length();
            }
            int nextLineStart = lineEnd + 1;
            int checkPos = skipWhitespace(text, nextLineStart);
            if (checkPos >= text.length() || text.charAt(checkPos) == '\n' || text.charAt(checkPos) == '\r') {
                return nextLineStart;
            }
            if (isListItemStart(text, checkPos)) {
                return nextLineStart;
            }
            lineStart = nextLineStart;
        }
        return text.length();
    }
    
    int skipWhitespace(String text, int pos) {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t') break;
            pos++;
        }
        return pos;
    }

    int skipEmptyLines(String text, int pos) {
        while (pos < text.length()) {
            int nonWs = skipWhitespace(text, pos);
            if (nonWs < text.length() && text.charAt(nonWs) == '\n') {
                pos = nonWs + 1;
            } else if (nonWs == text.length()) {
                return nonWs;
            } else {
                break;
            }
        }
        return pos;
    }
    
    int findListItemStart(String text, int pos) {
        int current = pos;
        while (current < text.length()) {
            int lineStart = text.lastIndexOf('\n', current - 1) + 1;
            if (current < lineStart) current = lineStart;
            int checkPos = skipWhitespace(text, current);
            if (isListItemStart(text, checkPos)) {
                return checkPos;
            }
            int nextLine = text.indexOf('\n', current);
            if (nextLine == -1) return -1;
            current = nextLine + 1;
        }
        return -1;
    }

    boolean isListItemStart(String text, int pos) {
        if (pos >= text.length()) return false;
        char c = text.charAt(pos);
        if (c == '*' || c == '-' || c == '+') {
            return pos + 1 < text.length() && (text.charAt(pos + 1) == ' ' || text.charAt(pos + 1) == '\t');
        }
        if (Character.isDigit(c)) {
            int p = pos;
            while (p < text.length() && Character.isDigit(text.charAt(p))) {
                p++;
            }
            if (p > pos && p < text.length() && text.charAt(p) == '.') {
                 return p + 1 < text.length() && (text.charAt(p + 1) == ' ' || text.charAt(p + 1) == '\t');
            }
        }
        return false;
    }
    
    ListMarker parseListMarker(String text, int pos) {
        if (!isListItemStart(text, pos)) return null;
        ListMarker marker = new ListMarker();
        char c = text.charAt(pos);
        if (c == '*' || c == '-' || c == '+') {
            marker.ordered = false;
            int markerEnd = skipWhitespace(text, pos + 1);
            marker.contentStart = markerEnd;
            marker.markerWidth = calculateColumnWidth(text, pos, markerEnd);
            return marker;
        }
        if (Character.isDigit(c)) {
            int p = pos;
            while (p < text.length() && Character.isDigit(text.charAt(p))) {
                p++;
            }
            int markerEnd = skipWhitespace(text, p + 1);
            marker.ordered = true;
            marker.contentStart = markerEnd;
            marker.markerWidth = calculateColumnWidth(text, pos, markerEnd);
            return marker;
        }
        return null;
    }
    
    static class ListMarker {
        boolean ordered;
        int contentStart;
        int markerWidth;
    }
}