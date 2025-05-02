package io.nop.markdown.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.mutable.MutableInt;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.TagsHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.markdown.MarkdownConstants;

import java.util.ArrayList;
import java.util.List;

public class MarkdownDocumentParser extends AbstractResourceParser<MarkdownDocument> {

    @Override
    protected MarkdownDocument doParseResource(IResource resource) {
        String text = ResourceHelper.readText(resource, null);
        return parseFromText(SourceLocation.fromPath(resource.getPath()), text);
    }

    public MarkdownDocument parseFromText(SourceLocation loc, String text) {
        text = text.trim();

        MarkdownDocument model = new MarkdownDocument();
        model.setLocation(loc);

        MarkdownSection section = parseRootSection(text);
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
            MarkdownTitle mt = new MarkdownTitleParser().parseTitle(title);
            section.setTitle(mt.getNormalizedTitle());
            section.setMeta(mt.getMeta());
        }
    }

    public MarkdownSection parseRootSection(String text) {
        text = text.trim();
        if (StringHelper.isEmpty(text))
            return null;

        List<MarkdownSection> sections = parseSections(text);
        sections = MarkdownSection.buildTree(sections);

        if (sections.size() == 1) {
            return sections.get(0);
        } else {
            MarkdownSection section = new MarkdownSection();
            section.setChildren(sections);
            return section;
        }
    }

    public List<MarkdownSection> parseSections(String text) {
        List<MarkdownSection> sections = new ArrayList<>();
        text = text.trim();

        MutableInt index = new MutableInt();

        if (text.startsWith("#")) {
            MarkdownSection section = parseSection(text, index);
            sections.add(section);
        } else if (text.startsWith("\n#")) {
            index.set(1);
        }

        do {
            MarkdownSection section = parseSection(text, index);
            if (section == null)
                break;
            sections.add(section);
        } while (true);

        return sections;
    }

    MarkdownSection parseSection(String text, MutableInt index) {
        int pos = index.get();
        if (text.length() <= pos)
            return null;

        int pos2 = text.indexOf("\n#", pos);
        if (pos2 < 0) {
            pos2 = text.length();
            index.set(pos2);
        } else {
            index.set(pos2 + 1);
        }

        MarkdownSection section = new MarkdownSection();

        int level = countSectionLevel(text, pos);
        section.setLevel(level);

        if (level > 0) {
            int pos3 = text.indexOf("\n", pos);
            if (pos3 < 0)
                pos3 = text.length();

            String title = text.substring(pos + level, pos3).trim();
            section.setTitle(title);
            pos = pos3 + 1;
        }

        if (pos < pos2) {
            section.setText(text.substring(pos, pos2));
        }

        return section;
    }

    int countSectionLevel(String text, int pos) {
        int count = 0;
        for (int i = pos, n = text.length(); i < n; i++) {
            char c = text.charAt(i);
            if (c == '#') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}