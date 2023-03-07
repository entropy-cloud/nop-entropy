/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package org.beetl.sql.jmh.xorm;

import io.nop.api.core.util.LogLevel;
import io.nop.core.initialize.CoreInitialization;
import io.nop.log.core.LoggerConfigurator;
import lombok.Data;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@Data
public class NopBoot {
    NopOrmService service = null;

    public void init() {
        CoreInitialization.initialize();

        LoggerConfigurator.instance().changeRootLogLevel(LogLevel.ERROR);

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(NopAppConfig.class);
        ctx.refresh();
        service = ctx.getBean(NopOrmService.class);
    }
}
