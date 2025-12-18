/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.core.lang.json.handler;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.RawText;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.utils.NestedProcessingState;

import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import static io.nop.api.core.ApiErrors.ARG_VALUE;
import static io.nop.api.core.util.Guard.notNull;
import static io.nop.core.CoreConfigs.CFG_JSON_MAX_NESTED_LEVEL;
import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ERR_JSON_VALUE_NOT_SERIALIZABLE;
import static io.nop.core.lang.json.handler.JsonScope.DANGLING_NAME;
import static io.nop.core.lang.json.handler.JsonScope.EMPTY_ARRAY;
import static io.nop.core.lang.json.handler.JsonScope.EMPTY_DOCUMENT;
import static io.nop.core.lang.json.handler.JsonScope.EMPTY_OBJECT;
import static io.nop.core.lang.json.handler.JsonScope.NONEMPTY_ARRAY;
import static io.nop.core.lang.json.handler.JsonScope.NONEMPTY_DOCUMENT;
import static io.nop.core.lang.json.handler.JsonScope.NONEMPTY_OBJECT;

public class CollectTextJsonHandler implements IJsonHandler {

    /*
     * From RFC 7159, "All Unicode characters may be placed within the quotation marks except for the characters that
     * must be escaped: quotation mark, reverse solidus, and the control characters (U+0000 through U+001F)."
     *
     * We also escape '\u2028' and '\u2029', which JavaScript interprets as newline characters. This prevents eval()
     * from failing with a syntax error. http://code.google.com/p/google-gson/issues/detail?id=341
     */
    private static final String[] REPLACEMENT_CHARS;
    private static final String[] HTML_SAFE_REPLACEMENT_CHARS;

    static {
        REPLACEMENT_CHARS = new String[128];
        for (int i = 0; i <= 0x1f; i++) {
            REPLACEMENT_CHARS[i] = String.format("\\u%04x", (int) i);
        }
        REPLACEMENT_CHARS['"'] = "\\\"";
        REPLACEMENT_CHARS['\\'] = "\\\\";
        REPLACEMENT_CHARS['\t'] = "\\t";
        REPLACEMENT_CHARS['\b'] = "\\b";
        REPLACEMENT_CHARS['\n'] = "\\n";
        REPLACEMENT_CHARS['\r'] = "\\r";
        REPLACEMENT_CHARS['\f'] = "\\f";
        HTML_SAFE_REPLACEMENT_CHARS = REPLACEMENT_CHARS.clone();
        HTML_SAFE_REPLACEMENT_CHARS['<'] = "\\u003c";
        HTML_SAFE_REPLACEMENT_CHARS['>'] = "\\u003e";
        HTML_SAFE_REPLACEMENT_CHARS['&'] = "\\u0026";
        HTML_SAFE_REPLACEMENT_CHARS['='] = "\\u003d";
        HTML_SAFE_REPLACEMENT_CHARS['\''] = "\\u0027";
    }

    /**
     * The output data, containing at most one top-level array or object.
     */
    private final Appendable out;
    private SourceLocation loc;

    private final NestedProcessingState state = new NestedProcessingState(CFG_JSON_MAX_NESTED_LEVEL.get());

    /**
     * A string containing a full set of spaces for a single level of indentation, or null for no pretty printing.
     */
    private String indent;

    /**
     * The name/value separator; either ":" or ": ".
     */
    private String separator = ":";

    private boolean lenient = true;

    private boolean htmlSafe;

    private String deferredName;

    private boolean serializeNulls = true;

    private String comment;
    private boolean outputComment;
    private boolean dumpLoc;

    /**
     * Creates a new instance that writes a JSON-encoded stream to {@code out}. For best performance, ensure
     * {@link Appendable} is buffered; wrapping in {@link java.io.BufferedWriter BufferedWriter} if necessary.
     */
    public CollectTextJsonHandler(Appendable out) {
        notNull(out, "out");

        this.out = out;
        state.push(EMPTY_DOCUMENT);
    }

    public CollectTextJsonHandler() {
        this(new StringBuilder());
    }

    public Appendable getOut() {
        return out;
    }

    public String getOutString() {
        return out.toString();
    }

    /**
     * Sets the indentation string to be repeated for each level of indentation in the encoded document. If
     * {@code indent.isEmpty()} the encoded document will be compact. Otherwise the encoded document will be more
     * human-readable.
     *
     * @param indent a string containing only whitespace.
     */
    public final void setIndent(String indent) {
        if (indent == null || indent.length() == 0) {
            this.indent = null;
            this.separator = ":";
        } else {
            this.indent = indent;
            this.separator = ": ";
        }
    }

    /**
     * Configure this writer to relax its syntax rules. By default, this writer only emits well-formed JSON as specified
     * by <a href="http://www.ietf.org/rfc/rfc7159.txt">RFC 7159</a>. Setting the writer to lenient permits the
     * following:
     * <ul>
     * <li>Top-level values of any type. With strict writing, the top-level value must be an object or an array.
     * <li>Numbers may be {@link Double#isNaN() NaNs} or {@link Double#isInfinite() infinities}.
     * </ul>
     */
    public final void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    /**
     * Returns true if this writer has relaxed syntax rules.
     */
    public boolean isLenient() {
        return lenient;
    }

    /**
     * Configure this writer to emit JSON that's safe for direct inclusion in HTML and XML documents. This escapes the
     * HTML characters {@code <}, {@code >}, {@code &} and {@code =} before writing them to the stream. Without this
     * setting, your XML/HTML encoder should replace these characters with the corresponding escape sequences.
     */
    public final void setHtmlSafe(boolean htmlSafe) {
        this.htmlSafe = htmlSafe;
    }

    /**
     * Returns true if this writer writes JSON that's safe for inclusion in HTML and XML documents.
     */
    public final boolean isHtmlSafe() {
        return htmlSafe;
    }

    /**
     * Sets whether object members are serialized when their value is null. This has no impact on array elements. The
     * default is true.
     */
    public final void setSerializeNulls(boolean serializeNulls) {
        this.serializeNulls = serializeNulls;
    }

    /**
     * Returns true if object members are serialized when their value is null. This has no impact on array elements. The
     * default is true.
     */
    public final boolean getSerializeNulls() {
        return serializeNulls;
    }

    public boolean isOutputComment() {
        return outputComment;
    }

    public void setOutputComment(boolean outputComment) {
        this.outputComment = outputComment;
    }

    public void setDumpLoc(boolean dumpLoc) {
        this.dumpLoc = dumpLoc;
    }

    public CollectTextJsonHandler outputComment(boolean outputComment) {
        this.setOutputComment(outputComment);
        return this;
    }

    public CollectTextJsonHandler dumpLoc(boolean dumpLoc) {
        this.setDumpLoc(dumpLoc);
        return this;
    }

    public CollectTextJsonHandler lenient(boolean lenient) {
        this.setLenient(lenient);
        return this;
    }

    public CollectTextJsonHandler htmlSafe(boolean htmlSafe) {
        this.setHtmlSafe(htmlSafe);
        return this;
    }

    public CollectTextJsonHandler indent(String indent) {
        this.setIndent(indent);
        return this;
    }

    public CollectTextJsonHandler serializeNulls(boolean serializeNulls) {
        this.setSerializeNulls(serializeNulls);
        return this;
    }

    /**
     * Begins encoding a new array. Each call to this method must be paired with a call to
     * {@link IJsonHandler#endArray}.
     *
     * @return this writer.
     */
    public IJsonHandler beginArray(SourceLocation loc) {
        try {
            writeDeferredName(loc);
            return open(loc, EMPTY_ARRAY, "[");
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * Ends encoding the current array.
     *
     * @return this writer.
     */
    public IJsonHandler endArray() {
        try {
            return close(EMPTY_ARRAY, NONEMPTY_ARRAY, "]");
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * Begins encoding a new object. Each call to this method must be paired with a call to
     * {@link IJsonHandler#endObject}.
     *
     * @return this writer.
     */
    public IJsonHandler beginObject(SourceLocation loc) {
        try {
            writeDeferredName(loc);
            return open(loc, EMPTY_OBJECT, "{");
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * Ends encoding the current object.
     *
     * @return this writer.
     */
    public IJsonHandler endObject() {
        try {
            return close(EMPTY_OBJECT, NONEMPTY_OBJECT, "}");
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * Enters a new scope by appending any necessary whitespace and the given bracket.
     */
    private IJsonHandler open(SourceLocation loc, int empty, String openBracket) throws IOException {
        beforeValue(loc);
        state.push(empty);
        out.append(openBracket);
        return this;
    }

    /**
     * Closes the current scope by appending any necessary whitespace and the given bracket.
     */
    private IJsonHandler close(int empty, int nonempty, String closeBracket) throws IOException {
        int context = state.peek();
        if (context != nonempty && context != empty) {
            throw new IllegalStateException("Nesting problem.");
        }
        if (deferredName != null) {
            throw new IllegalStateException("Dangling name: " + deferredName);
        }

        state.pop();
        if (context == nonempty) {
            newline(null);
        }
        out.append(closeBracket);
        return this;
    }

    /**
     * Encodes the property name.
     *
     * @param name the name of the forthcoming value. May not be null.
     * @return this writer.
     */
    public IJsonHandler key(String name) {
        if (name == null) {
            name = "null";
        }
        if (deferredName != null) {
            throw new IllegalStateException("Dangling name: " + deferredName);
        }
        if (state.isEmpty()) {
            throw new IllegalStateException("JsonWriter is closed.");
        }
        deferredName = name;
        return this;
    }

    private void writeDeferredName(SourceLocation loc) throws IOException {
        if (deferredName != null) {
            beforeName(loc);
            string(deferredName);
            deferredName = null;
        }
    }

    /**
     * Encodes {@code value}.
     *
     * @param value the literal string value, or null to encode a null literal.
     * @return this writer.
     */
    public IJsonHandler value(SourceLocation loc, Object value) {
        try {
            if (value == null) {
                return nullValue(loc);
            }
            writeDeferredName(loc);
            beforeValue(loc);
            writeValue(value);
            return this;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public IJsonHandler stringValue(SourceLocation loc, String value) {
        try {
            if (value == null) {
                return nullValue(loc);
            }
            writeDeferredName(loc);
            beforeValue(loc);
            string(value);
            return this;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public IJsonHandler numberValue(SourceLocation loc, Number value) {
        try {
            if (value == null) {
                return nullValue(loc);
            }
            writeDeferredName(loc);
            beforeValue(loc);
            out.append(value.toString());
            return this;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public IJsonHandler booleanValue(SourceLocation loc, Boolean value) {
        try {
            if (value == null) {
                return nullValue(loc);
            }
            writeDeferredName(loc);
            beforeValue(loc);
            out.append(value.toString());
            return this;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    void writeValue(Object o) throws IOException {
        // IJsonSeiralizable是bean层面的接口，由JsonSerializer负责处理
        if (o instanceof String || o instanceof Character) {
            string(o.toString());
        } else if (o instanceof Map) {
            writeMap((Map) o);
        } else if (o instanceof Collection) {
            writeCollection((Collection) o);
        } else if (o.getClass().isArray()) {
            int len = Array.getLength(o);
            beginArray(null);
            for (int i = 0; i < len; i++) {
                Object value = Array.get(o, i);
                value(null, value);
            }
            endArray();
        } else if (o instanceof RawText) {
            out.append(((RawText) o).getText());
        } else if (o instanceof Number || o instanceof Boolean) {
            out.append(o.toString());
        } else {
            throw new NopException(ERR_JSON_VALUE_NOT_SERIALIZABLE).param(ARG_CLASS_NAME, o.getClass().getTypeName())
                    .param(ARG_VALUE, o);
        }
    }

    void writeMap(Map<?, ?> map) {
        beginObject(null);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            key((String) entry.getKey());
            value(null, entry.getValue());
        }
        endObject();
    }

    void writeCollection(Collection<?> c) {
        beginArray(null);
        for (Object o : c) {
            value(null, o);
        }
        endArray();
    }

    /**
     * Encodes {@code null}.
     *
     * @return this writer.
     */
    IJsonHandler nullValue(SourceLocation loc) throws IOException {
        if (deferredName != null) {
            if (serializeNulls) {
                writeDeferredName(loc);
            } else {
                deferredName = null;
                return this; // skip the name and the value
            }
        }
        beforeValue(loc);
        out.append("null");
        return this;
    }

    /**
     * Ensures all buffered data is written to the underlying {@link Writer} and flushes that writer.
     */
    public void flush() throws IOException {
        if (state.isEmpty()) {
            throw new IllegalStateException("JsonWriter is closed.");
        }
        if (out instanceof Flushable) {
            ((Flushable) out).flush();
        }
    }

    /**
     * Flushes and closes this writer
     */
    public Object endDoc() {
        state.complete(NONEMPTY_DOCUMENT);
        return null;
    }

    private void string(String value) throws IOException {
        String[] replacements = htmlSafe ? HTML_SAFE_REPLACEMENT_CHARS : REPLACEMENT_CHARS;
        out.append("\"");
        int last = 0;
        int length = value.length();
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            String replacement;
            if (c < 128) {
                replacement = replacements[c];
                if (replacement == null) {
                    continue;
                }
            } else if (c == '\u2028') {
                replacement = "\\u2028";
            } else if (c == '\u2029') {
                replacement = "\\u2029";
            } else {
                continue;
            }
            if (last < i) {
                out.append(value, last, i);
            }
            out.append(replacement);
            last = i + 1;
        }
        if (last < length) {
            out.append(value, last, length);
        }
        out.append("\"");
    }

    private void newline(SourceLocation loc) throws IOException {
        if (indent == null) {
            return;
        }

        out.append("\n");

        if (dumpLoc) {
            if (loc != null && !Objects.equals(this.loc, loc)) {
                this.loc = loc;
                out.append("/*LOC:").append(loc.toString()).append("*/");
            }
        }

        if (outputComment && !StringHelper.isEmpty(comment)) {
            out.append("/* ").append(StringHelper.replace(comment, "*/", "* /")).append("*/\n");
            this.comment = null;
        }

        for (int i = 1, size = state.size(); i < size; i++) {
            out.append(indent);
        }
    }

    /**
     * Inserts any necessary separators and whitespace before a name. Also adjusts the stack to expect the name's value.
     */
    private void beforeName(SourceLocation loc) throws IOException {
        int context = state.peek();
        if (context == NONEMPTY_OBJECT) { // first in object
            out.append(',');
        } else if (context != EMPTY_OBJECT) { // not in an object!
            throw new IllegalStateException("Nesting problem. not in an object");
        }
        newline(loc);
        state.replaceTop(DANGLING_NAME);
    }

    /**
     * Inserts any necessary separators and whitespace before a literal value, inline array, or inline object. Also
     * adjusts the stack to expect either a closing bracket or another element.
     */
    @SuppressWarnings("fallthrough")
    private void beforeValue(SourceLocation loc) throws IOException {
        switch (state.peek()) {
            case NONEMPTY_DOCUMENT:
                if (!lenient) {
                    throw new IllegalStateException("JSON must have only one top-level value.");
                }
                // fall-through
            case EMPTY_DOCUMENT: // first in document
                state.replaceTop(NONEMPTY_DOCUMENT);
                break;

            case EMPTY_ARRAY: // first in array
                state.replaceTop(NONEMPTY_ARRAY);
                newline(loc);
                break;

            case NONEMPTY_ARRAY: // another in array
                out.append(',');
                newline(loc);
                break;

            case DANGLING_NAME: // value for name
                out.append(separator);
                state.replaceTop(NONEMPTY_OBJECT);
                break;

            default:
                throw new IllegalStateException("Nesting problem.");
        }
    }

    public IJsonHandler comment(String comment) {
        this.comment = comment;
        return this;
    }

}