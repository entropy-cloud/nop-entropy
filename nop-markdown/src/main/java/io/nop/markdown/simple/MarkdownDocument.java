package io.nop.markdown.simple;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;
import io.nop.markdown.utils.MarkdownTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public MarkdownDocument cloneInstance() {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.cloneInstance());
        return ret;
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

    /**
     * 检查markdown文档的结构与模板中定义的结构一致，包含所有结构部分
     */
    public boolean matchTpl(MarkdownDocument tpl, boolean throwError) {
        if (this.rootSection == null || !this.rootSection.hasChild()) {
            throw new NopException(ERR_MARKDOWN_MISSING_SECTION)
                    .param(ARG_TITLE, rootSection.getTitle());
        }

        return this.getRootSection().matchTpl(tpl.getRootSection(), throwError);
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

    public MarkdownDocument selectSection(Predicate<MarkdownSection> filter) {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.selectSection(filter));
        return ret;
    }

    public MarkdownDocument filterSection(Function<MarkdownSection, Boolean> filter) {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.filterSection(filter));
        return ret;
    }
}