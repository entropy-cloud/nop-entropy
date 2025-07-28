package io.nop.markdown.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.TagsHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.markdown.MarkdownConstants;

import java.util.ArrayList;
import java.util.List;

import static io.nop.markdown.simple.MarkdownSection.removeEmptySections;

public class MarkdownDocumentParser extends AbstractResourceParser<MarkdownDocument> {

    @Override
    protected MarkdownDocument doParseResource(IResource resource) {
        String text = ResourceHelper.readText(resource, null);
        return parseFromText(SourceLocation.fromPath(resource.getPath()), text);
    }

    public MarkdownDocument parseFromText(SourceLocation loc, String text) {
        TextScanner sc = TextScanner.fromString(loc, text);

        MarkdownDocument model = new MarkdownDocument();
        model.setLocation(loc);

        MarkdownSection section = parseRootSection(sc);
        section.forEachSection(this::normalizeSectionContent);
        model.setRootSection(section);
        return model;
    }

    protected void normalizeSectionContent(MarkdownSection section) {
        normalizeTitle(section);

        String text = section.getText();
        if (text != null) {
            text = text.trim();
            if (text.startsWith(MarkdownConstants.TAGS_PREFIX)) {
                int pos = text.indexOf('\n');
                if (pos < 0)
                    pos = text.length();

                String tags = text.substring(MarkdownConstants.TAGS_PREFIX.length(), pos);
                section.setTagSet(TagsHelper.parse(tags, ','));

                if (pos == text.length()) {
                    text = null;
                } else {
                    text = text.substring(pos + 1).trim();
                }
            }
            section.setText(text);
        }
    }

    protected void normalizeTitle(MarkdownSection section) {
        String title = section.getTitle();
        if (title != null) {
            MarkdownSectionHeader mt = MarkdownSectionHeaderParser.INSTANCE.parseSectionHeader(title);
            if (mt != null) {
                section.setTitle(mt.getTitle());
                if (mt.getLevel() > 0)
                    section.setLevel(mt.getLevel());
                section.setLinkUrl(mt.getLinkUrl());
                section.setSectionNo(mt.getSectionNo());
            }
        }
    }

    public MarkdownSection parseRootSection(TextScanner sc) {
        sc.skipBlank();
        if (sc.isEnd())
            return null;

        List<MarkdownSection> sections = parseSections(sc);
        removeEmptySections(sections);
        sections = MarkdownSection.buildTree(sections);

        if (sections.size() == 1) {
            return sections.get(0);
        } else {
            MarkdownSection section = new MarkdownSection();
            section.setChildren(sections);
            return section;
        }
    }

    public List<MarkdownSection> parseSections(TextScanner sc) {
        List<MarkdownSection> sections = new ArrayList<>();
        sc.skipBlank();

        while (!sc.isEnd()) {
            MarkdownSection section = parseSection(sc);
            if (section == null)
                break;
            sections.add(section);
        }

        return sections;
    }

    MarkdownSection parseSection(TextScanner sc) {

        MarkdownSection section = new MarkdownSection();
        section.setLocation(sc.location());
        section.setStartPos(sc.pos);

        if (sc.cur == '#' && sc.col == 1) {
            int level = countSectionLevel(sc);
            section.setLevel(level);
            String title = sc.nextLine().trim().toString();
            section.setTitle(title);
        }


        String text = sc.nextUntil('\n', '#', true).toString();
        section.setText(text);
        section.setEndPos(sc.pos);
        if (!sc.isEnd())
            sc.next();

        return section;
    }

    int countSectionLevel(TextScanner sc) {
        int count = 0;
        while (!sc.isEnd()) {
            int c = sc.cur;
            if (c == '#') {
                count++;
                sc.next();
            } else {
                break;
            }
        }
        return count;
    }
}