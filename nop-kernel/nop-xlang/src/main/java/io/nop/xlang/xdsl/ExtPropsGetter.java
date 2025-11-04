/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.hook.IPropGetMissingHook;
import io.nop.core.type.IGenericType;
import io.nop.core.type.utils.GenericTypeHelper;

import java.util.List;
import java.util.Map;

public class ExtPropsGetter {

    public static TreeBean getTreeBean(IPropGetMissingHook obj, String name) {
        Object value = obj.prop_get(name);
        if (value == null)
            return null;
        if (value instanceof ITreeBean)
            return ((ITreeBean) value).toTreeBean();

        return TreeBean.createFromJson((Map<String, Object>) value);
    }

    public static List<OrderFieldBean> getOrderBy(IPropGetMissingHook obj, String name) {
        Object value = obj.prop_get(name);
        if (value == null)
            return null;

        if(value instanceof List){
            return (List<OrderFieldBean>) value;
        }

        Map<String, Object> map = (Map<String, Object>) value;
        Object body = map.get(ApiConstants.TREE_BEAN_PROP_BODY);
        if (!(body instanceof List))
            return null;

        IGenericType beanType = ReflectionManager.instance().buildRawType(OrderFieldBean.class);
        return BeanTool.buildBean(body, GenericTypeHelper.buildListType(beanType));
    }
}
