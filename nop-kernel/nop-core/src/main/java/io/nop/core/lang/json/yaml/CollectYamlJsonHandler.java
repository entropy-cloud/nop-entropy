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
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * 使用 SnakeYAML 的 Node API 来序列化 YAML，保留 comment。
 * 
 * 注意：直接使用 Emitter API 会遇到 "tag is not specified" 错误，
 * 因为 SnakeYAML 的 Emitter 需要正确设置 tag 和 ImplicitTuple 的组合，
 * 而 chooseScalarStyle() 可能会根据 scalar 内容选择不同的样式，
 * 导致 processTag() 的条件判断复杂且容易出错。
 * 
 * 使用 Node API 更简单可靠。
 */
public class CollectYamlJsonHandler implements IJsonHandler {
    private final Writer out;
    private final DumperOptions options;
    
    // 使用栈来构建 Node 树
    private final Stack<Object> stack = new Stack<>();
    private String pendingComment = null;

    public CollectYamlJsonHandler(Writer out) {
        this.out = out;
        this.options = new DumperOptions();
        this.options.setPrettyFlow(true);
        this.options.setDefaultFlowStyle(FlowStyle.BLOCK);
        this.options.setProcessComments(true);
    }

    @Override
    public void beginDoc(String encoding) {
        // 开始一个新的文档
    }

    @Override
    public Object endDoc() {
        if (stack.isEmpty()) {
            return null;
        }
        
        Object root = stack.pop();
        Node rootNode = toNode(root);
        
        try {
            Yaml yaml = new Yaml(options);
            StringWriter tempWriter = new StringWriter();
            yaml.serialize(rootNode, tempWriter);
            out.write(tempWriter.toString());
            out.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        
        return null;
    }
    
    private Node toNode(Object obj) {
        if (obj instanceof Node) {
            return (Node) obj;
        } else if (obj instanceof MapBuilder) {
            return ((MapBuilder) obj).build();
        } else if (obj instanceof ListBuilder) {
            return ((ListBuilder) obj).build();
        }
        return new ScalarNode(Tag.STR, StringHelper.toString(obj, ""), null, null, ScalarStyle.PLAIN);
    }

    @Override
    public IJsonHandler comment(String comment) {
        this.pendingComment = comment;
        return this;
    }

    @Override
    public IJsonHandler beginObject(SourceLocation loc) {
        MapBuilder builder = new MapBuilder();
        if (pendingComment != null) {
            builder.setComment(pendingComment);
            pendingComment = null;
        }
        stack.push(builder);
        return this;
    }

    @Override
    public IJsonHandler endObject() {
        if (stack.isEmpty() || !(stack.peek() instanceof MapBuilder)) {
            return this;
        }
        
        MapBuilder builder = (MapBuilder) stack.pop();
        MappingNode node = builder.build();
        
        attachNodeToParent(node);
        
        return this;
    }
    
    private void attachNodeToParent(Node node) {
        if (stack.isEmpty()) {
            stack.push(node);
            return;
        }
        
        Object parent = stack.peek();
        if (parent instanceof MapBuilder) {
            ((MapBuilder) parent).setPendingValue(node);
        } else if (parent instanceof ListBuilder) {
            ((ListBuilder) parent).add(node);
        } else {
            stack.push(node);
        }
    }

    @Override
    public IJsonHandler key(String name) {
        if (!stack.isEmpty() && stack.peek() instanceof MapBuilder) {
            ((MapBuilder) stack.peek()).setPendingKey(name);
        }
        return this;
    }

    @Override
    public IJsonHandler value(SourceLocation loc, Object value) {
        Node node = toScalarNode(value);
        attachNodeToParent(node);
        return this;
    }
    
    /**
     * 将值转换为正确类型的 ScalarNode
     */
    private Node toScalarNode(Object value) {
        if (value == null) {
            return new ScalarNode(Tag.NULL, "null", null, null, ScalarStyle.PLAIN);
        } else if (value instanceof Boolean) {
            return new ScalarNode(Tag.BOOL, value.toString(), null, null, ScalarStyle.PLAIN);
        } else if (value instanceof Number) {
            // 数字类型
            return new ScalarNode(Tag.INT, value.toString(), null, null, ScalarStyle.PLAIN);
        } else {
            // 字符串或其他类型
            String str = StringHelper.toString(value, "");
            return new ScalarNode(Tag.STR, str, null, null, ScalarStyle.PLAIN);
        }
    }

    @Override
    public IJsonHandler beginArray(SourceLocation loc) {
        ListBuilder builder = new ListBuilder();
        if (pendingComment != null) {
            builder.setComment(pendingComment);
            pendingComment = null;
        }
        stack.push(builder);
        return this;
    }

    @Override
    public IJsonHandler endArray() {
        if (stack.isEmpty() || !(stack.peek() instanceof ListBuilder)) {
            return this;
        }
        
        ListBuilder builder = (ListBuilder) stack.pop();
        SequenceNode node = builder.build();
        
        attachNodeToParent(node);
        
        return this;
    }
    
    // 辅助类：构建 MappingNode
    private static class MapBuilder {
        private final List<NodeTuple> tuples = new ArrayList<>();
        private String comment;
        private String pendingKey;
        private Node pendingValue;
        
        void setComment(String comment) {
            this.comment = comment;
        }
        
        void setPendingKey(String key) {
            this.pendingKey = key;
        }
        
        void setPendingValue(Node value) {
            this.pendingValue = value;
            flushTuple();
        }
        
        private void flushTuple() {
            if (pendingKey != null && pendingValue != null) {
                ScalarNode keyNode = new ScalarNode(Tag.STR, pendingKey, null, null, ScalarStyle.PLAIN);
                tuples.add(new NodeTuple(keyNode, pendingValue));
                pendingKey = null;
                pendingValue = null;
            }
        }
        
        MappingNode build() {
            flushTuple();
            MappingNode node = new MappingNode(Tag.MAP, tuples, FlowStyle.BLOCK);
            if (comment != null) {
                node.setBlockComments(createCommentLines(comment));
            }
            return node;
        }
    }
    
    // 辅助类：构建 SequenceNode
    private static class ListBuilder {
        private final List<Node> items = new ArrayList<>();
        private String comment;
        
        void setComment(String comment) {
            this.comment = comment;
        }
        
        void add(Node item) {
            items.add(item);
        }
        
        SequenceNode build() {
            SequenceNode node = new SequenceNode(Tag.SEQ, items, FlowStyle.BLOCK);
            if (comment != null) {
                node.setBlockComments(createCommentLines(comment));
            }
            return node;
        }
    }
    
    private static List<CommentLine> createCommentLines(String comment) {
        List<CommentLine> comments = new ArrayList<>();
        if (comment == null) {
            return comments;
        }
        String[] lines = comment.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                trimmed = trimmed.substring(1).trim();
            }
            comments.add(new CommentLine(null, null, trimmed, CommentType.BLOCK));
        }
        return comments;
    }
}
