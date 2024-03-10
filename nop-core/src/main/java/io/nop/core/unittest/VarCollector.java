/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.unittest;

/**
 * 为自动化测试提供支持。通过此类来收集系统运行过程中产生的不确定性的变量，从而在录制/重放测试可以把随机值替换为变量名。
 */
public class VarCollector {
    static VarCollector _instance = new VarCollector();

    public static void registerInstance(VarCollector collector) {
        _instance = collector;
    }

    public static VarCollector instance() {
        return _instance;
    }

    public void collectVar(String name, Object obj, String propName) {

    }

    public void collectVar(String name, Object value) {

    }
}
