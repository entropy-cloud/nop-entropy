/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.handler;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.json.IJsonHandler;

public class DelegateJsonHandler implements IJsonHandler {
    private final IJsonHandler handler;

    public DelegateJsonHandler(IJsonHandler handler) {
        this.handler = handler;
    }

    public IJsonHandler getHandler() {
        return handler;
    }

    @Override
    public Object endDoc() {
        return handler.endDoc();
    }

    @Override
    public IJsonHandler comment(String comment) {
        handler.comment(comment);
        return this;
    }

    @Override
    public IJsonHandler endObject() {
        handler.endObject();
        return this;
    }

    @Override
    public IJsonHandler key(String name) {
        handler.key(name);
        return this;
    }

    @Override
    public IJsonHandler value(SourceLocation loc, Object value) {
        handler.value(loc, value);
        return this;
    }

    @Override
    public IJsonHandler beginArray(SourceLocation loc) {
        handler.beginArray(loc);
        return this;
    }

    @Override
    public IJsonHandler endArray() {
        handler.endArray();
        return this;
    }

    @Override
    public void beginDoc(String encoding) {
        handler.beginDoc(encoding);
    }

    @Override
    public IJsonHandler beginObject(SourceLocation loc) {
        handler.beginObject(loc);
        return this;
    }

    @Override
    public IJsonHandler stringValue(SourceLocation loc, String value) {
        handler.stringValue(loc, value);
        return this;
    }

    @Override
    public IJsonHandler numberValue(SourceLocation loc, Number value) {
        handler.numberValue(loc, value);
        return this;
    }

    @Override
    public IJsonHandler booleanValue(SourceLocation loc, Boolean b) {
        handler.booleanValue(loc, b);
        return this;
    }

    @Override
    public IJsonHandler rawValue(SourceLocation loc, Object value) {
        handler.rawValue(loc, value);
        return this;
    }
}