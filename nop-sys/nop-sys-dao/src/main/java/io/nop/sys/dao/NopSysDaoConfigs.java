/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.dao;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static io.nop.api.core.config.AppConfig.varRef;

public interface NopSysDaoConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopSysDaoConfigs.class);
    @Description("是否自动初始化缺省Sequence")
    IConfigReference<Boolean> CFG_SYS_INIT_DEFAULT_SEQUENCE =
            varRef(s_loc,"nop.sys.init-default-sequence", Boolean.class, true);

    @Description("编码规则缓存的容量上限")
    IConfigReference<Integer> CFG_SYS_CODE_RULE_CACHE_MAX_SIZE =
            varRef(s_loc, "nop.sys.code-rule.cache-max-size", Integer.class, 1000);

    @Description("编码规则缓存的超时时间。BizModel失效钩子只覆盖本节点的管理端修改，"
            + "多节点部署下其他节点依赖此超时最终一致")
    IConfigReference<Duration> CFG_SYS_CODE_RULE_CACHE_TTL =
            varRef(s_loc, "nop.sys.code-rule.cache-timeout", Duration.class,
                    Duration.of(60, ChronoUnit.SECONDS));
}
