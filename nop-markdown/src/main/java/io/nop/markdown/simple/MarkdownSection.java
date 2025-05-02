package io.nop.markdown.simple;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.lang.ITagSetSupport;
import io.nop.commons.util.StringHelper;
import io.nop.markdown.MarkdownConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.nop.core.CoreErrors.ERR_COMPONENT_NOT_ALLOW_CHANGE;

@DataBean
public class MarkdownSection implements ITagSetSupport {
    private int level;
    private String title;

    private String text;

    private List<MarkdownSection> children;

    private Map<String, String> meta;

    private Set<String> tagSet;

    private MarkdownSection tpl;

    private boolean frozen;

    public MarkdownSection() {
    }

    public MarkdownSection(int level, String title) {
        this.setLevel(level);
        this.setTitle(title);
    }

    public static List<MarkdownSection> buildTree(List<MarkdownSection> sections) {
        if (sections.size() <= 1)
            return new ArrayList<>(sections);

        MarkdownSection root = new MarkdownSection();
        int index = 0;
        do {
            MarkdownSection section = sections.get(index);
            root.addChild(section);
            index++;
            index = _buildTree(sections, index, section);
        } while (index < sections.size());

        return root.getChildren();
    }

    private static int _buildTree(List<MarkdownSection> sections, int index, MarkdownSection parent) {
        while (index < sections.size()) {
            MarkdownSection current = sections.get(index);
            if (current.getLevel() > parent.getLevel()) {
                // 当前块是父块的子节点
                parent.addChild(current);
                // 处理当前块的潜在子节点
                index = _buildTree(sections, index + 1, current);
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
            for (MarkdownSection child : children) {
                child.freeze();
            }
        }
    }

    protected void checkAllowChange() {
        if (frozen)
            throw new NopException(ERR_COMPONENT_NOT_ALLOW_CHANGE);
    }

    @Override
    public Set<String> getTagSet() {
        return tagSet;
    }

    public void setTagSet(Set<String> tagSet) {
        this.tagSet = tagSet;
    }

    public MarkdownSection cloneInstance() {
        MarkdownSection ret = new MarkdownSection();
        ret.setLevel(level);
        ret.setTitle(title);
        ret.setText(text);
        if (children != null) {
            ret.setChildren(children.stream().map(MarkdownSection::cloneInstance).collect(Collectors.toList()));
        }
        if (meta != null) {
            ret.setMeta(new LinkedHashMap<>(meta));
        }
        ret.setTpl(tpl);
        return ret;
    }

    public String parseBlock(String blockBegin, String blockEnd) {
        return parseBlock(blockBegin, blockEnd, true, true);
    }

    public String parseBlock(String blockBegin, String blockEnd, boolean includeBegin, boolean includeEnd) {
        if (text == null)
            return null;

        int pos = text.indexOf(blockBegin);
        if (pos < 0)
            return null;

        int pos2 = text.indexOf(blockEnd, pos + blockBegin.length());
        if (pos2 < 0)
            return null;

        if (!includeBegin)
            pos += blockBegin.length();

        if (includeEnd)
            pos2 += blockEnd.length();

        return text.substring(pos, pos2).trim();
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

    public MarkdownSection find(Predicate<MarkdownSection> filter) {
        if (filter.test(this))
            return this;
        if (this.children != null) {
            for (MarkdownSection child : this.children) {
                MarkdownSection found = child.find(filter);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    public MarkdownSection findChild(Predicate<MarkdownSection> filter) {
        if (this.children != null) {
            for (MarkdownSection child : this.children) {
                if (filter.test(child))
                    return child;
            }
        }
        return null;
    }

    public MarkdownSection findByTitle(String title) {
        return find(section -> title.equals(section.getTitle()));
    }

    public MarkdownSection findByTag(String tag) {
        return find(section -> section.containsTag(tag));
    }

    public MarkdownSection findChildByTitle(String title) {
        return findChild(section -> title.equals(section.getTitle()));
    }

    public MarkdownSection findChildByMark(String mark) {
        return findChild(section -> section.containsText(mark));
    }

    public MarkdownSection findChildByTag(String tag) {
        return findChild(section -> section.containsTag(tag));
    }

    public MarkdownSection filterSection(Function<MarkdownSection, Boolean> filter) {
        Boolean b = filter.apply(this);
        if (Boolean.TRUE.equals(b))
            return this;
        if (Boolean.FALSE.equals(b))
            return null;

        if (!hasChild())
            return null;

        MarkdownSection ret = null;
        for (int i = 0, n = children.size(); i < n; i++) {
            MarkdownSection child = children.get(i);
            MarkdownSection newChild = child.filterSection(filter);
            if (newChild != child) {
                if (ret == null) {
                    ret = new MarkdownSection();
                    for (int j = 0; j < i; j++) {
                        ret.addChild(children.get(j));
                    }
                } else {
                    if (newChild != null)
                        ret.addChild(newChild);
                }
            }
        }

        return ret == null ? this : ret;
    }

    public Boolean removeSection(Function<MarkdownSection, Boolean> filter, boolean multiple) {
        checkAllowChange();
        Boolean b = filter.apply(this);
        if (b != null)
            return b;

        if (hasChild()) {
            for (int i = 0, n = this.children.size(); i < n; i++) {
                MarkdownSection section = this.children.get(i);
                Boolean bChild = section.removeSection(filter, multiple);
                if (Boolean.TRUE.equals(bChild)) {
                    this.children.remove(i);
                    i--;
                    n--;
                    if (!multiple)
                        return true;
                    b = true;
                }
            }

            if (children.isEmpty()) {
                if (Boolean.TRUE.equals(filter.apply(this)))
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
            for (MarkdownSection child : this.children) {
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

    public void forEachSection(Consumer<MarkdownSection> action) {
        action.accept(this);

        if (this.children != null) {
            for (MarkdownSection child : children) {
                child.forEachSection(action);
            }
        }
    }

    public String toString() {
        return "#".repeat(Math.max(0, level)) + ' ' + title;
    }

    public boolean hasTag() {
        return tagSet != null && !tagSet.isEmpty();
    }

    public void buildText(StringBuilder sb, boolean includeTags) {
        if (getLevel() > 0) {
            sb.append("#".repeat(getLevel())).append(" ");
            if (title != null)
                sb.append(getFullTitle());
            sb.append("\n");
        }

        if (includeTags && hasTag()) {
            sb.append(MarkdownConstants.TAGS_PREFIX).append(StringHelper.join(getTagSet(), ","));
            sb.append("\n");
        }

        if (getText() != null)
            sb.append(getText()).append("\n");

        sb.append("\n");

        if (children != null) {
            for (MarkdownSection child : children) {
                child.buildText(sb, includeTags);
            }
        }
    }

    public void addChild(MarkdownSection child) {
        checkAllowChange();
        if (children == null)
            children = new ArrayList<>();
        children.add(child);
    }

    public void resetLevel(int level) {
        checkAllowChange();

        this.level = level;
        if (children != null) {
            for (MarkdownSection child : children) {
                child.resetLevel(level + 1);
            }
        }
    }

    public List<MarkdownSection> flatten() {
        List<MarkdownSection> ret = new ArrayList<>();
        _flatten(ret);
        return ret;
    }

    private void _flatten(List<MarkdownSection> ret) {
        ret.add(this);
        if (children != null) {
            for (MarkdownSection child : children) {
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

    public int getChildCount() {
        return children == null ? 0 : children.size();
    }

    public MarkdownSection getChild(int index) {
        return children == null ? null : children.get(index);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<MarkdownSection> getChildren() {
        return children;
    }

    public void setChildren(List<MarkdownSection> children) {
        checkAllowChange();
        this.children = children;
    }

    @JsonIgnore
    public MarkdownSection getTpl() {
        return tpl;
    }

    public void setTpl(MarkdownSection tpl) {
        checkAllowChange();
        this.tpl = tpl;
    }
}