package io.nop.markdown.dsl;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.json.JObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.core.resource.component.parse.ITextResourceParser;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.model.MarkdownListItem;
import io.nop.markdown.model.MarkdownSection;
import io.nop.markdown.simple.MarkdownDocumentParser;
import io.nop.markdown.simple.MarkdownListParser;
import io.nop.markdown.utils.MarkdownHelper;

import java.util.ArrayList;
import java.util.List;

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
        // # [value](name)
        //   - name: value
        //   - name: value
        // ## 1. name: value
        // ## 2.
        return null;
    }

    void parseObjectSection(MarkdownSection section, JObject obj) {
        parseTitleProp(section, obj);

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
                value = section.getTitle().substring(pos + 1).trim();
            } else {
                name = "name";
                value = section.getTitle();
            }
            obj.put(name, value);
        }
    }

    Pair<String, String> parseNameValuePair(SourceLocation loc, String title) {
        title = title.trim();
        if (containsLink(title)) {
            int pos = title.indexOf("](");
            String content = title.substring(1, pos);
            String name = title.substring(pos + 2, title.length() - 1).trim();
            return Pair.of(name, content);
        } else {
            int pos = title.indexOf(':');
            if (pos < 0) {
                String name = title.substring(0, pos).trim();
                String value = title.substring(pos + 1).trim();
                return Pair.of(name, value);
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
        if (MarkdownHelper.isOrderedItem(children.get(0).getContent())) {
            List<Object> ret = new ArrayList<>();
            for (MarkdownListItem child : children) {
                if (child.hasChildren()) {
                    ret.add(parseItemChildren(child));
                } else {
                    ret.add(removeOrderNo(child.getContent()));
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

        }
    }

    static String removeOrderNo(String text) {
        if (MarkdownHelper.isOrderedItem(text)) {
            int pos = text.indexOf('.');
            return text.substring(pos + 1).trim();
        }
        return text;
    }
}
