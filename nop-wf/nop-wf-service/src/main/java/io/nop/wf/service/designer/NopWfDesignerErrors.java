/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service.designer;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * 工作流设计器错误码。消息语言遵循 nop-wf 模块既有约定（NopWfErrors / NopWfCoreErrors）。
 */
public interface NopWfDesignerErrors {
    String ARG_WF_DEF_ID = "wfDefId";
    String ARG_STEP_NAME = "stepName";
    String ARG_NODE_ID = "nodeId";
    String ARG_DETAIL = "detail";

    ErrorCode ERR_WF_DESIGNER_UNKNOWN_DEFINITION = define("nop.err.wf.designer-unknown-definition",
            "工作流定义[{wfDefId}]不存在", ARG_WF_DEF_ID);

    ErrorCode ERR_WF_DESIGNER_DEFINITION_PUBLISHED = define("nop.err.wf.designer-definition-published",
            "工作流定义[{wfDefId}]已发布，不允许在设计器中编辑", ARG_WF_DEF_ID);

    ErrorCode ERR_WF_DESIGNER_INVALID_DOCUMENT = define("nop.err.wf.designer-invalid-document",
            "设计器文档JSON格式非法: {detail}", ARG_DETAIL);

    ErrorCode ERR_WF_DESIGNER_EMPTY_DOCUMENT = define("nop.err.wf.designer-empty-document",
            "设计图中至少需要一个步骤节点");

    ErrorCode ERR_WF_DESIGNER_DUPLICATE_STEP_NAME = define("nop.err.wf.designer-duplicate-step-name",
            "设计图中存在重复的步骤节点名称[{stepName}]", ARG_STEP_NAME);

    ErrorCode ERR_WF_DESIGNER_RESERVED_STEP_NAME = define("nop.err.wf.designer-reserved-step-name",
            "步骤节点名称[{stepName}]与设计器保留id冲突，请更换名称", ARG_STEP_NAME);

    ErrorCode ERR_WF_DESIGNER_UNKNOWN_NODE = define("nop.err.wf.designer-unknown-node",
            "设计图中的边引用了不存在的节点[{nodeId}]", ARG_NODE_ID);

    ErrorCode ERR_WF_DESIGNER_START_EDGE_INVALID = define("nop.err.wf.designer-start-edge-invalid",
            "起始节点只允许连接到步骤节点，不能连接到[{nodeId}]", ARG_NODE_ID);

    ErrorCode ERR_WF_DESIGNER_MODEL_PARSE_FAILED = define("nop.err.wf.designer-model-parse-failed",
            "工作流定义[{wfDefId}]的模型文本解析失败: {detail}", ARG_WF_DEF_ID, ARG_DETAIL);

    ErrorCode ERR_WF_DESIGNER_MODEL_INVALID = define("nop.err.wf.designer-model-invalid",
            "工作流设计器保存校验未通过[{wfDefId}]: {detail}", ARG_WF_DEF_ID, ARG_DETAIL);
}