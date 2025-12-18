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
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.IJsonSerializable;
import io.nop.core.lang.utils.NestedProcessingState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreConfigs.CFG_JSON_MAX_NESTED_LEVEL;
import static io.nop.core.CoreErrors.ARG_KEY;
import static io.nop.core.CoreErrors.ERR_JSON_DUPLICATE_KEY;
import static io.nop.core.CoreErrors.ERR_JSON_SERIALIZE_STATE_FAIL;
import static io.nop.core.lang.json.handler.JsonScope.DANGLING_NAME;
import static io.nop.core.lang.json.handler.JsonScope.EMPTY_ARRAY;
import static io.nop.core.lang.json.handler.JsonScope.EMPTY_DOCUMENT;
import static io.nop.core.lang.json.handler.JsonScope.EMPTY_OBJECT;
import static io.nop.core.lang.json.handler.JsonScope.NONEMPTY_ARRAY;
import static io.nop.core.lang.json.handler.JsonScope.NONEMPTY_DOCUMENT;
import static io.nop.core.lang.json.handler.JsonScope.NONEMPTY_OBJECT;

public class BuildObjectJsonHandler implements IJsonHandler {

    private final NestedProcessingState state = new NestedProcessingState(CFG_JSON_MAX_NESTED_LEVEL.get());

    private String deferredName;

    private List<Object> values = new ArrayList<>();
    private Object rootObj;
    private String comment;

    public BuildObjectJsonHandler() {
        state.push(EMPTY_DOCUMENT);
    }

    public Object getResult() {
        return rootObj;
    }

    protected String getComment() {
        return comment;
    }

    protected String getDeferredName() {
        return deferredName;
    }

    protected List<Object> getValues() {
        return values;
    }

    /**
     * Begins encoding a new array. Each call to this method must be paired with a call to
     * {@link IJsonHandler#endArray}.
     *
     * @return this writer.
     */
    public IJsonHandler beginArray(SourceLocation loc) {
        List<Object> array = newArray(loc);
        addValue(loc, array);
        return open(EMPTY_ARRAY, array);
    }

    protected List<Object> newArray(SourceLocation loc) {
        return new ArrayList<>();
    }

    protected void addValue(SourceLocation loc, Object value) {
        int context = state.peek();

        if (deferredName != null) {
            if (context != DANGLING_NAME)
                throw new NopException(ERR_JSON_SERIALIZE_STATE_FAIL).loc(loc).param(ARG_KEY, deferredName);
            addEntry(deferredName, loc, value);
            deferredName = null;
        } else if (context == EMPTY_DOCUMENT) {
            this.rootObj = value;
        } else if (context == EMPTY_ARRAY || context == NONEMPTY_ARRAY) {
            addToList(getTopList(), loc, value);
        } else {
            throw new NopException(ERR_JSON_SERIALIZE_STATE_FAIL).loc(loc);
        }
    }

    protected void addToList(List<Object> list, SourceLocation loc, Object value) {
        list.add(value);
    }

    protected void addEntry(String key, SourceLocation loc, Object value) {
        Map<String, Object> map = getTopMap();
        if (map.containsKey(deferredName))
            throw new NopException(ERR_JSON_DUPLICATE_KEY).loc(loc).param(ARG_KEY, deferredName);
        addToMap(map, loc, deferredName, value);
    }

    protected void addToMap(Map<String, Object> map, SourceLocation loc, String key, Object value) {
        map.put(key, value);
    }

    protected Object getTopObject() {
        return values.get(values.size() - 1);
    }

    Map<String, Object> getTopMap() {
        return (Map<String, Object>) this.values.get(this.values.size() - 1);
    }

    List<Object> getTopList() {
        return (List<Object>) values.get(values.size() - 1);
    }

    /**
     * Ends encoding the current array.
     *
     * @return this writer.
     */
    public IJsonHandler endArray() {
        return close(EMPTY_ARRAY, NONEMPTY_ARRAY);
    }

    /**
     * Begins encoding a new object. Each call to this method must be paired with a call to
     * {@link IJsonHandler#endObject}.
     *
     * @return this writer.
     */
    public IJsonHandler beginObject(SourceLocation loc) {
        Object obj = newObject(loc);
        addValue(loc, obj);
        return open(EMPTY_OBJECT, obj);
    }

    protected Object newObject(SourceLocation loc) {
        return new LinkedHashMap<>();
    }

    /**
     * Ends encoding the current object.
     *
     * @return this writer.
     */
    public IJsonHandler endObject() {
        return close(EMPTY_OBJECT, NONEMPTY_OBJECT);
    }

    /**
     * Enters a new scope by appending any necessary whitespace and the given bracket.
     */
    private IJsonHandler open(int empty, Object value) {
        beforeValue();
        state.push(empty);
        values.add(value);
        return this;
    }

    /**
     * Closes the current scope by appending any necessary whitespace and the given bracket.
     */
    private IJsonHandler close(int empty, int nonempty) {
        int context = state.peek();
        if (context != nonempty && context != empty) {
            throw new IllegalStateException("Nesting problem.");
        }
        if (deferredName != null) {
            throw new IllegalStateException("Dangling name: " + deferredName);
        }

        state.pop();
        values.remove(values.size() - 1);
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
        beforeName();
        return this;
    }

    /**
     * Inserts any necessary separators and whitespace before a name. Also adjusts the stack to expect the name's value.
     */
    private void beforeName() {
        int context = state.peek();
        if (context == NONEMPTY_OBJECT) { //NOPMD - suppressed EmptyControlStatement
            // first in object
            // ignore
        } else if (context != EMPTY_OBJECT) { // not in an object!
            throw new IllegalStateException("Nesting problem. not in an object");
        }
        state.replaceTop(DANGLING_NAME);
    }

    /**
     * Encodes {@code value}.
     *
     * @param value the literal string value, or null to encode a null literal.
     * @return this writer.
     */
    public IJsonHandler value(SourceLocation loc, Object value) {
        if (value instanceof IJsonSerializable) {
            ((IJsonSerializable) value).serializeToJson(this);
        } else if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            this.beginObject(SourceLocation.getLocation(value));
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String k = entry.getKey();
                Object v = entry.getValue();
                put(k, v);
            }
            this.endObject();
        } else if (value instanceof Collection) {
            Collection<?> c = (Collection<?>) value;
            this.beginArray(SourceLocation.getLocation(value));
            for (Object v : c) {
                value(SourceLocation.getLocation(v), v);
            }
            this.endArray();
        } else {
            addValue(loc, value);
            beforeValue();
        }
        return this;
    }

    /**
     * Flushes and closes this writer
     *
     * @if the JSON document is incomplete.
     */
    public Object endDoc() {
        state.complete(NONEMPTY_DOCUMENT);
        return rootObj;
    }

    /**
     * Inserts any necessary separators and whitespace before a literal value, inline array, or inline object. Also
     * adjusts the stack to expect either a closing bracket or another element.
     */
    @SuppressWarnings("fallthrough")
    private void beforeValue() {
        switch (state.peek()) {
            case NONEMPTY_DOCUMENT:
                throw new IllegalStateException("JSON must have only one top-level value.");
                // fall-through
            case EMPTY_DOCUMENT: // first in document
                state.replaceTop(NONEMPTY_DOCUMENT);
                break;

            case EMPTY_ARRAY: // first in array
                state.replaceTop(NONEMPTY_ARRAY);
                break;

            case NONEMPTY_ARRAY: // another in array
                break;

            case DANGLING_NAME: // value for name
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