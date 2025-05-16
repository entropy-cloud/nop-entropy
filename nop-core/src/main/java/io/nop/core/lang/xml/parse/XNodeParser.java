/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml.parse;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.CommonErrors;
import io.nop.commons.io.stream.CharSequenceReader;
import io.nop.commons.io.stream.FastBufferedReader;
import io.nop.commons.io.stream.ICharReader;
import io.nop.commons.text.CDataText;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.handler.CollectXNodeHandler;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractCharReaderResourceParser;
import io.nop.core.resource.impl.InMemoryTextResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static io.nop.core.CoreConfigs.CFG_XML_MAX_NESTED_LEVEL;
import static io.nop.core.CoreErrors.ARG_ATTR_NAME;
import static io.nop.core.CoreErrors.ARG_ATTR_VALUE;
import static io.nop.core.CoreErrors.ARG_EXPECTED;
import static io.nop.core.CoreErrors.ARG_OLD_LOC;
import static io.nop.core.CoreErrors.ARG_PROLOG;
import static io.nop.core.CoreErrors.ARG_START_LOC;
import static io.nop.core.CoreErrors.ERR_XML_ATTR_VALUE_NOT_QUOTED;
import static io.nop.core.CoreErrors.ERR_XML_DOC_NOT_END_PROPERLY;
import static io.nop.core.CoreErrors.ERR_XML_DUPLICATE_ATTR_NAME;
import static io.nop.core.CoreErrors.ERR_XML_EXCEED_MAX_NESTED_LEVEL;
import static io.nop.core.CoreErrors.ERR_XML_INVALID_INSTRUCTION;
import static io.nop.core.CoreErrors.ERR_XML_INVALID_XML_PROLOG;
import static io.nop.core.CoreErrors.ERR_XML_IS_EMPTY;
import static io.nop.core.CoreErrors.ERR_XML_NOT_ALLOW_MULTIPLE_ROOT;
import static io.nop.core.CoreErrors.ERR_XML_PARSE_ESCAPE_CHAR_FAIL;
import static io.nop.core.CoreErrors.ERR_XML_PARSE_XML_ENTITY_FAIL;
import static io.nop.core.CoreErrors.ERR_XML_STRING_NOT_END_PROPERLY;
import static io.nop.core.CoreErrors.ERR_XML_TAG_NOT_END_PROPERLY;
import static io.nop.core.CoreErrors.ERR_XML_UNEXPECTED_CHAR;
import static io.nop.core.CoreErrors.ERR_XML_UNEXPECTED_EOF;
import static io.nop.core.CoreErrors.ERR_XML_UNKNOWN_XML_ENTITY;

public class XNodeParser extends AbstractCharReaderResourceParser<XNode> implements IXNodeParser {
    public static IXNodeParser instance() {
        return new XNodeParser();
    }

    static final Set<String> PROLOG_ATTR_NAMES = new HashSet<>(Arrays.asList("version", "encoding", "standalone"));

    private int _depth;
    private int maxDepth = CFG_XML_MAX_NESTED_LEVEL.get();
    private boolean intern;

    // AI返回的XML可能有错误，这里进行一定的容错
    private boolean loopMode;

    protected XNodeParser() {
    }

    protected void incParseDepth(TextScanner sc) {
        _depth++;
        if (_depth > maxDepth) {
            throw newError(ERR_XML_EXCEED_MAX_NESTED_LEVEL);
        }
    }

    protected void decParseDepth() {
        _depth--;
    }

    private boolean keepComment;
    private boolean keepWhitespace;
    private boolean forFragments;
    private boolean forHtml;

    private String defaultEncoding;
    private IXNodeHandler handler;

    private TextScanner sc;

    private SourceLocation prevLoc;
    private boolean prevCDATA;
    private String prevText;

    private boolean hasNode;

    public IXNodeParser looseMode(boolean looseMode) {
        this.loopMode = looseMode;
        return this;
    }

    @Override
    public IXNodeParser forHtml(boolean forHtml) {
        this.forHtml = forHtml;
        return this;
    }

    public IXNodeParser shouldTraceDepends(boolean value) {
        return (IXNodeParser) super.shouldTraceDepends(value);
    }

    @Override
    public IXNodeParser keepComment(boolean keepComment) {
        this.keepComment = keepComment;
        return this;
    }

    @Override
    public IXNodeParser intern(boolean intern) {
        this.intern = intern;
        return this;
    }

    @Override
    public IXNodeParser keepWhitespace(boolean keepWhitespace) {
        this.keepWhitespace = keepWhitespace;
        return this;
    }

    @Override
    public IXNodeParser defaultEncoding(String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
        return this;
    }

    @Override
    public IXNodeParser handler(IXNodeHandler handler) {
        this.handler = handler;
        return this;
    }

    @Override
    public IXNodeParser forFragments(boolean forFragments) {
        this.forFragments = forFragments;
        return this;
    }

    @Override
    protected ICharReader toReader(IResource resource) {
        if (resource instanceof InMemoryTextResource) {
            return new CharSequenceReader(((InMemoryTextResource) resource).getText());
        }

        InputStream is = resource.getInputStream();
        try {
            is = IoHelper.toBufferedInputStream(is);

            String encoding = guessEncoding(is);
            setEncoding(encoding);
            Reader in = IoHelper.toReader(is, encoding);
            return new FastBufferedReader(in);
        } catch (Exception e) {
            IoHelper.safeClose(is);
            throw NopException.adapt(e);
        }
    }

    NopException newError(ErrorCode errorCode) {
        return newError(errorCode, null);
    }

    NopException newError(ErrorCode errorCode, Throwable cause) {
        if (sc != null) {
            return sc.newError(errorCode, cause);
        }
        return new NopException(errorCode).cause(cause);
    }

    String guessEncoding(InputStream is) throws IOException {
        String encoding = IoHelper.getEncodingFromBOM(is);
        if (encoding != null)
            return encoding;

        is.mark(128);
        byte[] data = new byte[128];
        IoHelper.readFully(is, data, 0, 2);
        if (data[0] == '<' && data[1] == '?') {
            int pos = IoHelper.readTill(is, (byte) '>', data, 2);
            String s = new String(data, 0, pos + 1, StandardCharsets.ISO_8859_1);
            if (s.startsWith("<?xml ") && s.endsWith("?>")) {
                int i = s.indexOf("encoding");
                if (i < 0)
                    return "UTF-8";
                while (s.charAt(i) != '"' && s.charAt(i) != '\'' && s.charAt(i) != '>')
                    i++;
                if (s.charAt(i) == '>')
                    throw newError(ERR_XML_INVALID_XML_PROLOG);

                char quote = s.charAt(i++);
                int end = s.indexOf(quote, i);
                if (end < 0)
                    throw newError(ERR_XML_INVALID_XML_PROLOG);

                encoding = s.substring(i, end);
            }
            if (encoding == null)
                encoding = defaultEncoding;
        }
        is.reset();
        return encoding;
    }

    public XNode parseFromReader(SourceLocation loc, ICharReader in) {
        return doParse(loc, in);
    }

    protected XNode doParse(SourceLocation loc, ICharReader in) {
        if (this.handler == null)
            this.handler = new CollectXNodeHandler();
        this.sc = TextScanner.fromReader(loc, in);

        try {
            sc.skipBlank();

            if (this.forFragments) {
                if (sc.isEnd())
                    return XNode.make(loc, CoreConstants.DUMMY_TAG_NAME);

                this.parseFragments();
            } else {
                if (sc.isEnd())
                    throw newError(ERR_XML_IS_EMPTY);
                if (sc.cur != '<')
                    throw newError(ERR_XML_UNEXPECTED_CHAR);

                // skip xml prolog
                parseProlog();

                String docType = parseDOCTYPE();
                String instruction = parseInstruction();

                handler.beginDoc(getEncoding(), docType, instruction);

                parseDoc();

                sc.skipBlank();
                if (sc.cur == '<') {
                    if (sc.peek() != '!')
                        throw newError(ERR_XML_NOT_ALLOW_MULTIPLE_ROOT);
                    sc.consume("<!--");
                    parseComment();
                }
            }

            sc.skipBlank();
            if (!sc.isEnd())
                throw newError(ERR_XML_DOC_NOT_END_PROPERLY);

            XNode doc = handler.endDoc();
            if (!hasNode) {
                if (forFragments)
                    hasNode = true;
            }

            if (!hasNode) {
                throw newError(ERR_XML_IS_EMPTY);
            }

            // doc.normalizeContent();
            return doc;
        } finally {
            IoHelper.safeClose(sc);
        }
    }

    void parseProlog() {
        if (sc.tryConsume("<?xml ")) {
            SourceLocation loc = sc.location();
            MutableString s = sc.nextUntil('?', false);
            sc.match("?>");
            TextScanner lineSc = TextScanner.fromString(loc, s);
            Set<String> attrNames = new HashSet<>();

            do {
                lineSc.skipBlank();
                String name = lineSc.nextJavaVar();
                if (!attrNames.add(name) || !PROLOG_ATTR_NAMES.contains(name)) {
                    throw new NopException(ERR_XML_INVALID_XML_PROLOG).param(ARG_PROLOG, s.toString());
                }
                lineSc.match('=');
                lineSc.nextJavaString();
                lineSc.skipBlank();
            } while (!lineSc.isEnd());
        }
    }

    void parseDoc() {
        // 第一个节点前的所有空白被忽略
        sc.skipBlank();

        ParseResult result;

        do {
            result = parseNode();
            if (result == ParseResult.NODE)
                break;

            if (result == ParseResult.OTHER) {
                sc.skipBlank();
                continue;
            }
            if (result == ParseResult.END_NODE)
                throw newError(ERR_XML_DOC_NOT_END_PROPERLY);
        } while (true);
        flushText();
    }

    protected ParseResult parseNode() {
        incParseDepth(sc);

        try {
            sc.consume('<');
            hasNode = true;
            switch (sc.cur) {
                case '!':
                    sc.next();
                    if (sc.cur == '[') {
                        sc.next();
                        sc.consume("CDATA[");
                        // <![CDATA[
                        parseCDATA();
                        return ParseResult.TEXT;
                    } else if (sc.cur == '-') {
                        sc.next();
                        sc.match('-');
                        // <!--
                        parseComment();
                    } else {
                        throw newError(ERR_XML_UNEXPECTED_CHAR);
                    }
                    return ParseResult.OTHER;
                case '/':
                    return ParseResult.END_NODE;
            }

            flushText();
            SourceLocation loc = sc.location();
            String tagName = intern(sc.nextXmlName());
            sc.skipBlank();
            Map<String, ValueWithLocation> attrs = parseAttrs();

            if (sc.cur == '/') {
                sc.next();
                sc.consume('>');
                handler.simpleNode(loc, tagName, attrs);
                return ParseResult.NODE;
            } else if (sc.cur == '>') {
                handler.beginNode(loc, tagName, attrs);
                sc.next();
                parseBody();
                String expected = '/' + tagName + '>';
                if (!sc.tryConsume(expected))
                    throw sc.newError(ERR_XML_TAG_NOT_END_PROPERLY).param(ARG_EXPECTED, expected).param(ARG_START_LOC,
                            loc);
                handler.endNode(tagName);
                return ParseResult.NODE;
            } else {
                throw newError(ERR_XML_UNEXPECTED_CHAR);
            }
        } finally {
            decParseDepth();
        }
    }

    void parseComment() {
        if (this.keepComment) {
            String comment = sc.nextUntil("--", false).toString();
            sc.consume("-->");
            if (comment.length() > 0) {
                comment = normalizeComment(comment);
                handler.comment(comment);
            }
        } else {
            sc.skipUntil("--", false);
            sc.consume("-->");
        }
    }

    String normalizeComment(String comment) {
        return StringHelper.normalizeComment(comment, null);
    }

    String intern(String str) {
        if (intern)
            return str.intern();
        return str;
    }

    String parseInstruction() {
        if (sc.tryConsume("<?")) {
            if (StringHelper.isWhitespace((char) sc.cur))
                throw newError(ERR_XML_INVALID_INSTRUCTION);
            String s = sc.nextUntil("?>", false).trim().toString();
            sc.match("?>");
            return s;
        }
        return null;
    }

    void parseCDATA() {
        SourceLocation loc = sc.location();
        String text = sc.nextUntil("]]>", false).toString();
        sc.consume("]]>");
        appendText(loc, text, true);
    }

    String parseDOCTYPE() {
        if (sc.startsWithIgnoreCase("<!d")) {
            sc.next(2);
            sc.consumeIgnoreCase("DOCTYPE ", ERR_XML_UNEXPECTED_CHAR);
            String s = sc.nextUntil('>', false).trim().toString();
            sc.match('>');
            if (StringHelper.startsWithIgnoreCase(s, "html"))
                this.forHtml = true;
            return s;
        }
        return null;
    }

    /**
     * 一直读取到endChar，所有读到的字符按照xml格式要求进行反转义处理。如果匹配返回时，cur指向endChar。
     *
     * @param endChar
     * @return
     */
    String nextXString(char endChar) {
        MutableString buf = sc.useBuf();
        SourceLocation startPos = sc.location();
        do {
            if (sc.isEnd()) {
                throw newError(CommonErrors.ERR_SCAN_NEXT_UNTIL_UNEXPECTED_EOF)
                        .param(CommonErrors.ARG_EXPECTED, endChar).param(ARG_START_LOC, startPos);
            }
            if (sc.cur == endChar) {
                return buf.toString();
            }

            if (loopMode) {
                if (sc.cur == '&') {
                    buf.append(parseEntity());
                } else {
                    sc.consumeToBuf(buf);
                }
            } else if (sc.cur == '<') {
                throw newError(ERR_XML_STRING_NOT_END_PROPERLY).param(CommonErrors.ARG_EXPECTED, endChar)
                        .param(ARG_START_LOC, startPos);
            } else if (sc.cur == '&') {
                buf.append(parseEntity());
            } else {
                sc.consumeToBuf(buf);
            }
        } while (true);
    }

    void parseText() {
        SourceLocation loc = sc.location();
        MutableString buf = sc.useBuf();
        do {
            if (sc.isEnd()) {
                if (forFragments)
                    break;
                throw newError(CommonErrors.ERR_SCAN_NEXT_UNTIL_UNEXPECTED_EOF).param(CommonErrors.ARG_EXPECTED, '<');
            }
            if (sc.cur == '<') {
                break;
            }
            if (sc.cur == '&') {
                buf.append(parseEntity());
            } else {
                sc.consumeToBuf(buf);
            }
        } while (true);

        String text = buf.toString();
        appendText(loc, text, false);
    }

    void appendText(SourceLocation loc, String text, boolean cdata) {
        if (text.isEmpty())
            return;

        if (prevText == null) {
            this.prevLoc = loc;
            this.prevText = text;
            this.prevCDATA = cdata;
        } else {
            if (cdata)
                this.prevCDATA = cdata;
            this.prevText += text;
        }
    }

    void flushText() {
        if (prevText != null) {
            if (!keepWhitespace) {
                if (StringHelper.isBlank(prevText)) {
                    prevText = null;
                    return;
                }
            }
            if (prevCDATA) {
                this.handler.value(prevLoc, new CDataText(prevText));
            } else {
                this.handler.text(prevLoc, prevText);
            }
            this.prevText = null;
            this.prevCDATA = false;
        }
    }

    char parseEntity() {
        sc.next();
        switch (sc.cur) {
            case 'a':
                if (sc.peek() == 'm') {
                    sc.consume("amp;", ERR_XML_PARSE_XML_ENTITY_FAIL);
                    return '&';
                } else {
                    sc.consume("apos;", ERR_XML_PARSE_XML_ENTITY_FAIL);
                    return '\'';
                }
            case 'l':
                sc.consume("lt;", ERR_XML_PARSE_XML_ENTITY_FAIL);
                return '<';
            case 'g':
                sc.consume("gt;", ERR_XML_PARSE_XML_ENTITY_FAIL);
                return '>';
            case 'q':
                sc.consume("quot;", ERR_XML_PARSE_XML_ENTITY_FAIL);
                return '"';
            case '#':
                if (!sc.next())
                    throw newError(ERR_XML_UNEXPECTED_EOF);
                int num;
                if (sc.cur == 'x' || sc.cur == 'X') {
                    sc.next();
                    num = sc.nextHexInt();
                    if (num == 0)
                        throw sc.newError(ERR_XML_PARSE_ESCAPE_CHAR_FAIL);
                    if (sc.cur == ';') {
                        sc.next();
                        return (char) num;
                    }
                    num = num * 16 + (sc.nextHexInt());
                    if (sc.cur == ';') {
                        sc.next();
                        return (char) num;
                    }
                    num = num * 16 + (sc.nextHexInt());
                    if (sc.cur == ';') {
                        sc.next();
                        return (char) num;
                    }
                    num = num * 16 + (sc.nextHexInt());
                    sc.consume(';', ERR_XML_PARSE_ESCAPE_CHAR_FAIL);
                } else {
                    num = sc.nextDigit() - '0';
                    if (num == 0)
                        throw sc.newError(ERR_XML_PARSE_ESCAPE_CHAR_FAIL);
                    for (int i = 0; i < 5; i++) {
                        if (sc.cur == ';') {
                            sc.next();
                            return (char) num;
                        }
                        num = num * 10 + (sc.nextDigit() - '0');
                    }
                    sc.consume(';', ERR_XML_PARSE_ESCAPE_CHAR_FAIL);
                }
                return (char) num;
            default:
                if (loopMode)
                    return '&';

                throw newError(ERR_XML_UNKNOWN_XML_ENTITY);
        }
    }

    Map<String, ValueWithLocation> parseAttrs() {
        Map<String, ValueWithLocation> attrs = Collections.emptyMap();
        if (sc.cur == '/' || sc.cur == '>')
            return attrs;

        attrs = new LinkedHashMap<>();

        do {
            String xname = forHtml ? sc.nextHtmlAttrName() : sc.nextXmlName();
            xname = intern(xname);
            sc.skipBlank();

            if (sc.cur == '=') {
                sc.match('=');
                if (sc.cur == '\'' || sc.cur == '"') {
                    char quote = (char) sc.cur;
                    sc.next();
                    SourceLocation loc = sc.location();
                    String str = nextXString(quote);
                    sc.match(quote);

                    addAttr(attrs, loc, xname, str);
                } else {
                    throw newError(ERR_XML_ATTR_VALUE_NOT_QUOTED).param(ARG_ATTR_NAME, xname);
                }
            } else if (forHtml) {
                // <a disabled > 这种没有写=的情况
                addAttr(attrs, sc.location(), xname, "true");
                sc.skipBlank();
            } else {
                // 非html情况下属性定义必须有=
                sc.match('=');
            }

            // 已经跳过所有空白，此时判断是否属性结束
            if (sc.cur == '/' || sc.cur == '>')
                break;
        } while (true);

        return attrs;
    }

    void addAttr(Map<String, ValueWithLocation> attrs, SourceLocation loc, String name, Object v) {
        ValueWithLocation value = ValueWithLocation.of(loc, v);
        ValueWithLocation oldValue = attrs.put(name, value);
        if (oldValue != null)
            throw newError(ERR_XML_DUPLICATE_ATTR_NAME)
                    .param(ARG_ATTR_NAME, name).param(ARG_ATTR_VALUE, v).param(ARG_OLD_LOC, oldValue.getLocation());
    }

    boolean parseBody() {
        boolean hasChild = false;
        do {
            parseText();
            ParseResult result = parseNode();
            if (result == ParseResult.NODE)
                hasChild = true;
            if (result == ParseResult.END_NODE) {
                flushText();
                return hasChild;
            }
        } while (true);
    }

    void parseFragments() {
        handler.beginNode(null, CoreConstants.DUMMY_TAG_NAME, Collections.emptyMap());
        do {
            parseText();
            if (sc.isEnd())
                break;
            ParseResult result = parseNode();
            if (result == ParseResult.END_NODE)
                return;
            sc.skipBlank();
        } while (!sc.isEnd());
        flushText();
        handler.endNode(CoreConstants.DUMMY_TAG_NAME);
    }

    @Override
    public XNode parseSingleNode(TextScanner sc) {
        this.sc = sc;
        if (this.handler == null)
            this.handler = new CollectXNodeHandler();

        handler.beginNode(null, CoreConstants.DUMMY_TAG_NAME, Collections.emptyMap());
        parseNode();
        handler.endNode(CoreConstants.DUMMY_TAG_NAME);
        XNode node = handler.endDoc();
        if (node.hasChild()) {
            XNode child = node.child(0);
            child.detach();
            return child;
        }
        return null;
    }

    public ParseResult parseSingleNode(TextScanner sc, IXNodeHandler handler) {
        this.sc = sc;
        this.handler = handler;
        return parseNode();
    }
}