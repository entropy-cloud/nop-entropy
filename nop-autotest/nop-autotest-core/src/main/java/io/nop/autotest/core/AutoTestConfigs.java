/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

public interface AutoTestConfigs {
    // @Description("强制设置AutoTestCase进入记录模式, 重新运行测试用例来更新录制的数据,而不是进入检查模式")
    // IConfigReference<Boolean> CFG_AUTOTEST_FORCE_RECORD_MODE =
    // AppConfig.varRef("nop.autotest.force-record-mode",
    // Boolean.class, false);
    SourceLocation S_LOC = SourceLocation.fromClass(AutoTestConfigs.class);

    @Description("强制设置AutoTestCase保存执行结果，而不是校验执行结果符合预期。当我们需要根据根据录制的输入数据重新生成输出时可以开启此开关")
    IConfigReference<Boolean> CFG_AUTOTEST_FORCE_SAVE_OUTPUT = AppConfig.varRef(S_LOC,"nop.autotest.force-save-output",
            Boolean.class, false);

    @Description("强制设置AutoTestCase保存执行结果，禁用所有方法上的@EnableSnapshot注解")
    IConfigReference<Boolean> CFG_AUTOTEST_DISABLE_SNAPSHOT = AppConfig.varRef(S_LOC,"nop.autotest.disable-snapshot",
            Boolean.class, false);
}
