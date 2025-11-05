/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.mermaid;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface MermaidErrors {
    // ======================= 参数定义 =======================
    String ARG_OP = "op";
    String ARG_NODE_TYPE = "nodeType";
    String ARG_DIAGRAM_TYPE = "diagramType";
    String ARG_SHAPE = "shape";
    String ARG_DIRECTION = "direction";
    String ARG_EDGE_TYPE = "edgeType";
    String ARG_ID = "id";
    String ARG_NAME = "name";
    String ARG_TEXT = "text";
    String ARG_LABEL = "label";
    String ARG_VALUE = "value";

    // ======================= 错误码定义 =======================
    ErrorCode ERR_MERMAID_UNSUPPORTED_OP = define("nop.err.mermaid.unsupported-op",
            "不支持的Mermaid操作符:{op}", ARG_OP);

    ErrorCode ERR_MERMAID_INVALID_NODE_TYPE = define("nop.err.mermaid.invalid-node-type",
            "不支持的Mermaid节点类型:{nodeType}", ARG_NODE_TYPE);

    ErrorCode ERR_MERMAID_INVALID_DIAGRAM_TYPE = define("nop.err.mermaid.invalid-diagram-type",
            "不支持的Mermaid图表类型:{diagramType}", ARG_DIAGRAM_TYPE);

    ErrorCode ERR_MERMAID_INVALID_SHAPE = define("nop.err.mermaid.invalid-shape",
            "不支持的Mermaid节点形状:{shape}", ARG_SHAPE);

    ErrorCode ERR_MERMAID_INVALID_DIRECTION = define("nop.err.mermaid.invalid-direction",
            "不支持的Mermaid方向:{direction}", ARG_DIRECTION);

    ErrorCode ERR_MERMAID_INVALID_EDGE_TYPE = define("nop.err.mermaid.invalid-edge-type",
            "不支持的Mermaid边类型:{edgeType}", ARG_EDGE_TYPE);

    ErrorCode ERR_MERMAID_MISSING_ID = define("nop.err.mermaid.missing-id",
            "Mermaid节点缺少必需的ID属性", ARG_ID);

    ErrorCode ERR_MERMAID_DUPLICATE_ID = define("nop.err.mermaid.duplicate-id",
            "Mermaid节点ID重复:{id}", ARG_ID);

    ErrorCode ERR_MERMAID_MISSING_NAME = define("nop.err.mermaid.missing-name",
            "Mermaid节点缺少必需的名称属性", ARG_NAME);

    ErrorCode ERR_MERMAID_MISSING_TEXT = define("nop.err.mermaid.missing-text",
            "Mermaid节点缺少必需的文本内容", ARG_TEXT);

    ErrorCode ERR_MERMAID_MISSING_LABEL = define("nop.err.mermaid.missing-label",
            "Mermaid节点缺少必需的标签", ARG_LABEL);

    ErrorCode ERR_MERMAID_MISSING_VALUE = define("nop.err.mermaid.missing-value",
            "Mermaid节点缺少必需的值", ARG_VALUE);

    ErrorCode ERR_MERMAID_INVALID_SYNTAX = define("nop.err.mermaid.invalid-syntax",
            "Mermaid语法错误:{message}", "message");

    ErrorCode ERR_MERMAID_UNEXPECTED_TOKEN = define("nop.err.mermaid.unexpected-token",
            "意外的Mermaid语法标记:{token}", "token");

    ErrorCode ERR_MERMAID_MISSING_REQUIRED_FIELD = define("nop.err.mermaid.missing-required-field",
            "Mermaid节点缺少必需的字段:{field}", "field");
}