/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.response;

import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class XmlResponseParser {
    static final Logger LOG = LoggerFactory.getLogger(XmlResponseParser.class);

    private static XmlResponseParser s_instance = new XmlResponseParser();

    protected XmlResponseParser() {
    }

    public static void registerInstance(XmlResponseParser parser) {
        s_instance = parser;
    }

    public static XmlResponseParser instance() {
        return s_instance;
    }

    public XNode parseResponse(String response) {
        if (StringHelper.isEmpty(response))
            return null;


        TextScanner sc = TextScanner.fromString(null, response);
        int pos = response.indexOf("\n```");
        if (pos >= 0) {
            int pos2 = response.indexOf('<', pos);
            if (pos2 > 0) {
                sc.next(pos);
            }
        }

        sc.skipUntil('<', true);
        if (sc.isEnd())
            return null;

        try {
            return trimAttrs(XNodeParser.instance().parseSingleNode(sc));
        } catch (Exception e) {
            return trimAttrs(tryParseAgain(response));
        }
    }

    XNode tryParseAgain(String response) {
        response = StringHelper.replace(response, "&gt;", ">");
        response = StringHelper.replace(response, "&lt;", "<");

        TextScanner sc = TextScanner.fromString(null, response);
        sc.skipUntil('<', true);
        if (sc.isEnd())
            return null;

        try {
            return XNodeParser.instance().parseSingleNode(sc);
        } catch (Exception e) {
            LOG.debug("nop.err.parse-response-xml-fail", e);
            return null;
        }
    }

    XNode trimAttrs(XNode node) {
        if (node == null)
            return null;
        node.forEachNode(n -> {
            for (Map.Entry<String, ValueWithLocation> entry : n.attrValueLocs().entrySet()) {
                entry.setValue(entry.getValue().trim());
            }
        });
        return node;
    }
}
