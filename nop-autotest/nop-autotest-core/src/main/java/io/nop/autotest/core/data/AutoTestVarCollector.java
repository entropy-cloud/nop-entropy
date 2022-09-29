package io.nop.autotest.core.data;

import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.unittest.VarCollector;

public class AutoTestVarCollector extends VarCollector {

    @Override
    public void collectVar(String name, Object obj, String propName) {
        collectVar(name, BeanTool.instance().getProperty(obj, propName));
    }

    @Override
    public void collectVar(String name, Object value) {
        AutoTestVars.addVar(name, value);
    }
}