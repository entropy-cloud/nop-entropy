package io.nop.markdown.simple;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.markdown.utils.MarkdownTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.nop.markdown.MarkdownErrors.ARG_TITLE;
import static io.nop.markdown.MarkdownErrors.ERR_MARKDOWN_MISSING_SECTION;

public class MarkdownDocument implements IComponentModel {
    static final Logger LOG = LoggerFactory.getLogger(MarkdownDocument.class);

    private SourceLocation location;
    private MarkdownSection rootSection;

    public void normalizeSectionNo() {
        if (rootSection != null) {
            rootSection.normalizeSectionNo(null);
        }
    }

    public void resetLevel() {
        if (rootSection != null) {
            if (StringHelper.isBlank(rootSection.getTitleWithSectionNo())
                    && StringHelper.isBlank(rootSection.getText())) {
                rootSection.resetLevel(0);
            } else {
                rootSection.resetLevel(1);
            }
        }
    }

    public MarkdownDocument cloneInstance() {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.cloneInstance());
        return ret;
    }

    public MarkdownDocument getStructure(int depth) {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null) {
            ret.setRootSection(rootSection.getStructure(depth));
        }
        return ret;
    }

    public MarkdownDocument getStructure() {
        return getStructure(10);
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public Set<String> getAllTitles() {
        return rootSection != null ? rootSection.getAllTitles() : null;
    }

    public boolean containsTitle(String title) {
        Set<String> allTitles = getAllTitles();
        if (allTitles == null)
            return false;
        return allTitles.contains(title);
    }

    public void removeSectionWithTag(String tag) {
        if (rootSection != null) {
            if (Boolean.TRUE.equals(rootSection.removeSectionByTag(tag))) {
                this.rootSection = null;
            }
        }
    }

    public MarkdownSection findSectionByTitle(String title) {
        if (rootSection == null)
            return null;
        return rootSection.findSectionByTitle(title);
    }

    public MarkdownSection findSectionBySectionNo(String sectionNo) {
        return rootSection == null ? null : rootSection.findSectionBySectionNo(sectionNo);
    }

    public MarkdownSection getRootSection() {
        return rootSection;
    }

    public void setRootSection(MarkdownSection section) {
        this.rootSection = section;
    }

    public void forEachSection(Consumer<MarkdownSection> action) {
        this.rootSection.forEachSection(action);
    }

    public List<String> getAllFullTitles() {
        List<String> ret = new ArrayList<>();
        forEachSection(section -> {
            if (section.getTitle() != null) {
                ret.add(section.getFullTitle());
            }
        });
        return ret;
    }

    public String toText(boolean includeTags) {
        if (rootSection == null)
            return "";
        StringBuilder sb = new StringBuilder();
        rootSection.buildText(sb, new MarkdownTextOptions().includeTags(includeTags));
        return sb.toString();
    }

    public String toText() {
        return toText(false);
    }

    public String getText() {
        return toText();
    }

    /**
     * 检查markdown文档的结构与模板中定义的结构一致，包含所有结构部分
     */
    public boolean matchTpl(MarkdownDocument tpl, boolean throwError) {
        if (this.rootSection == null || !this.rootSection.hasChild()) {
            throw new NopException(ERR_MARKDOWN_MISSING_SECTION)
                    .param(ARG_TITLE, rootSection.getTitle());
        }

        String tplTitle = tpl.getRootSection().getTitle();

        // 可能会有多余的内容，自动清除
        if (!StringHelper.isEmpty(tplTitle)) {
            MarkdownSection section = this.getRootSection().findSectionByTitle(tplTitle);
            if (section != null) {
                section.setTpl(tpl.getRootSection());
                this.rootSection = section;
            }
        }

        boolean b = this.getRootSection().matchTpl(tpl.getRootSection(), throwError);
        if (b && this.rootSection.getTitle() == null && tplTitle != null) {
            if (!tplTitle.contains("{{")) {
                this.rootSection.setTitle(tplTitle);
            }
        }
        return b;
    }

    public boolean matchTplFromPath(String tplPath, boolean throwError) {
        return matchTpl(MarkdownTool.loadMarkdownTpl(tplPath), throwError);
    }

    public boolean removeSectionNoTpl() {
        return rootSection.removeSection(blk -> {
            if (rootSection == blk)
                return false;
            return blk.getTpl() == null;
        }, true);
    }

    public MarkdownDocument removeSectionByTitle(String title) {
        rootSection.removeSectionByTitle(title);
        return this;
    }

    public MarkdownDocument removeSectionBySectionNo(String sectionNo) {
        rootSection.removeSectionBySectionNo(sectionNo);
        return this;
    }

    public MarkdownDocument selectSectionByTag(String tag) {
        return selectSectionByTag(tag, false);
    }

    public MarkdownDocument selectSectionByTag(String tag, boolean autoIncludeChild) {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.selectSectionByTag(tag, autoIncludeChild));
        return ret;
    }

    public MarkdownDocument selectSectionByTplTag(String tag) {
        return selectSectionByTplTag(tag, false);
    }

    public MarkdownDocument selectSectionByTplTag(String tag, boolean autoIncludeChild) {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.selectSectionByTplTag(tag, autoIncludeChild));
        return ret;
    }

    public MarkdownDocument selectSection(Predicate<MarkdownSection> filter, boolean autoIncludeChild) {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.selectSection(filter, autoIncludeChild));
        return ret;
    }

    public MarkdownDocument filterSection(Function<MarkdownSection, Boolean> filter, boolean autoIncludeChild) {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.filterSection(filter, autoIncludeChild));
        return ret;
    }

    public void addSection(MarkdownSection section) {
        if (section != null) {
            if (rootSection == null) {
                rootSection = section;
            } else {
                rootSection.addChild(section);
            }
        }
    }

    public void splitToDir(File dir, int depth, MarkdownTextOptions options) {
        if (rootSection != null)
            rootSection.splitToDir(dir, depth, options);
    }
}