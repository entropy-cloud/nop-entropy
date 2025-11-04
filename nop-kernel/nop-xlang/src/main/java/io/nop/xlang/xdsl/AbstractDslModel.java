/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.xlang.ast.ImportAsDeclaration;

import java.util.List;

public abstract class AbstractDslModel extends AbstractComponentModel implements IXDslModel {
    private String xdslSchema;
    private String xdslTransform;
    private List<ImportAsDeclaration> importExprs;

    public List<ImportAsDeclaration> getImportExprs() {
        return importExprs;
    }

    @Override
    public void setImportExprs(List<ImportAsDeclaration> importExprs) {
        this.importExprs = importExprs;
    }

    @Override
    public String getXdslSchema() {
        return xdslSchema;
    }

    @Override
    public void setXdslSchema(String xdslSchema) {
        checkAllowChange();
        this.xdslSchema = xdslSchema;
    }

    @Override
    public String getXdslTransform() {
        return xdslTransform;
    }

    @Override
    public void setXdslTransform(String xdslTransform) {
        checkAllowChange();
        this.xdslTransform = xdslTransform;
    }
}