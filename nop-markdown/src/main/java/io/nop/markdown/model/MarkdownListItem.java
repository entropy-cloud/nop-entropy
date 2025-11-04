package io.nop.markdown.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MarkdownListItem extends MarkdownNode {
    private boolean ordered;
    private String content;

    // 元数据
    private int rawIndent;      // 原始缩进宽度（视觉列数）
    private int listLevel;      // 列表层级（0表示顶层）
    private int itemIndex;      // 在当前层级中的序号（从1开始）

    // 子节点
    private List<MarkdownListItem> children;

    // ========== 子节点管理 ==========

    public void addChild(MarkdownListItem child) {
        checkAllowChange();
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

    public void removeChild(MarkdownListItem child) {
        checkAllowChange();
        if (children != null) {
            children.remove(child);
        }
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public List<MarkdownListItem> getChildren() {
        if (children == null) {
            return Collections.emptyList();
        }
        return frozen ? Collections.unmodifiableList(children) : children;
    }

    public void setChildren(List<MarkdownListItem> children) {
        checkAllowChange();
        this.children = children;
    }

    public int getChildCount() {
        return children == null ? 0 : children.size();
    }

    public MarkdownListItem getChild(int index) {
        if (children == null || index < 0 || index >= children.size()) {
            return null;
        }
        return children.get(index);
    }

    // ========== 冻结支持 ==========

    @Override
    public void freeze() {
        if (frozen) return;
        super.freeze();
        if (children != null) {
            for (MarkdownListItem child : children) {
                child.freeze();
            }
        }
    }

    // ========== 文本构建 ==========

    @Override
    protected void buildText(StringBuilder sb, MarkdownTextOptions options) {
        buildText(sb, options, 0);
    }

    protected void buildText(StringBuilder sb, MarkdownTextOptions options, int level) {
        // 添加缩进
        if (level > 0) {
            sb.append("  ".repeat(level));
        }

        // 添加列表标记
        if (ordered) {
            sb.append(itemIndex).append(". ");
        } else {
            sb.append("- ");
        }

        // 添加内容（保持多行格式）
        if (content != null) {
            String[] lines = content.split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    sb.append('\n');
                    if (level > 0) {
                        sb.append("  ".repeat(level));
                    }
                    sb.append("  "); // 内容缩进
                }
                sb.append(lines[i]);
            }
        }

        // 添加子项
        if (hasChildren()) {
            for (MarkdownListItem child : children) {
                sb.append('\n');
                child.buildText(sb, options, level + 1);
            }
        }
    }

    // ========== 实用方法 ==========

    public List<MarkdownListItem> getAllDescendants() {
        List<MarkdownListItem> result = new ArrayList<>();
        collectDescendants(result);
        return result;
    }

    private void collectDescendants(List<MarkdownListItem> result) {
        if (hasChildren()) {
            for (MarkdownListItem child : children) {
                result.add(child);
                child.collectDescendants(result);
            }
        }
    }

    // ========== Getter/Setter ==========

    public boolean isOrdered() {
        return ordered;
    }

    public void setOrdered(boolean ordered) {
        checkAllowChange();
        this.ordered = ordered;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        checkAllowChange();
        this.content = content;
    }

    public int getRawIndent() {
        return rawIndent;
    }

    public void setRawIndent(int rawIndent) {
        checkAllowChange();
        this.rawIndent = rawIndent;
    }

    public int getListLevel() {
        return listLevel;
    }

    public void setListLevel(int listLevel) {
        checkAllowChange();
        this.listLevel = listLevel;
    }

    public int getItemIndex() {
        return itemIndex;
    }

    public void setItemIndex(int itemIndex) {
        checkAllowChange();
        this.itemIndex = itemIndex;
    }

    @Override
    public String toString() {
        return "MarkdownListItem{" +
                "ordered=" + ordered +
                ", content='" + content + '\'' +
                ", rawIndent=" + rawIndent +
                ", listLevel=" + listLevel +
                ", itemIndex=" + itemIndex +
                ", childCount=" + getChildCount() +
                ", pos=[" + getStartPos() + "-" + getEndPos() + "]" +
                '}';
    }
}