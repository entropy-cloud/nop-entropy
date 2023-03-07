/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.unittest;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.parse.AbstractResourceParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreErrors.ERR_UNITTEST_INVALID_MARKDOWN_TEST_SECTION;

public class MarkdownTestFileParser extends AbstractResourceParser<MarkdownTestFile> {

    @Override
    protected MarkdownTestFile doParseResource(IResource resource) {
        String text = ResourceHelper.readText(resource);
        text = StringHelper.replace(text, "\r\n", "\n");
        TextScanner sc = TextScanner.fromString(SourceLocation.fromPath(resource.getPath()), text);
        MarkdownTestFile file = new MarkdownTestFile();
        file.setFileName(getResourceName());
        file.setSections(parseBlocks(sc));
        return file;
    }

    private List<MarkdownTestSection> parseBlocks(TextScanner sc) {
        List<MarkdownTestSection> blocks = new ArrayList<>();

        String fileName = getResourceName();
        while (!sc.isEnd()) {
            if (sc.tryMatch('#')) {
                MarkdownTestSection block = parseBlock(sc);
                if (block != null) {
                    block.setFileName(fileName);
                    blocks.add(block);
                }
            } else {
                sc.skipLine();
            }
        }
        if (!sc.isEnd())
            throw sc.newError(ERR_UNITTEST_INVALID_MARKDOWN_TEST_SECTION);

        return blocks;
    }

    private MarkdownTestSection parseBlock(TextScanner sc) {
        sc.skipUntil(s -> s.cur != '#', true, "~#");
        String title = sc.nextLine().trim().toString();
        if (sc.isEnd())
            return null;

        if (!sc.startsWith("```"))
            sc.skipUntil("\n```", true);
        if (sc.isEnd())
            return null;
        sc.next();
        MarkdownCodeBlock code = readCodeBlock(sc);

        Map<String, MarkdownCodeBlock> attrs = null;
        if (!sc.isEnd()) {
            attrs = parseAttrs(sc);
        }

        MarkdownTestSection block = new MarkdownTestSection();
        block.setTitle(title);
        block.setType(code.getType());
        block.setLocation(code.getLocation());
        block.setSource(code.getSource());
        block.setAttributes(attrs);
        return block;
    }

    private MarkdownCodeBlock readCodeBlock(TextScanner sc) {
        sc.skipUntil(s -> s.cur != '`', true, "~`");
        String type = sc.nextLine().trim().toString();
        SourceLocation loc = sc.location();
        String source = sc.nextUntil("\n```", false).toString();
        sc.next();
        sc.skipLine();

        MarkdownCodeBlock code = new MarkdownCodeBlock();
        code.setType(type);
        code.setLocation(loc);
        code.setSource(source);
        return code;
    }

    private Map<String, MarkdownCodeBlock> parseAttrs(TextScanner sc) {
        skipToAttr(sc);

        Map<String, MarkdownCodeBlock> attrs = new HashMap<>();

        while (sc.cur == '*') {
            sc.next();
            SourceLocation loc = sc.location();
            String key = sc.nextLine().trim().toString();
            int pos = key.indexOf(':');
            if (pos > 0) {
                String value = key.substring(pos + 1).trim();
                key = key.substring(0, pos).trim();
                if (!value.isEmpty()) {
                    attrs.put(key, parseAttrBlock(loc, value));
                    continue;
                }
            }
            if (sc.isEnd())
                break;

            sc.skipEmptyLines();
            if (sc.startsWith("```")) {
                MarkdownCodeBlock code = readCodeBlock(sc);
                attrs.put(key, code);
            } else {
                sc.skipBlank();
                loc = sc.location();
                String value = sc.nextUntil(s -> s.startsWith("\n#") || s.startsWith("\n*"), true, "\n#").trim()
                        .toString();
                if (!value.isEmpty()) {
                    attrs.put(key, MarkdownCodeBlock.build(loc, null, value));
                }
            }
        }
        return attrs;
    }

    private MarkdownCodeBlock parseAttrBlock(SourceLocation loc, String attr) {
        if (attr.startsWith("@")) {
            int pos = attr.indexOf(':');
            if (pos < 0) {
                return MarkdownCodeBlock.build(loc, null, attr);
            } else {
                String type = attr.substring(1, pos).trim();
                String value = attr.substring(pos + 1).trim();
                return MarkdownCodeBlock.build(loc, type, value);
            }
        }
        return MarkdownCodeBlock.build(loc, null, attr);
    }

    private void skipToAttr(TextScanner sc) {
        while (!sc.isEnd() && sc.cur != '*' && sc.cur != '#') {
            sc.skipLine();
        }
    }
}