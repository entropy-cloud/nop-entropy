/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.model;

import io.nop.core.CoreConstants;
import io.nop.core.lang.eval.FixedValueEvalAction;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.task.model._gen._TaskInputModel;
import io.nop.xlang.xmeta.ISchema;

import java.util.HashMap;

public class TaskInputModel extends _TaskInputModel implements ITaskInputModel {
    private Object normalizedValue;

    public TaskInputModel() {

    }

    public XNode getSchemaNode() {
        ISchema schema = getSchema();
        if (schema == null)
            return null;
        return schema.toNode(new HashMap<>());
    }

    public void normalize() {
        String value = getValue();
        if (value != null) {
            Object v = value;
            if (value.startsWith(CoreConstants.ATTR_EXPR_PREFIX)) {
                v = JsonTool.parseNonStrict(value.substring(CoreConstants.ATTR_EXPR_PREFIX.length()));
            }
            IGenericType type = getType();
            if (type != null) {
                v = BeanTool.castBeanToType(v, type);
            }
            this.normalizedValue = v;
        }
    }

    public IEvalAction getValueExpr() {
        IEvalAction source = getSource();
        if (source == null) {
            if (normalizedValue != null)
                source = new FixedValueEvalAction(normalizedValue);
        }
        return source;
    }

    public Object getNormalizedValue() {
        return normalizedValue;
    }
}
