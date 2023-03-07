/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.loader;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.xdsl.DslModelParser;

public class TestParseSpeed {

    public static void main(String[] args) {
        CoreInitialization.initialize();
        IResource resource = VirtualFileSystem.instance().getResource("/nop/test/orm/app.orm.xml");
        for (int i = 0; i < 10000; i++) {
            new DslModelParser().parseFromResource(resource);
        }
    }
}
