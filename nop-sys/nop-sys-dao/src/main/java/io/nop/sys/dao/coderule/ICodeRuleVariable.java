/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.sys.dao.coderule;

import java.time.LocalDateTime;
import java.util.function.LongSupplier;

public interface ICodeRuleVariable {
    String resolve(String options, LocalDateTime now, LongSupplier seqGenerator, Object bean);
}
