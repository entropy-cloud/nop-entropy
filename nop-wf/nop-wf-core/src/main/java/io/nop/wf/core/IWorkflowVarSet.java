/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core;

import java.util.Map;
import java.util.Set;

/**
 * 每个工作流实例都对应一个全局变量集合
 */
public interface IWorkflowVarSet {
    Set<String> getVarNames();

    Object getVar(String varName);

    void removeVar(String varName);

    void setVar(String varName, Object value);

    void setVars(Map<String, Object> vars);
}