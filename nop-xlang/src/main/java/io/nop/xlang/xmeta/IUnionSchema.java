/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta;

import java.util.List;

public interface IUnionSchema extends ISchemaNode {
    /**
     * union类型转换为json之后通过type属性来区分
     */
    String getSubTypeProp();

    List<ISchema> getOneOf();
}