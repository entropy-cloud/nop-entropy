/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.pdm;

import io.nop.core.resource.IResource;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;

public class PdmModelHelper {
    /**
     * 将pdm模型转化为ORM模型，并保存到模型文件中
     *
     * @param pdmResource pdm模型文件路径
     * @param ormResource orm模型文件路径
     */
    public static void transform(IResource pdmResource, IResource ormResource) {
        OrmModel ormModel = new PdmModelParser().parseFromResource(pdmResource);
        DslModelHelper.saveDslModel(ormModel.getXdslSchema(), ormModel, ormResource);
    }
}
