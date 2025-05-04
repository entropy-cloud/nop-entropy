package io.nop.markdown.simple;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;
import io.nop.markdown.MarkdownConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

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
        return rootSection.findByTitle(title);
    }

    public MarkdownSection getRootSection() {
        return rootSection;
    }

    public void setRootSection(MarkdownSection section) {
        this.rootSection = Guard.notNull(section, "section");
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

                if(tpl != null) {
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

    public MarkdownDocument filterSectionByTag(String tag) {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.filterSection(section -> {
                if (section.containsTag(tag))
                    return true;
                return section.hasChild() ? null : false;
            }));
        return ret;
    }

    public MarkdownDocument filterSectionByTitle(String title) {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.filterSection(section -> {
                if (title.equals(section.getTitle()))
                    return true;
                return section.hasChild() ? null : false;
            }));
        return ret;
    }

    public MarkdownDocument filterSection(Function<MarkdownSection, Boolean> filter) {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (rootSection != null)
            ret.setRootSection(rootSection.filterSection(filter));
        return ret;
    }

    public MarkdownDocument filterRelatedSection(MarkdownSection section) {
        return filterSection(child -> {
            if (section == child)
                return true;
            return child.hasChild() ? null : false;
        });
    }
}