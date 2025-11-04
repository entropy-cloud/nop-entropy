/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.yaml;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.IJsonHandler;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.events.CommentEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ImplicitTuple;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import java.io.IOException;
import java.io.Writer;

public class CollectYamlJsonHandler implements IJsonHandler {
    private final Writer out;
    private Emitter emitter;

    public CollectYamlJsonHandler(Writer out) {
        this.out = out;
    }

    Emitter getEmitter() {
        if (emitter == null) {
            DumperOptions options = new DumperOptions();
            emitter = new Emitter(out, options);
        }
        return emitter;
    }

    @Override
    public Object endDoc() {
        return null;
    }

    @Override
    public IJsonHandler comment(String comment) {
        if (comment != null)
            emit(new CommentEvent(CommentType.BLOCK, comment, null, null));
        return this;
    }

    IJsonHandler emit(Event event) {
        try {
            getEmitter().emit(event);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return this;
    }

    @Override
    public IJsonHandler beginObject(SourceLocation loc) {
        return emit(new MappingStartEvent(null, null, false, null, null, FlowStyle.AUTO));
    }

    @Override
    public IJsonHandler endObject() {
        return emit(new MappingEndEvent(null, null));
    }

    @Override
    public IJsonHandler key(String name) {
        return emit(new ScalarEvent(null, null, new ImplicitTuple(true, false), name, null, null, ScalarStyle.PLAIN));
    }

    @Override
    public IJsonHandler value(SourceLocation loc, Object value) {
        String str = StringHelper.toString(value, "");

        return emit(new ScalarEvent(null, null, new ImplicitTuple(true, false), str, null, null, ScalarStyle.PLAIN));
    }

    @Override
    public IJsonHandler beginArray(SourceLocation loc) {
        return emit(new SequenceStartEvent(null, null, false, null, null, FlowStyle.AUTO));
    }

    @Override
    public IJsonHandler endArray() {
        return emit(new SequenceEndEvent(null, null));
    }
}