/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.model;

import io.nop.core.lang.xml.XNode;
import io.nop.task.TaskConstants;
import io.nop.task.model._gen._GraphTaskStepModel;
import io.nop.xlang.xdsl.json.DslModelToXNodeTransformer;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.SchemaLoader;

public class GraphTaskStepModel extends _GraphTaskStepModel implements IGraphTaskStepModel {
    public GraphTaskStepModel() {

    }

    public XNode toNode() {
        IObjMeta objMeta = SchemaLoader.loadXMeta(TaskConstants.XDEF_PATH_TASK);
        IObjSchema graphStep = objMeta.getDefine(GraphTaskStepModel.class.getSimpleName());
        return new DslModelToXNodeTransformer(objMeta).transformObj(graphStep, this);
    }

    @Override
    public String getType() {
        return TaskConstants.STEP_TYPE_GRAPH;
    }

    @Override
    public boolean isConcurrent() {
        return true;
    }

    @Override
    public boolean isGraphMode() {
        return true;
    }
}
