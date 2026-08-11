/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service.designer;

import java.util.List;

/**
 * 工作流设计器(v1)契约常量。
 *
 * 设计契约见 ai-dev/design/nop-wf/workflow-designer-integration.md：
 * - 转换器工作在原始 XNode 层（x:extends 头、未识别属性、xpl 片段原样保留）
 * - 合成节点（start/end/empty）使用保留 id，保存时从 .xwf 剔除
 * - 边记录 <transition>（步骤级/步骤 action 级/被 ref-actions 引用的工作流级 action 级）
 */
public interface WfDesignerConstants {

    /** 保留节点 id：步骤名不得与之冲突，保存时冲突拒绝。 */
    String NODE_START = "__wf_start";
    String NODE_END = "__wf_end";
    String NODE_EMPTY = "__wf_empty";

    /** 设计器节点类型（DesignerConfig.nodeTypes 的 key，与 flow-designer-core/types.ts 对齐）。 */
    String NODE_TYPE_START = "start";
    String NODE_TYPE_END = "end";
    String NODE_TYPE_STEP = "step";
    String NODE_TYPE_SUBWORKFLOW = "subworkflow";

    /** 边类型（DesignerConfig.edgeTypes 的 key）。 */
    String EDGE_TYPE_TO_STEP = "to-step";
    String EDGE_TYPE_TO_END = "to-end";
    String EDGE_TYPE_TO_EMPTY = "to-empty";

    /** GraphDocument.kind。 */
    String DOC_KIND = "workflow";

    /** wf-def-status 字典：PUBLISHED=1（见 nop-wf.orm.xml 生成物 _app.orm.xml，勿手改生成文件）。 */
    int WF_STATUS_PUBLISHED = 1;

    /** 步骤元素<step>属性白名单：设计器可见可编辑。未列属性原样保留。 */
    List<String> STEP_ATTRS = List.of(
            "displayName", "waitSignals", "internal", "optional", "priority", "appState",
            "wfAppState", "bizEntityState", "execGroupType", "tagSet", "allowWithdraw", "allowReject",
            "dueAction", "initAsWaiting", "passWeight", "passPercent", "specialType", "independent");

    /** <transition>属性白名单。 */
    List<String> TRANSITION_ATTRS = List.of(
            "splitType", "onAppStates", "appState", "wfAppState", "bizEntityState", "backLink");

    /** to-step/to-end/to-empty 目标元素属性白名单。 */
    List<String> TARGET_ATTRS = List.of("order", "label");

    /** built-in specialType → 设计器节点类型。未知 specialType 回退为 step，且 data.specialType 原样保留。 */
    List<String> SPECIAL_TYPE_VARIANTS = List.of(
            "approver", "cc", "notify", "route", "condition", "script", "subworkflow");

    /** DesignerConfig.edgeTypes 的 to-step 外观：sourcePort 输出端口 id（与 flux ports 契约对齐）。 */
    String OUTPUT_PORT = "out";

    String INPUT_PORT = "in";
}