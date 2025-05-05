package io.nop.markdown.simple;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;
import io.nop.markdown.MarkdownConstants;
import io.nop.markdown.utils.MarkdownTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private Map<String, MarkdownSection> sectionExtMap;

    public MarkdownDocument cloneInstance() {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.cloneInstance());
        return ret;
    }

    public MarkdownDocument loadSectionExt() {
        if (location != null) {
            MarkdownTool.instance().loadSectionExtForDocument(this);
        }
        return this;
    }

    public MarkdownDocument mergeSectionExt() {
        if (sectionExtMap != null && rootSection != null) {
            rootSection.forEachSection(section -> {
                String sectionNo = section.getSectionNo();
                if (sectionNo != null && sectionExtMap.containsKey(sectionNo)) {
                    MarkdownSection ext = sectionExtMap.get(sectionNo);
                    if (ext != null)
                        section.addChildren(ext.getChildren());
                }
            });
        }
        return this;
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
        rootSection.buildText(sb, includeTags);
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

        return matchSectionChildren(this.getRootSection().getChildren(), tpl.getRootSection(), throwError);
    }

    private boolean matchSectionChildren(List<MarkdownSection> sections, MarkdownSection tplSection, boolean throwError) {
        boolean match = true;

        Map<String, MarkdownSection> byTitle = new HashMap<>();
        if (tplSection.hasChild()) {
            for (MarkdownSection child : tplSection.getChildren()) {
                byTitle.put(child.getTitle(), child);
            }
        }

        if (sections != null) {
            for (MarkdownSection section : sections) {
                MarkdownSection tpl = byTitle.remove(section.getTitle());
                if (tpl != null) {
                    section.setTpl(tpl);
                } else {
                    tpl = tplSection.findChildByTag(MarkdownConstants.TAG_DYNAMIC);
                    if (tpl != null) {
                        section.setTpl(tpl);
                    } else {
                        LOG.info("nop.markdown.section-not-in-tpl:{}", section.getTitle());
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
                        .param(ARG_TITLE, tpl.getTitle());
            }
        }

        return match;
    }

    public boolean removeSectionNoTpl() {
        return rootSection.removeSection(blk -> {
            if (rootSection == blk)
                return false;
            return blk.getTpl() == null;
        }, true);
    }

    public MarkdownDocument selectSectionByTag(String tag) {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.selectSectionByTag(tag));
        return ret;
    }

    public MarkdownDocument selectSectionByTplTag(String tag) {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.selectSectionByTplTag(tag));
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

    public Map<String, MarkdownSection> getSectionExtMap() {
        return sectionExtMap;
    }

    public void setSectionExtMap(Map<String, MarkdownSection> sectionExtMap) {
        this.sectionExtMap = sectionExtMap;
    }

    public void addSectionExt(String sectionNo, MarkdownSection section) {
        if (section == null)
            return;

        if (sectionExtMap == null)
            sectionExtMap = new HashMap<>();
        sectionExtMap.put(sectionNo, section);
    }

    public MarkdownSection getSectionExt(String sectionNo) {
        return sectionExtMap == null ? null : sectionExtMap.get(sectionNo);
    }

    public MarkdownSection getSectionWithExt(String sectionNo) {
        MarkdownSection section = findSectionBySectionNo(sectionNo);
        MarkdownSection sectionExt = getSectionExt(sectionNo);
        if (section == null)
            return sectionExt;
        if (sectionExt == null)
            return section;

        section = sectionExt.shallowCopy();
        section.addChildren(sectionExt.getChildren());
        return section;
    }
}