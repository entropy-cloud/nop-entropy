package io.nop.markdown.dsl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.SourceCodeBlock;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.json.JObject;
import io.nop.core.model.table.impl.BaseTable;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.core.resource.component.parse.ITextResourceParser;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.model.MarkdownListItem;
import io.nop.markdown.model.MarkdownSection;
import io.nop.markdown.simple.MarkdownCodeBlockParser;
import io.nop.markdown.simple.MarkdownDocumentParser;
import io.nop.markdown.simple.MarkdownListParser;
import io.nop.markdown.table.MarkdownTableParser;
import io.nop.markdown.utils.MarkdownHelper;

import java.util.ArrayList;
import java.util.List;

import static io.nop.markdown.MarkdownErrors.ERR_MARKDOWN_NOT_ALL_CHILD_ORDERED;
import static io.nop.markdown.MarkdownErrors.ERR_MARKDOWN_NOT_ALL_CHILD_SECTION_ORDERED;

public class MarkdownObjectParser extends AbstractResourceParser<Object>
        implements ITextResourceParser<Object> {

    private final MarkdownListParser listParser = new MarkdownListParser(true);

    public MarkdownObjectParser() {
    }

    @Override
    protected Object doParseResource(IResource resource) {
        SourceLocation loc = SourceLocation.fromPath(resource.getStdPath());
        return parseFromText(loc, resource.readText());
    }

    @Override
    public Object parseFromText(SourceLocation loc, String text) {
        MarkdownDocument doc = new MarkdownDocumentParser().parseFromText(loc, text);
        return parseFromDocument(doc);
    }

    public Object parseFromDocument(MarkdownDocument doc) {
        return parseObjectFromSection(doc.getRootSection());
    }

    public JObject parseObjectFromSection(MarkdownSection section) {
        JObject obj = new JObject();
        parseTitleProp(section, obj);

        parseObjectFromSection(section, obj);
        return obj;
    }

    void parseObjectFromSection(MarkdownSection section, JObject obj) {
        List<MarkdownListItem> items = listParser.parseAllListItems(section.getContentLocation(), section.getContent());
        parseListItems(items, obj);

        parseSectionChildren(section, obj);
    }

    void parseTitleProp(MarkdownSection section, JObject obj) {
        String name = section.getLinkUrl();
        if (StringHelper.isValidXmlName(name)) {
            obj.put(name, ValueWithLocation.of(section.getLocation(), section.getFullTitle()));
        } else if (!StringHelper.isEmpty(section.getTitle())) {
            int pos = section.getTitle().indexOf(':');
            String value;
            if (pos <= 0) {
                name = section.getTitle().substring(0, pos).trim();
                name = normalizeKey(name);
                value = section.getTitle().substring(pos + 1).trim();
            } else {
                name = "name";
                value = section.getTitle();
            }
            value = decodeText(value);
            obj.put(name, value);
        }
    }

    String normalizeKey(String key) {
        key = MarkdownHelper.removeStyle(key);
        return key;
    }

    Pair<String, String> parseNameValuePair(SourceLocation loc, String title) {
        title = title.trim();
        if (containsLink(title)) {
            int pos = title.indexOf("](");
            String content = title.substring(1, pos);
            String name = title.substring(pos + 2, title.length() - 1).trim();
            return Pair.of(name, decodeText(content));
        } else {
            int pos = title.indexOf(':');
            if (pos < 0) {
                String name = title.substring(0, pos).trim();
                name = normalizeKey(name);
                String value = title.substring(pos + 1).trim();
                return Pair.of(name, decodeText(value));
            } else {
                return Pair.of(title, null);
            }
        }
    }

    boolean containsLink(String title) {
        return title.startsWith("[") && title.contains("](") && title.endsWith(")");
    }

    void parseListItems(List<MarkdownListItem> items, JObject obj) {
        for (MarkdownListItem item : items) {
            if (item.hasChildren()) {
                Object child = parseItemChildren(item);
                obj.put(item.getContent().trim(), ValueWithLocation.of(item.getLocation(), child));
            } else {
                Pair<String, String> pair = parseNameValuePair(item.getLocation(), item.getContent());
                obj.put(pair.getKey(), ValueWithLocation.of(item.getLocation(), pair.getValue()));
            }
        }
    }

    Object parseItemChildren(MarkdownListItem item) {
        List<MarkdownListItem> children = item.getChildren();
        if (children == null || children.isEmpty())
            return null;

        if (item.containsOrderedChild()) {
            if (!item.isAllChildOrdered())
                throw new NopException(ERR_MARKDOWN_NOT_ALL_CHILD_ORDERED)
                        .source(item);

            List<Object> ret = new ArrayList<>();
            for (MarkdownListItem child : children) {
                if (child.hasChildren()) {
                    ret.add(parseItemChildren(child));
                } else {
                    ret.add(decodeText(child.getContent()));
                }
            }
            return ret;
        } else {
            JObject obj = new JObject();
            parseListItems(children, obj);
            return obj;
        }
    }

    void parseSectionChildren(MarkdownSection section, JObject obj) {
        List<MarkdownSection> children = section.getChildren();
        if (children == null || children.isEmpty())
            return;

        for (MarkdownSection child : children) {
            String varName = getVarName(child);
            Object value;

            if (containsOrderedChildSection(child)) {
                if (!isAllChildSectionOrdered(child))
                    throw new NopException(ERR_MARKDOWN_NOT_ALL_CHILD_SECTION_ORDERED)
                            .source(child);

                value = parseSectionList(section);
            } else {
                value = parseSectionValue(section);
            }
            obj.put(varName, ValueWithLocation.of(child.getContentLocation(), value));
        }
    }

    String decodeText(String text) {
        if (text == null || text.isEmpty())
            return null;
        return StringHelper.unquote(text);
    }

    Object parseSectionList(MarkdownSection section) {
        List<Object> ret = new ArrayList<>(section.getChildCount());
        for (MarkdownSection child : section.getChildren()) {
            Object value = parseObjectFromSection(child);
            ret.add(value);
        }
        return ret;
    }

    Object parseSectionValue(MarkdownSection section) {

        // section content只能是code block、list、table、普通文本
        String content = section.getContent();
        if (!StringHelper.isBlank(content)) {
            SourceCodeBlock block = MarkdownCodeBlockParser.INSTANCE.parseCodeBlockForLang(
                    section.getContentLocation(), content, null);
            if (block != null)
                return block;

            int pos = MarkdownHelper.findTable(content);
            if (pos >= 0) {
                SourceLocation loc = section.getContentLocation();
                if (loc != null) {
                    loc = loc.skipContent(content, 0, pos);
                }
                BaseTable table = MarkdownTableParser.parseTable(loc, content.substring(pos + 1));
                return MarkdownHelper.toRecordList(table);
            }
        }

        JObject obj = new JObject();
        obj.setLocation(section.getContentLocation());

        parseObjectFromSection(section, obj);
        return obj;
    }

    boolean containsSectionContent(String content) {
        if (content.startsWith("```") || content.contains("\n```"))
            return true;
        if (content.startsWith("|") || content.contains("\n|"))
            return true;
        if (content.startsWith("-") || content.contains("\n-"))
            return true;
        if (content.startsWith("*") || content.contains("**"))
            return true;
        return false;
    }

    String getVarName(MarkdownSection section) {
        String url = section.getLinkUrl();
        if (StringHelper.isEmpty(url))
            return url;
        return section.getTitle();
    }

    boolean containsOrderedChildSection(MarkdownSection section) {
        for (MarkdownSection child : section.getChildren()) {
            if (child.getSectionNo() != null)
                return true;
        }
        return false;
    }

    boolean isAllChildSectionOrdered(MarkdownSection section) {
        for (MarkdownSection child : section.getChildren()) {
            if (child.getSectionNo() == null)
                return false;
        }
        return true;
    }
}
