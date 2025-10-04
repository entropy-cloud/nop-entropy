package io.nop.markdown.simple;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ITextSerializable;
import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.lang.ITagSetSupport;
import io.nop.commons.util.CharSequenceHelper;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.tree.ITreeChildrenStructure;
import io.nop.markdown.MarkdownConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.nop.markdown.MarkdownConstants.SECTION_PREFIX;
import static io.nop.markdown.MarkdownErrors.ARG_TITLE;
import static io.nop.markdown.MarkdownErrors.ERR_MARKDOWN_MISSING_SECTION;

@DataBean
public class MarkdownSection extends MarkdownNode implements ITagSetSupport, ITextSerializable, ITreeChildrenStructure {
    static final Logger LOG = LoggerFactory.getLogger(MarkdownSection.class);

    private int level;
    private String sectionNo;
    private String title;
    private String linkUrl;

    private String text;
    private String summary;

    private int tokenCount;

    private List<MarkdownSection> children;

    private Set<String> tagSet;

    private MarkdownSection tpl;


    public MarkdownSection() {
    }

    public MarkdownSection(int level, String title) {
        this.setLevel(level);
        if (title != null) {
            this.setTitle(title);
            int pos = title.indexOf(' ');
            if (pos > 0) {
                String sectionNo = title.substring(0, pos).trim();
                if (StringHelper.isNumberedPrefix(sectionNo)) {
                    setSectionNo(sectionNo);
                    setTitle(title.substring(pos).trim());
                }
            }
        }
    }

    public static int compareWithSectionNo(MarkdownSection sectionA, MarkdownSection sectionB) {
        String sectionNoA = sectionA.getSectionNo();
        String sectionNoB = sectionB.getSectionNo();

        if (StringHelper.isEmpty(sectionNoA) && StringHelper.isEmpty(sectionNoB)) {
            return 0;
        } else {
            if (StringHelper.isEmpty(sectionNoA))
                return -1;
            if (StringHelper.isEmpty(sectionNoB))
                return 1;
            return StringHelper.compareVersions(sectionNoA, sectionNoB);
        }
    }

    @Override
    public String serializeToString() {
        return toText();
    }

    public void mergeWith(MarkdownSection section) {
        MarkdownSectionMerger.instance().merge(this, section);
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    /**
     * 获取向下几层对象的结构，不包含内容（text和meta等信息）
     *
     * @param depth 向下获取的层数，1表示只获取直接子节点
     * @return 只包含标题和层级信息的简化结构
     */
    public MarkdownSection getStructure(int depth) {
        if (depth <= 0) {
            return cloneInstance(false); // 返回当前节点的副本，不包含子节点
        }

        MarkdownSection copy = cloneInstance(false);
        if (children != null && !children.isEmpty()) {
            List<MarkdownSection> childCopies = new ArrayList<>(children.size());
            for (MarkdownSection child : children) {
                childCopies.add(child.getStructure(depth - 1));
            }
            copy.setChildren(childCopies);
        }
        return copy;
    }

    public MarkdownSection shallowCopy() {
        MarkdownSection ret = cloneInstance(false);
        if (getChildren() != null)
            ret.setChildren(new ArrayList<>(getChildren()));
        return ret;
    }

    public static void removeEmptySections(List<MarkdownSection> sections) {
        sections.removeIf(section -> {
            if (!StringHelper.isBlank(section.getTitle()))
                return false;
            if (!StringHelper.isBlank(section.getText()))
                return false;
            if (section.getChildCount() > 0)
                return false;
            return true;
        });
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

    public void freeze() {
        frozen = true;

        if (this.children != null) {
            for (MarkdownSection child : children) {
                child.freeze();
            }
        }
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    @Override
    public Set<String> getTagSet() {
        return tagSet;
    }

    public void setTagSet(Set<String> tagSet) {
        this.tagSet = tagSet;
    }


    public MarkdownSection cloneInstance() {
        return cloneInstance(true);
    }

    public MarkdownSection cloneInstance(boolean includeChildren) {
        MarkdownSection ret = new MarkdownSection();
        ret.setLocation(getLocation());
        ret.setStartPos(getStartPos());
        ret.setEndPos(getEndPos());
        ret.setLevel(level);
        ret.setTitle(title);
        ret.setText(text);
        ret.setSectionNo(sectionNo);
        ret.setLinkUrl(linkUrl);
        ret.setSummary(summary);

        if (includeChildren && children != null) {
            ret.setChildren(children.stream().map(MarkdownSection::cloneInstance).collect(Collectors.toList()));
        }

        if (tagSet != null)
            ret.setTagSet(new LinkedHashSet<>(tagSet));
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
        StringBuilder sb = new StringBuilder();
        MarkdownSectionHeader.buildText(sb, getLevel(), getSectionNo(), getTitle(), getLinkUrl());
        return sb.toString();
    }

    public String getSectionNo() {
        return sectionNo;
    }

    public String getSectionNoOrTitle() {
        if (sectionNo != null)
            return sectionNo;
        return title;
    }

    /**
     * 设置当前节点的章节编号（sectionNo），保持与 getSectionNo() 对称的逻辑：
     * - 编号位于标题开头，以空格分隔
     * - 如果传入 null 或空字符串，则移除现有编号
     *
     * @param sectionNo 新的章节编号（如 "1.1"）
     */
    public void setSectionNo(String sectionNo) {
        checkAllowChange();

        this.sectionNo = sectionNo;
    }

    public String getParentSectionNo() {
        String prefix = getSectionNo();
        if (prefix == null)
            return null;

        int pos = prefix.lastIndexOf('.');
        if (pos < 0)
            return null;
        return prefix.substring(0, pos + 1);
    }

    public MarkdownSection findSection(Predicate<MarkdownSection> filter) {
        if (filter.test(this))
            return this;
        if (this.children != null) {
            for (MarkdownSection child : this.children) {
                MarkdownSection found = child.findSection(filter);
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

    public MarkdownCodeBlock getCodeBlock(String lang) {
        if (StringHelper.isEmpty(text))
            return null;
        return new MarkdownCodeBlockParser().parseCodeBlockForLang(getLocation(), text, lang);
    }

    public String getCodeSource(String lang) {
        MarkdownCodeBlock block = getCodeBlock(lang);
        return block == null ? null : block.getSource();
    }

    public MarkdownSection findSectionBySectionNo(String sectionNo) {
        return findSection(section -> sectionNo.equals(section.getSectionNo()));
    }

    public MarkdownSection findSectionByTitle(String title) {
        return findSection(section -> title.equals(section.getTitle()));
    }

    public MarkdownSection findSectionByTag(String tag) {
        return findSection(section -> section.containsTag(tag));
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

    public MarkdownSection selectSectionByTag(String tag, boolean autoIncludeChild) {
        return selectSection(section -> {
            return section.containsTag(tag);
        }, autoIncludeChild);
    }

    public MarkdownSection selectSectionByTplTag(String tag, boolean autoIncludeChild) {
        return selectSection(section -> {
            return section.containsTplTag(tag);
        }, autoIncludeChild);
    }

    public boolean containsTplTag(String tag) {
        return getTpl() != null && getTpl().containsTag(tag);
    }

    public MarkdownSection selectSectionByTitle(String title) {
        return selectSection(section -> title.equals(section.getTitle()), true);
    }

    /**
     * 与filterSection的区别在于，selectSelection会包含filter返回为true的节点及其父节点
     *
     * @param filter 返回为false只是表示没有明确指定要包含当前节点，需要检查它的子节点。如果子节点满足选中条件，则当前节点也要被包含
     */
    public MarkdownSection selectSection(Predicate<MarkdownSection> filter, boolean autoIncludeChild) {
        return filterSection(section -> {
            if (filter.test(section))
                return true;
            return section.hasChild() ? null : false;
        }, autoIncludeChild);
    }

    /**
     * 根据过滤条件过滤本节及其子节
     *
     * @param filter 返回true表示包含，返回false表示排除，返回null则检查子节点。如果所有子节点都不包含，且当前节点没有被明确指定包含，则不包含
     * @return null如果当前节以及子节都不包含
     */
    public MarkdownSection filterSection(Function<MarkdownSection, Boolean> filter, boolean autoIncludeChild) {
        Boolean b = filter.apply(this);
        if (Boolean.TRUE.equals(b)) {
            if (autoIncludeChild || !hasChild())
                return this;

            List<MarkdownSection> children = filterChild(filter, autoIncludeChild);

            // 所有child都被接受，所以直接返回当前节点即可
            if (children == null)
                return this;

            MarkdownSection ret = cloneInstance(false);
            ret.setChildren(children);
            return ret;
        }

        if (Boolean.FALSE.equals(b)) {
            return null;
        }

        if (!hasChild())
            return null;

        List<MarkdownSection> children = filterChild(filter, autoIncludeChild);

        // 所有child都被接受，所以直接返回当前节点即可
        if (children == null)
            return this;

        // 所有child都被删除了
        if (children.isEmpty())
            return null;

        MarkdownSection ret = cloneInstance(false);
        ret.setChildren(children);
        return ret;
    }

    private List<MarkdownSection> filterChild(Function<MarkdownSection, Boolean> filter, boolean autoIncludeChild) {
        List<MarkdownSection> ret = null;
        for (int i = 0, n = children.size(); i < n; i++) {
            MarkdownSection child = children.get(i);
            MarkdownSection newChild = child.filterSection(filter, autoIncludeChild);
            if (newChild != child) {
                if (ret == null) {
                    ret = new ArrayList<>();

                    for (int j = 0; j < i; j++) {
                        ret.add(children.get(j));
                    }
                }

                if (newChild != null)
                    ret.add(newChild);
            } else {
                if (ret != null)
                    ret.add(newChild);
            }
        }
        return ret;
    }

    public Boolean removeSectionBySectionNo(String sectionNo) {
        return removeSection(section -> sectionNo.equals(section.getSectionNo()) ? true : null, false);
    }

    public Boolean removeSectionByTitle(String title) {
        return removeSection(section -> title.equals(section.getTitle()) ? true : null, false);
    }

    public Boolean removeSectionByTag(String tag) {
        return removeSection(section -> section.containsTag(tag) ? true : null, true);
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
                        return null;
                }
            }

            if (children.isEmpty()) {
                if (Boolean.TRUE.equals(filter.apply(this)))
                    return true;
            }
        }

        return null;
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

    public void forEachSection(Consumer<MarkdownSection> action) {
        action.accept(this);

        if (this.children != null) {
            for (MarkdownSection child : children) {
                child.forEachSection(action);
            }
        }
    }

    public boolean matchTpl(MarkdownSection tpl, boolean throwError) {
        return matchSectionChildren(this.getChildren(), tpl, throwError);
    }

    private boolean matchSectionChildren(List<MarkdownSection> sections, MarkdownSection tplSection, boolean throwError) {
        boolean match = true;

        Map<String, MarkdownSection> byTitle = new HashMap<>();
        if (tplSection.hasChild()) {
            for (MarkdownSection child : tplSection.getChildren()) {
                byTitle.put(child.getSectionNoOrTitle(), child);
            }
        }

        if (sections != null) {
            for (MarkdownSection section : sections) {
                MarkdownSection tpl = byTitle.remove(section.getSectionNoOrTitle());
                if (tpl != null) {
                    section.setTpl(tpl);
                } else {
                    tpl = tplSection.findChildByTag(MarkdownConstants.TAG_DYNAMIC);
                    if (tpl != null) {
                        section.setTpl(tpl);
                    } else {
                        LOG.info("nop.markdown.section-not-in-tpl:{}", section.getFullTitle());
                    }
                }

                if (tpl != null) {
                    match = match && matchSectionChildren(section.getChildren(), tpl, throwError);
                }
            }
        }

        for (MarkdownSection tpl : byTitle.values()) {
            if (!tpl.containsTag(MarkdownConstants.TAG_OPTIONAL) && !tpl.containsTag(MarkdownConstants.TAG_DYNAMIC)) {
                if (!throwError)
                    return false;
                throw new NopException(ERR_MARKDOWN_MISSING_SECTION)
                        .param(ARG_TITLE, tpl.getFullTitle());
            }
        }

        return match;
    }

    public String toString() {
        return "#".repeat(Math.max(0, level)) + (title == null ? "" : " " + title);
    }

    public boolean hasTag() {
        return tagSet != null && !tagSet.isEmpty();
    }

    public String toText(boolean includeTags) {
        StringBuilder sb = new StringBuilder();
        MarkdownTextOptions options = new MarkdownTextOptions();
        options.setIncludeTags(includeTags);
        buildText(sb, options);
        return sb.toString();
    }

    private String buildText(MarkdownTextOptions options) {
        StringBuilder sb = new StringBuilder();
        buildText(sb, options);
        return sb.toString();
    }

    public void buildText(StringBuilder sb, MarkdownTextOptions options) {
        if (options == null)
            options = DEFAULT_OPTIONS;

        buildMainText(sb, options);

        if (children != null) {
            for (MarkdownSection child : children) {
                child.buildText(sb, options);
            }
        }
    }

    protected void buildMainText(StringBuilder sb, MarkdownTextOptions options) {
        boolean includeTags = options.isIncludeTags();

        if (getLevel() > 0) {
            sb.append(getFullTitle());
            sb.append("\n");
        }

        if (includeTags && hasTag()) {
            sb.append(MarkdownConstants.TAGS_PREFIX).append(StringHelper.join(getTagSet(), ","));
            sb.append("\n");
        }

        if (getSummary() != null) {
            sb.append("<summary>")
                    .append(StringHelper.escapeXmlValue(getSummary()))
                    .append("</summary>\n");
        }

        if (getText() != null)
            sb.append(getText());

        if (!CharSequenceHelper.endsWith(sb, "\n\n"))
            sb.append("\n\n");
    }

    public void addChild(MarkdownSection child) {
        checkAllowChange();
        if (children == null)
            children = new ArrayList<>();
        children.add(child);
    }

    public void addChildren(Collection<MarkdownSection> list) {
        if (list != null)
            list.forEach(this::addChild);
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

    public String getTitleWithSectionNo() {
        if (sectionNo == null || title == null) {
            if (sectionNo == null)
                return title;
            return sectionNo;
        }
        return sectionNo + ' ' + title;
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

    /**
     * 删除当前的sectionNo，按照层级结构重新编配sectionNo
     *
     * @param prevNumbers 父层级的编号数组，可以为null表示从顶级开始
     */
    public void normalizeSectionNo(MutableIntArray prevNumbers) {
        checkAllowChange();

        int currentLevel = level - 1;
        MutableIntArray numbers = prevNumbers != null ? prevNumbers : MutableIntArray.empty();

        if (level == 0) {
            // 递归处理子节点
            if (children != null) {
                for (MarkdownSection child : children) {
                    child.normalizeSectionNo(numbers);
                }
            }
            return; // 根节点不参与编号
        }

        // 确保 numbers 的长度足够
        while (numbers.size() <= currentLevel) {
            numbers.push(0); // 新增层级，初始化为0
        }

        // 移除多余层级
        while (numbers.size() > currentLevel + 1) {
            numbers.pop();
        }

        // 当前层级的编号+1（确保从1开始）
        numbers.set(currentLevel, numbers.get(currentLevel) + 1);

        // 生成新的sectionNo（如 "1", "1.1", "2.3.1"）
        StringBuilder sectionNo = new StringBuilder();
        for (int i = 0; i <= currentLevel; i++) {
            if (i > 0) sectionNo.append(".");
            sectionNo.append(numbers.get(i));
        }
        setSectionNo(sectionNo.toString());

        // 递归处理子节点
        if (children != null) {
            for (MarkdownSection child : children) {
                child.normalizeSectionNo(numbers);
            }
        }
    }

    /**
     * 生成当前节点的 index.md 内容
     *
     * @param options 生成选项（保留但不再使用内部字段）
     */
    public String toIndexMarkdown(int depth, MarkdownTextOptions options) {

        StringBuilder sb = new StringBuilder();
        if (options == null) {
            options = DEFAULT_OPTIONS;
        }

        if (depth <= 0) {
            buildText(sb, options);
            return sb.toString();
        }

        buildMainText(sb, options);

        // 3. 添加子节点链接（始终生成）
        if (children != null && !children.isEmpty()) {
            for (MarkdownSection child : children) {
                String childLink = getChildLink(child, depth);
                MarkdownSectionHeader.buildText(sb, child.getLevel(), child.getSectionNo(), child.getTitle(), childLink);
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 生成子节点的链接路径.
     *
     * @param child 子节点
     * @param depth 剩余递归深度
     * @return 子节点的相对路径（如 "./section-1.1/index.md" 或 "./section-1.1.md"）
     */
    protected String getChildLink(MarkdownSection child, int depth) {
        String sectionNo = child.getSectionNo();
        if (StringHelper.isEmpty(sectionNo))
            sectionNo = child.getTitle();

        // 根据 depth 决定是否生成子目录
        if (depth > 1) {
            return SECTION_PREFIX + sectionNo + "/index.md";
        } else {
            return SECTION_PREFIX + sectionNo + ".md";
        }
    }

    /**
     * 将当前节点及子节点保存为目录结构或文件。
     *
     * @param baseDir 基础目录（如 "/docs"）
     * @param depth   剩余递归深度
     */
    public void splitToDir(File baseDir, int depth, MarkdownTextOptions options) {
        // 2. 生成并保存 index.md
        String indexContent = this.toIndexMarkdown(depth, null);
        FileHelper.writeText(new File(baseDir, "index.md"), indexContent, null);

        // 3. 递归保存子节点
        if (children != null && depth > 0) {
            for (MarkdownSection child : children) {
                String childLink = getChildLink(child, depth);
                if (depth == 1) {
                    FileHelper.writeText(new File(baseDir, childLink), child.buildText(options), null);
                } else {
                    File childDir = new File(baseDir, StringHelper.firstPart(childLink, '/'));
                    child.splitToDir(childDir, depth - 1, options);
                }
            }
        }
    }

    public void mergeChild(MarkdownSection section) {
        String sectionNo = section.getSectionNo();
        if (sectionNo == null) {
            addChild(section);
        } else {
            MarkdownSection child = findSectionBySectionNo(sectionNo);
            if (child == null) {
                addChild(section);
            } else {
                child.mergeWith(section);
            }
        }
    }
}