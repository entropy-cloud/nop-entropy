package io.nop.core.lang.eval;

import io.nop.api.core.util.IVariableScope;
import io.nop.core.reflect.bean.BeanTool;

public interface IBeanVariableScope extends IVariableScope {
    default Object getValueByPropPath(String propPath) {
        int pos = propPath.indexOf('.');
        if (pos < 0)
            return getValue(propPath);
        Object o = getValue(propPath.substring(0, pos));
        if (o == null)
            return null;
        return BeanTool.getComplexProperty(o, propPath.substring(pos + 1));
    }
}
