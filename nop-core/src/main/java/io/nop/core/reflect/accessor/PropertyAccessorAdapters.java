/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.core.reflect.IElementGetter;
import io.nop.core.reflect.IElementSetter;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;

public class PropertyAccessorAdapters {

    public static ISpecializedPropertyGetter getByProp(IPropertyGetter getter, String propName) {
        return (obj, ignored, scope) -> getter.getProperty(obj, propName, scope);
    }

    public static ISpecializedPropertyGetter getByIndex(IElementGetter getter, int index) {
        return (obj, ignored, scope) -> getter.getElement(obj, index, scope);
    }

    public static ISpecializedPropertySetter setByProp(IPropertySetter setter, String propName) {
        return (obj, ignored, value, scope) -> setter.setProperty(obj, propName, value, scope);
    }

    public static ISpecializedPropertySetter setByIndex(IElementSetter setter, int index) {
        return (obj, ignored, value, scope) -> setter.setElement(obj, index, value, scope);
    }
}
