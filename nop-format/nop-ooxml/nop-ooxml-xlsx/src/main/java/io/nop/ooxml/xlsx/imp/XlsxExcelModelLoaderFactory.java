/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.imp;

import io.nop.core.resource.IResourceObjectLoader;
import io.nop.xlang.xdsl.IExcelModelLoaderFactory;

public class XlsxExcelModelLoaderFactory implements IExcelModelLoaderFactory {

    @Override
    public IResourceObjectLoader<Object> newExcelModelLoader(String impPath) {
        return new XlsxObjectLoader(impPath);
    }
}
