/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.demo.spring.batch;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Service;


@Service
public class MyProcessor implements ItemProcessor {
    @Override
    public Object process(Object o) throws Exception {
        return null;
    }
}
