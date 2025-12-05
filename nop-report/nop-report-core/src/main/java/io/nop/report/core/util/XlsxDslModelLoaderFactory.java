/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.util;

import io.nop.core.resource.IResourceObjectLoader;
import io.nop.report.core.imp.XlsxDslModelLoader;
import io.nop.xlang.xdsl.IExcelModelLoaderFactory;

public class XlsxDslModelLoaderFactory implements IExcelModelLoaderFactory {
    @Override
    public int order() {
        return NORMAL_PRIORITY - 100;
    }

    @Override
    public IResourceObjectLoader<Object> newExcelModelLoader(String impPath) {
        return new XlsxDslModelLoader(impPath);
    }
}
