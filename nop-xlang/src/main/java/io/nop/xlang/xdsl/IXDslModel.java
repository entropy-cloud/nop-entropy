/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.util.IComponentModel;
import io.nop.xlang.ast.ImportAsDeclaration;

import java.util.List;

public interface IXDslModel extends IComponentModel {
    String getXdslSchema();

    void setXdslSchema(String schemaPath);

    default void setImportExprs(List<ImportAsDeclaration> importExprs) {

    }

    String getXdslTransform();

    void setXdslTransform(String transform);
}
