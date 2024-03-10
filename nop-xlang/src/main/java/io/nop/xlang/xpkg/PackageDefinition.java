/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpkg;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.resource.component.AbstractComponentModel;

import java.util.Collections;
import java.util.Map;

public class PackageDefinition extends AbstractComponentModel implements IPackageDefinition {
    private Map<String, IFunctionModel> functions = Collections.emptyMap();
    private Map<String, IConstantDefinition> constants = Collections.emptyMap();

    @Override
    public Map<String, IFunctionModel> getFunctions() {
        return functions;
    }

    public void setFunctions(Map<String, IFunctionModel> functions) {
        checkAllowChange();
        this.functions = CollectionHelper.immutableSortedMap(functions);
    }

    @Override
    public Map<String, IConstantDefinition> getConstants() {
        return constants;
    }

    public void setConstants(Map<String, IConstantDefinition> constants) {
        checkAllowChange();
        this.constants = CollectionHelper.immutableSortedMap(constants);
    }
}
