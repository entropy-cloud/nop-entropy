/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
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
    private String pendingComment = null;  // 待附加到下一个嵌套对象的注释
    private String rootCommentContent = null;  // 根注释内容（用于跳过第一个 key 的注释）

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
        // 提取文档开头的注释（将附加到根对象）
        if (keepComment) {
            rootCommentContent = extractLeadingComments(text);
        }

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setProcessComments(keepComment);
        Yaml yaml = new Yaml(loaderOptions);
        Node node = yaml.compose(new StringReader(text));
        if (node == null)
            return null;
        if (handler == null)
            handler = new BuildJObjectJsonHandler();

        // 在 beginDoc 之前发送根注释，这样它会被附加到第一个创建的对象（根对象）
        if (rootCommentContent != null && !rootCommentContent.isEmpty()) {
            handler.comment(rootCommentContent);
        }

        handler.beginDoc(StringHelper.ENCODING_UTF8);
        transformNode(node, true);
        return handler.endDoc();
    }

    /**
     * 从 YAML 文本开头提取 comment
     * 支持以 # 开头的行，直到遇到非 comment 行
     */
    private String extractLeadingComments(String text) {
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                // 去掉 # 和后面的空格
                String commentText = trimmed.substring(1).trim();
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(commentText);
            } else if (trimmed.isEmpty()) {
                // 空行，继续
                continue;
            } else {
                // 非空行且不是 comment，停止
                break;
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 递归转换 Node 到 JSON 对象
     * @param node 要转换的节点
     * @param isRoot 是否是根节点
     */
    void transformNode(Node node, boolean isRoot) {
        SourceLocation loc = buildLoc(node);

        // 如果有待处理的注释，先发送
        if (pendingComment != null) {
            handler.comment(pendingComment);
            pendingComment = null;
        }

        if (node.getNodeId() == NodeId.scalar) {
            ScalarNode scalarNode = (ScalarNode) node;
            String value = scalarNode.getValue();
            handler.value(loc, castType(loc, node.getTag(), value));
        } else if (node.getNodeId() == NodeId.sequence) {
            SequenceNode seq = (SequenceNode) node;
            List<Node> children = seq.getValue();
            handler.beginArray(loc);
            for (int i = 0; i < children.size(); i++) {
                Node child = children.get(i);
                // 处理数组元素的注释
                if (keepComment && child.getBlockComments() != null && !child.getBlockComments().isEmpty()) {
                    String commentText = extractComments(child.getBlockComments());
                    if (!commentText.isEmpty()) {
                        handler.comment(commentText);
                    }
                }
                transformNode(child, false);
            }
            handler.endArray();
        } else if (node.getNodeId() == NodeId.mapping) {
            MappingNode map = (MappingNode) node;
            List<NodeTuple> tuples = map.getValue();
            handler.beginObject(loc);

            for (int i = 0; i < tuples.size(); i++) {
                NodeTuple tuple = tuples.get(i);
                Node keyNode = tuple.getKeyNode();
                Node valueNode = tuple.getValueNode();
                String key = getKey(keyNode);
                if (intern)
                    key = key.intern();

                // 处理 key 前面的注释
                if (keepComment && keyNode.getBlockComments() != null && !keyNode.getBlockComments().isEmpty()) {
                    String commentText = extractComments(keyNode.getBlockComments());
                    // 检查是否是根注释（通过比较内容，忽略空格差异）
                    boolean isRootComment = isRoot && i == 0 && rootCommentContent != null 
                            && normalizeComment(commentText).equals(normalizeComment(rootCommentContent));
                    
                    if (!isRootComment && !commentText.isEmpty()) {
                        // 只对嵌套对象/数组，key 前的注释附加到 value 上
                        if (valueNode.getNodeId() == NodeId.mapping || valueNode.getNodeId() == NodeId.sequence) {
                            pendingComment = commentText;
                        }
                        // 对于标量值，注释暂时忽略
                    }
                }

                handler.key(key);
                transformNode(valueNode, false);
            }
            handler.endObject();
        }
    }

    /**
     * 标准化注释内容（去除多余空格、换行等）
     */
    private String normalizeComment(String comment) {
        if (comment == null)
            return "";
        return comment.trim().replaceAll("\\s+", " ");
    }

    /**
     * 从 CommentLine 列表中提取注释文本
     * 跳过 BLANK_LINE 类型（空行）和空内容
     */
    private String extractComments(List<CommentLine> comments) {
        StringBuilder sb = new StringBuilder();
        for (CommentLine comment : comments) {
            // 跳过空行（BLANK_LINE 类型）
            if (comment.getCommentType() == CommentType.BLANK_LINE) {
                continue;
            }
            String value = comment.getValue();
            // 跳过空内容
            if (value != null && !value.trim().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(value.trim());
            }
        }
        return sb.toString();
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