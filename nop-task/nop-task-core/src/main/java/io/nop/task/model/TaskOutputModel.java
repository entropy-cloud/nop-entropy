/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.model;

import io.nop.core.lang.xml.XNode;
import io.nop.task.model._gen._TaskOutputModel;
import io.nop.xlang.xmeta.ISchema;

import java.util.HashMap;

public class TaskOutputModel extends _TaskOutputModel implements ITaskOutputModel {
    public TaskOutputModel() {

    }

    public XNode getSchemaNode() {
        ISchema schema = getSchema();
        if (schema == null)
            return null;
        return schema.toNode(new HashMap<>());
    }
}
