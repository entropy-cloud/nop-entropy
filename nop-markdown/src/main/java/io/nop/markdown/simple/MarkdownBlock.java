package io.nop.markdown.simple;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.List;

@DataBean
public class MarkdownBlock {
    private int startIndex;
    private int endIndex;

    private int level;
    private String title;

    private String text;

    private List<MarkdownBlock> children;

    public MarkdownBlock() {
    }

    public MarkdownBlock(int level, String title) {
        this.setLevel(level);
        this.setTitle(title);
    }

    public static List<MarkdownBlock> buildTree(List<MarkdownBlock> blocks) {
        if (blocks.size() <= 1)
            return new ArrayList<>(blocks);

        MarkdownBlock root = new MarkdownBlock();
        int index = 0;
        do {
            MarkdownBlock block = blocks.get(index);
            root.addChild(block);
            index++;
            index = _buildTree(blocks, index, block);
        } while (index < blocks.size());

        return root.getChildren();
    }

    private static int _buildTree(List<MarkdownBlock> blocks, int index, MarkdownBlock parent) {
        while (index < blocks.size()) {
            MarkdownBlock current = blocks.get(index);
            if (current.getLevel() > parent.getLevel()) {
                // 当前块是父块的子节点
                parent.addChild(current);
                // 处理当前块的潜在子节点
                index = _buildTree(blocks, index + 1, current);
            } else {
                // 当前块不是子节点，返回处理上一层级
                return index;
            }
        }
        return index;
    }

    public String toString() {
        return "#".repeat(Math.max(0, level)) + ' ' + title;
    }

    public void addChild(MarkdownBlock child) {
        if (children == null)
            children = new ArrayList<>();
        children.add(child);
    }

    public void resetLevel(int level) {
        this.level = level;
        if (children != null) {
            for (MarkdownBlock child : children) {
                child.resetLevel(level + 1);
            }
        }
    }

    public List<MarkdownBlock> flatten() {
        List<MarkdownBlock> ret = new ArrayList<>();
        _flatten(ret);
        return ret;
    }

    private void _flatten(List<MarkdownBlock> ret) {
        ret.add(this);
        if (children != null) {
            for (MarkdownBlock child : children) {
                child._flatten(ret);
            }
        }
    }

    public boolean hasContent() {
        return !StringHelper.isBlank(text);
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<MarkdownBlock> getChildren() {
        return children;
    }

    public void setChildren(List<MarkdownBlock> children) {
        this.children = children;
    }
}