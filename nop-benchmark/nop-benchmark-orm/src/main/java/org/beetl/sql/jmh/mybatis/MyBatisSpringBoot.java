/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh.mybatis;

import lombok.Data;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@Data
public class MyBatisSpringBoot {
    MyBatisSpringService service = null;

    public void init() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(MyBatisAppConfig.class);
        ctx.refresh();
        service = ctx.getBean(MyBatisSpringService.class);

    }

}
