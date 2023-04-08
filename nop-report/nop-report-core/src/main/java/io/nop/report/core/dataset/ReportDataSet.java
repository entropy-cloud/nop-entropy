/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.dataset;

import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.FieldComparator;
import io.nop.commons.collections.MappingComparator;
import io.nop.commons.collections.OrderByComparator;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.utils.Underscore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.nop.report.core.XptErrors.ARG_DS_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_MISSING_VAR_DS;

/**
 * 从dsName对应的数据集中获取得到的一个数据子集
 */
public class ReportDataSet implements Iterable<Object> {
    private final String dsName;
    private final List<Object> items;

    public ReportDataSet(String dsName, List<Object> items) {
        this.dsName = dsName;
        this.items = items;
    }

    public static ReportDataSet newDataSet(String dsName, Object value) {
        if (value == null)
            throw new NopException(ERR_XPT_MISSING_VAR_DS)
                    .param(ARG_DS_NAME, dsName);

        if (value instanceof ReportDataSet) {
            ReportDataSet rs = (ReportDataSet) value;
            if (rs.getDsName().equals(dsName))
                return rs;
            return new ReportDataSet(dsName, rs.getItems());
        }

        List<Object> items = CollectionHelper.toList(value);
        return new ReportDataSet(dsName, items);
    }

    public DynamicReportDataSet toDynamic() {
        if (this instanceof DynamicReportDataSet)
            return ((DynamicReportDataSet) this);
        return new DynamicReportDataSet(dsName, items);
    }

    public String getDsName() {
        return dsName;
    }

    public List<Object> getItems() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    @Override
    public Iterator<Object> iterator() {
        return items.iterator();
    }

    @EvalMethod
    public List<KeyedReportDataSet> group(IEvalScope scope, String field) {
        Map<Object, List<Object>> map = new LinkedHashMap<>();
        for (Object item : current(scope)) {
            Object value = getFieldValue(item, field);
            map.computeIfAbsent(value, k -> new ArrayList<>()).add(item);
        }

        List<KeyedReportDataSet> ret = new ArrayList<>(map.size());
        for (Map.Entry<Object, List<Object>> entry : map.entrySet()) {
            KeyedReportDataSet result = new KeyedReportDataSet(dsName, entry.getValue(), entry.getKey());
            ret.add(result);
        }
        return ret;
    }

    @EvalMethod
    public List<KeyedReportDataSet> groupBy(IEvalScope scope, Function<Object, Object> fn) {
        Map<Object, List<Object>> map = new LinkedHashMap<>();
        for (Object item : current(scope)) {
            Object value = fn.apply(item);
            map.computeIfAbsent(value, k -> new ArrayList<>()).add(item);
        }

        List<KeyedReportDataSet> ret = new ArrayList<>(map.size());
        for (Map.Entry<Object, List<Object>> entry : map.entrySet()) {
            KeyedReportDataSet result = new KeyedReportDataSet(dsName, entry.getValue(), entry.getKey());
            ret.add(result);
        }
        return ret;
    }

    @EvalMethod
    public List<KeyedReportDataSet> group(IEvalScope scope, String field, Object sortFn) {
        List<KeyedReportDataSet> ret = group(scope, field);
        return Underscore.sortBy(ret, sortFn);
    }

    @EvalMethod
    public ReportDataSet where(IEvalScope scope, Map<String, Object> props) {
        List<Object> ret = Underscore.where(current(scope), props);
        return new ReportDataSet(dsName, ret);
    }

    @EvalMethod
    public ReportDataSet where(IEvalScope scope, String propName, Object propValue) {
        List<Object> ret = Underscore.where(current(scope), propName, propValue);
        return new ReportDataSet(dsName, ret);
    }

    @EvalMethod
    public ReportDataSet filter(IEvalScope scope, Predicate<Object> filter) {
        List<Object> items = current(scope);
        items = items.stream().filter(filter).collect(Collectors.toList());
        return new ReportDataSet(dsName, items);
    }

    @EvalMethod
    public int countIf(IEvalScope scope, Predicate<Object> filter) {
        List<Object> items = current(scope);
        return (int) items.stream().filter(filter).count();
    }

    @EvalMethod
    public ReportDataSet sort(IEvalScope scope, String field, boolean desc) {
        List<Object> items = current(scope);
        items = new ArrayList<>(items);
        items.sort(new FieldComparator<>(field, desc, null, this::getFieldValue));
        return new ReportDataSet(dsName, items);
    }

    @EvalMethod
    public ReportDataSet sortBy(IEvalScope scope, Function<Object, Object> fn) {
        List<Object> items = current(scope);
        items = new ArrayList<>(items);
        items.sort(new MappingComparator<>(false, null, fn));
        return new ReportDataSet(dsName, items);
    }

    @EvalMethod
    public ReportDataSet forEach(IEvalScope scope, Consumer<Object> action) {
        List<Object> items = current(scope);
        items.forEach(action);
        return this;
    }

    @EvalMethod
    public ReportDataSet sort2(IEvalScope scope, String field, boolean desc, String field2, boolean desc2) {
        List<OrderFieldBean> orderBy = new ArrayList<>(2);
        orderBy.add(OrderFieldBean.forField(field, desc));
        orderBy.add(OrderFieldBean.forField(field2, desc2));
        return sort(scope, orderBy);
    }


    /**
     * 在EL表达式中可以使用 ds.sort(order_by `a asc, b desc`)，利用order_by宏表达式来生成orderBy
     */
    @EvalMethod
    public ReportDataSet sort(IEvalScope scope, List<OrderFieldBean> orderBy) {
        List<Object> items = current(scope);
        items = new ArrayList<>(items);
        items.sort(new OrderByComparator<>(orderBy, this::getFieldValue));
        return new ReportDataSet(dsName, items);
    }

    @EvalMethod
    public Object first(IEvalScope scope) {
        List<Object> items = current(scope);
        if (items.isEmpty())
            return null;
        Object item = items.get(0);
        return item;
    }

    @EvalMethod
    public Object last(IEvalScope scope) {
        List<Object> items = current(scope);
        if (items.isEmpty())
            return null;
        Object item = items.get(items.size() - 1);
        return item;
    }

    @EvalMethod
    public Object field(IEvalScope scope, String field) {
        Object item = first(scope);
        return item == null ? null : getFieldValue(item, field);
    }

    @EvalMethod
    public List<Object> select(IEvalScope scope, String field) {
        List<Object> items = current(scope);
        return Underscore.pluck(items, field);
    }

    @EvalMethod
    public ReportDataSet map(IEvalScope scope, Function<Object, Object> fn) {
        List<Object> items = current(scope);
        items = items.stream().map(fn).collect(Collectors.toList());
        return new ReportDataSet(dsName, items);
    }

    @EvalMethod
    public Number sum(IEvalScope scope, String field) {
        List<Object> items = current(scope);
        Number ret = 0;
        for (Object item : items) {
            Object value = getFieldValue(item, field);
            if (!StringHelper.isEmptyObject(value)) {
                ret = MathHelper.add(ret, value);
            }
        }
        return ret;
    }

    @EvalMethod
    public Number sumBy(IEvalScope scope, Function<Object, Object> fn) {
        List<Object> items = current(scope);
        Number ret = 0;
        for (Object item : items) {
            Object value = fn.apply(item);
            if (!StringHelper.isEmptyObject(value)) {
                ret = MathHelper.add(ret, value);
            }
        }
        return ret;
    }

    @EvalMethod
    public Number avg(IEvalScope scope, String field) {
        List<Object> items = current(scope);
        Number ret = 0;
        for (Object item : items) {
            Object value = getFieldValue(item, field);
            if (!StringHelper.isEmptyObject(value)) {
                ret = MathHelper.add(ret, value);
            }
        }
        return MathHelper.divide(ret, items.size());
    }

    @EvalMethod
    public Number avgBy(IEvalScope scope, Function<Object, Object> fn) {
        List<Object> items = current(scope);
        Number ret = 0;
        for (Object item : items) {
            Object value = fn.apply(item);
            if (!StringHelper.isEmptyObject(value)) {
                ret = MathHelper.add(ret, value);
            }
        }
        return MathHelper.divide(ret, items.size());
    }


    @EvalMethod
    public Object max(IEvalScope scope, String field) {
        List<Object> items = current(scope);
        Object ret = null;
        for (Object item : items) {
            Object value = getFieldValue(item, field);
            if (ret == null) {
                ret = value;
            } else if (value != null) {
                if (MathHelper.compareWithConversion(ret, value) < 0) {
                    ret = value;
                }
            }
        }
        return ret;
    }


    @EvalMethod
    public Object maxBy(IEvalScope scope, Function<Object, Object> fn) {
        List<Object> items = current(scope);
        Object ret = null;
        for (Object item : items) {
            Object value = fn.apply(item);
            if (ret == null) {
                ret = value;
            } else if (value != null) {
                if (MathHelper.compareWithConversion(ret, value) < 0) {
                    ret = value;
                }
            }
        }
        return ret;
    }

    @EvalMethod
    public Object min(IEvalScope scope, String field) {
        List<Object> items = current(scope);
        Object ret = null;
        for (Object item : items) {
            Object value = getFieldValue(item, field);
            if (ret == null) {
                ret = value;
            } else if (value == null) {
                return value;
            } else if (MathHelper.compareWithConversion(ret, value) > 0) {
                ret = value;
            }
        }
        return ret;
    }

    @EvalMethod
    public Object minBy(IEvalScope scope, Function<Object, Object> fn) {
        List<Object> items = current(scope);
        Object ret = null;
        for (Object item : items) {
            Object value = fn.apply(item);
            if (ret == null) {
                ret = value;
            } else if (value == null) {
                return value;
            } else if (MathHelper.compareWithConversion(ret, value) > 0) {
                ret = value;
            }
        }
        return ret;
    }

    private Object getFieldValue(Object bean, String field) {
        return Underscore.getFieldValue(bean, field);
    }

    @EvalMethod
    public List<Object> current(IEvalScope scope) {
        return items;
    }
}