package io.nop.markdown.simple;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.nop.core.CoreErrors.ERR_COMPONENT_NOT_ALLOW_CHANGE;

@DataBean
public class MarkdownBlock {
    private int startIndex;
    private int endIndex;

    private int level;
    private String title;

    private String text;

    private List<MarkdownBlock> children;

    private Map<String, String> meta;

    private MarkdownBlock tpl;

    private boolean frozen;

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

    public boolean isFrozen() {
        return frozen;
    }

    public void freeze() {
        frozen = true;

        if (this.children != null) {
            for (MarkdownBlock child : children) {
                child.freeze();
            }
        }
    }

    protected void checkAllowChange() {
        if (frozen)
            throw new NopException(ERR_COMPONENT_NOT_ALLOW_CHANGE);
    }

    public MarkdownBlock cloneInstance() {
        MarkdownBlock ret = new MarkdownBlock();
        ret.setStartIndex(startIndex);
        ret.setEndIndex(endIndex);
        ret.setLevel(level);
        ret.setTitle(title);
        ret.setText(text);
        if (children != null) {
            ret.setChildren(children.stream().map(MarkdownBlock::cloneInstance).collect(Collectors.toList()));
        }
        if (meta != null) {
            ret.setMeta(new LinkedHashMap<>(meta));
        }
        ret.setTpl(tpl);
        return ret;
    }

    public boolean containsText(String text) {
        if (this.text == null)
            return false;
        return this.text.contains(text);
    }

    public boolean hasChild() {
        return children != null && !children.isEmpty();
    }

    public boolean tplContainsText(String text) {
        if (tpl == null)
            return false;
        return tpl.containsText(text);
    }

    public String getFullTitle() {
        if (meta == null)
            return title;

        StringBuilder sb = new StringBuilder();
        sb.append(title);
        sb.append('(');
        boolean first = true;
        for (Map.Entry<String, String> entry : meta.entrySet()) {
            if (first) {
                first = false;
            } else {
                sb.append(',');
            }
            sb.append(entry.getKey());
            sb.append(':');
            sb.append(entry.getValue());
        }
        sb.append(')');
        return sb.toString();
    }

    public String getPrefix() {
        if (title == null)
            return null;
        int pos = title.indexOf(' ');
        if (pos < 0)
            return null;
        return title.substring(0, pos).trim();
    }

    public String getParentPrefix() {
        String prefix = getPrefix();
        if (prefix == null)
            return null;

        int end = prefix.length();
        if (prefix.charAt(end - 1) == '.') {
            end--;
        }
        int pos = prefix.lastIndexOf('.', end);
        if (pos < 0)
            return null;
        return prefix.substring(0, pos + 1);
    }

    public MarkdownBlock findByTitle(String title) {
        if (title.equals(this.title))
            return this;

        if (this.children != null) {
            for (MarkdownBlock child : this.children) {
                MarkdownBlock found = child.findByTitle(title);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    public MarkdownBlock findChildByTitle(String title) {
        if (this.children != null) {
            for (MarkdownBlock child : this.children) {
                if (title.equals(child.title))
                    return child;
            }
        }
        return null;
    }

    public MarkdownBlock findChildByMark(String mark) {
        if (this.children != null) {
            for (MarkdownBlock child : this.children) {
                if (child.containsText(mark))
                    return child;
            }
        }
        return null;
    }

    public boolean removeBlock(Predicate<MarkdownBlock> filter, boolean multiple) {
        checkAllowChange();
        if (filter.test(this))
            return true;

        boolean b = false;
        if (this.children != null) {
            for (int i = 0, n = this.children.size(); i < n; i++) {
                MarkdownBlock block = this.children.get(i);
                if (block.removeBlock(filter, multiple)) {
                    this.children.remove(i);
                    i--;
                    n--;
                    if (!multiple)
                        return true;
                    b = true;
                }
            }

            if (children.isEmpty()) {
                if (filter.test(this))
                    return true;
            }
        }

        return b;
    }

    public Set<String> getAllTitles() {
        Set<String> titles = new LinkedHashSet<>();
        collectTitles(titles);
        return titles;
    }

    public void collectTitles(Set<String> titles) {
        if (this.title != null)
            titles.add(this.title);

        if (this.children != null) {
            for (MarkdownBlock child : this.children) {
                child.collectTitles(titles);
            }
        }
    }

    public void addMetaValue(String name, String value) {
        checkAllowChange();
        if (meta == null)
            meta = new LinkedHashMap<>();
        meta.put(name, value);
    }

    public String getMetaValue(String name) {
        return meta == null ? null : meta.get(name);
    }

    public int getMetaInt(String name, int defaultValue) {
        Object v = getMetaValue(name);
        return v == null ? defaultValue : ConvertHelper.toInt(v);
    }

    public double getMetaDouble(String name, double defaultValue) {
        Object v = getMetaValue(name);
        return v == null ? defaultValue : ConvertHelper.toDouble(v);
    }

    public Map<String, String> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, String> meta) {
        checkAllowChange();
        this.meta = meta;
    }

    public void forEachBlock(Consumer<MarkdownBlock> action) {
        action.accept(this);

        if (this.children != null) {
            for (MarkdownBlock child : children) {
                child.forEachBlock(action);
            }
        }
    }

    public String toString() {
        return "#".repeat(Math.max(0, level)) + ' ' + title;
    }

    public void buildText(StringBuilder sb) {
        if (getLevel() > 0) {
            sb.append("#".repeat(getLevel())).append(" ");
            if (title != null)
                sb.append(getFullTitle()).append("\n");
        }

        if (getText() != null)
            sb.append(getText());
        sb.append("\n");

        if (children != null) {
            for (MarkdownBlock child : children) {
                child.buildText(sb);
            }
        }
    }

    public void addChild(MarkdownBlock child) {
        checkAllowChange();
        if (children == null)
            children = new ArrayList<>();
        children.add(child);
    }

    public void resetLevel(int level) {
        checkAllowChange();

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
        checkAllowChange();
        this.level = level;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        checkAllowChange();
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        checkAllowChange();
        this.text = text;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        checkAllowChange();
        this.startIndex = startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setEndIndex(int endIndex) {
        checkAllowChange();
        this.endIndex = endIndex;
    }

    public int getChildCount() {
        return children == null ? 0 : children.size();
    }

    public MarkdownBlock getChild(int index) {
        return children == null ? null : children.get(index);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<MarkdownBlock> getChildren() {
        return children;
    }

    public void setChildren(List<MarkdownBlock> children) {
        checkAllowChange();
        this.children = children;
    }

    @JsonIgnore
    public MarkdownBlock getTpl() {
        return tpl;
    }

    public void setTpl(MarkdownBlock tpl) {
        checkAllowChange();
        this.tpl = tpl;
    }
}