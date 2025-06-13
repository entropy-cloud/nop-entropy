package io.nop.rule.core.excel;

import io.nop.api.core.beans.FilterBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.utils.JavaGenericTypeBuilder;

import java.util.List;
import java.util.Map;

public class RuleModelHelper {
    public static TreeBean transformToFilter(List<Map<String, Object>> filterBeans) {
        if (filterBeans == null || filterBeans.isEmpty())
            return null;

        List<FilterBean> ret = BeanTool.castBeanToType(filterBeans, JavaGenericTypeBuilder.buildListType(FilterBean.class));
        return FilterBeans.fromFilterBeanList(ret);
    }
}
