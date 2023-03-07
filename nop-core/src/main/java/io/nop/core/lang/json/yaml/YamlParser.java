/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.yaml;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JsonParseOptions;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.handler.BuildJObjectJsonHandler;
import io.nop.core.lang.json.parse.IJsonParser;
import io.nop.core.resource.component.parse.AbstractTextResourceParser;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.StringReader;
import java.util.List;

import static io.nop.api.core.convert.ConvertHelper.toBoolean;
import static io.nop.api.core.convert.ConvertHelper.toDouble;
import static io.nop.api.core.convert.ConvertHelper.toNumber;

public class YamlParser extends AbstractTextResourceParser<Object> implements IJsonParser {
    private IJsonHandler handler;
    private boolean keepComment = true;
    private boolean intern = false;

    public YamlParser intern(boolean intern) {
        this.intern = intern;
        return this;
    }

    public YamlParser handler(IJsonHandler handler) {
        this.handler = handler;
        return this;
    }

    public YamlParser defaultEncoding(String encoding) {
        this.setEncoding(encoding);
        return this;
    }

    public YamlParser keepComment(boolean keepComment) {
        this.keepComment = keepComment;
        return this;
    }

    public YamlParser config(JsonParseOptions options) {
        if (options != null) {
            this.defaultEncoding(options.getDefaultEncoding()).intern(options.isIntern())
                    .keepComment(options.isKeepComment()).shouldTraceDepends(options.isTraceDepends());
            if (options.isKeepComment() || options.isKeepLocation()) {
                this.handler(new BuildJObjectJsonHandler());
            }
        }
        return this;
    }

    @Override
    protected Object doParseText(SourceLocation loc, String text) {
        Yaml yaml = new Yaml();
        Node node = yaml.compose(new StringReader(text));
        if (node == null)
            return null;
        if (handler == null)
            handler = new BuildJObjectJsonHandler();
        handler.beginDoc(StringHelper.ENCODING_UTF8);
        transformNode(node);
        return handler.endDoc();
    }

    void transformNode(Node node) {
        SourceLocation loc = buildLoc(node);
        if (node.getNodeId() == NodeId.scalar) {
            String value = ((ScalarNode) node).getValue();
            if (node.getTag() == Tag.COMMENT) {
                if (keepComment)
                    handler.comment(value);
            } else {
                handler.value(loc, castType(loc, node.getTag(), value));
            }
        } else if (node.getNodeId() == NodeId.sequence) {
            SequenceNode seq = (SequenceNode) node;
            List<Node> children = seq.getValue();
            handler.beginArray(loc);
            for (Node child : children) {
                transformNode(child);
            }
            handler.endArray();
        } else if (node.getNodeId() == NodeId.mapping) {
            MappingNode map = (MappingNode) node;
            List<NodeTuple> tuples = map.getValue();
            handler.beginObject(loc);
            for (NodeTuple tuple : tuples) {
                String key = getKey(tuple.getKeyNode());
                if (intern)
                    key = key.intern();
                handler.key(key);
                transformNode(tuple.getValueNode());
            }
            handler.endObject();
        }
    }

    String getKey(Node node) {
        return ((ScalarNode) node).getValue();
    }

    Object castType(SourceLocation loc, Tag tag, String value) {
        if (tag == Tag.BOOL) {
            return toBoolean(value, err -> new NopException(err).loc(loc));
        } else if (tag == Tag.INT) {
            return toNumber(value, err -> new NopException(err).loc(loc));
        } else if (tag == Tag.FLOAT) {
            return toDouble(value, err -> new NopException(err).loc(loc));
        } else if (tag == Tag.NULL) {
            return null;
        }
        return value;
    }

    SourceLocation buildLoc(Node node) {
        if (node.getStartMark() == null)
            return null;
        int line = node.getStartMark().getLine();
        int col = node.getStartMark().getColumn();
        String path = getResourcePath();
        if (path == null)
            path = "text";
        return SourceLocation.fromLine(path, line, col);
    }
}