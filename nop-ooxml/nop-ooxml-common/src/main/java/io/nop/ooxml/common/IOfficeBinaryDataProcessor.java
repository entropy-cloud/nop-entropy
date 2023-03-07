/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.common;

import io.nop.core.resource.IResource;
import io.nop.ooxml.common.model.OfficeRelationship;

public interface IOfficeBinaryDataProcessor {
    /**
     * 用于导出文档中的图片等二进制数据
     *
     * @param rel
     * @param resource
     * @return html文件中使用的链接
     */
    String process(OfficeRelationship rel, IResource resource);
}