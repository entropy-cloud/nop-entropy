/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.coderule;

import java.time.LocalDateTime;
import java.util.function.LongSupplier;

public interface ICodeRule {
    void addVariable(String name, ICodeRuleVariable variable);

    void removeVariable(String name, ICodeRuleVariable variable);

    String generate(String codeRulePattern, LocalDateTime now, LongSupplier seqGenerator, Object bean);
}
