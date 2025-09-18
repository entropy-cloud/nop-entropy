/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.dataset;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.FieldComparator;
import io.nop.commons.collections.MappingComparator;
import io.nop.commons.collections.OrderByComparator;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.utils.Underscore;
import io.nop.report.core.engine.IXptRuntime;

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

    public DynamicReportDataSet toDynamic(IXptRuntime xptRt) {
        if (this instanceof DynamicReportDataSet)
            return ((DynamicReportDataSet) this);
        return new DynamicReportDataSet(dsName, items, xptRt);
    }

    public String getDsName() {
        return dsName;
    }

    public List<Object> getItems() {
        return items;
    }

    public boolean isEmpty() {
        return current().isEmpty();
    }

    public int size() {
        return current().size();
    }

    @Override
    public Iterator<Object> iterator() {
        return current().iterator();
    }

    public List<KeyedReportDataSet> group(String field) {
        Map<Object, List<Object>> map = new LinkedHashMap<>();
        List<Object> current = current();
        for (Object item : current) {
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

    public List<KeyedReportDataSet> groupBy(Function<Object, Object> fn) {
        Map<Object, List<Object>> map = new LinkedHashMap<>();
        for (Object item : current()) {
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

    public List<KeyedReportDataSet> groupAndSortBy(String field, Function<KeyedReportDataSet,Object> sortFn) {
        List<KeyedReportDataSet> ret = group(field);
        return Underscore.sortByFn(ret, sortFn);
    }

    public ReportDataSet where(Map<String, Object> props) {
        List<Object> ret = Underscore.where(current(), props);
        return new ReportDataSet(dsName, ret);
    }

    public ReportDataSet where(String propName, Object propValue) {
        List<Object> ret = Underscore.where(current(), propName, propValue);
        return new ReportDataSet(dsName, ret);
    }

    public ReportDataSet filter(Predicate<Object> filter) {
        List<Object> items = current();
        items = items.stream().filter(filter).collect(Collectors.toList());
        return new ReportDataSet(dsName, items);
    }

    public int countIf(Predicate<Object> filter) {
        List<Object> items = current();
        return (int) items.stream().filter(filter).count();
    }

    public ReportDataSet sort(String field, boolean desc) {
        List<Object> items = current();
        items = new ArrayList<>(items);
        items.sort(new FieldComparator<>(field, desc, null, this::getFieldValue));
        return new ReportDataSet(dsName, items);
    }

    public ReportDataSet sortBy(Function<Object, Object> fn) {
        List<Object> items = current();
        items = new ArrayList<>(items);
        items.sort(new MappingComparator<>(false, null, fn));
        return new ReportDataSet(dsName, items);
    }

    public ReportDataSet each(Consumer<Object> action) {
        List<Object> items = current();
        items.forEach(action);
        return this;
    }

    public ReportDataSet sort2(String field, boolean desc, String field2, boolean desc2) {
        List<OrderFieldBean> orderBy = new ArrayList<>(2);
        orderBy.add(OrderFieldBean.forField(field, desc));
        orderBy.add(OrderFieldBean.forField(field2, desc2));
        return sort(orderBy);
    }


    /**
     * 在EL表达式中可以使用 ds.sort(order_by `a asc, b desc`)，利用order_by宏表达式来生成orderBy
     */
    public ReportDataSet sort(List<OrderFieldBean> orderBy) {
        List<Object> items = current();
        items = new ArrayList<>(items);
        items.sort(new OrderByComparator<>(orderBy, this::getFieldValue));
        return new ReportDataSet(dsName, items);
    }

    public Object first() {
        List<Object> items = current();
        if (items.isEmpty())
            return null;
        Object item = items.get(0);
        return item;
    }

    public Object last() {
        List<Object> items = current();
        if (items.isEmpty())
            return null;
        Object item = items.get(items.size() - 1);
        return item;
    }

    public Object field(String field) {
        Object item = first();
        return item == null ? null : getFieldValue(item, field);
    }

    public List<Object> select(String field) {
        List<Object> items = current();
        return Underscore.pluck(items, field);
    }

    public ReportDataSet map(Function<Object, Object> fn) {
        List<Object> items = current();
        items = items.stream().map(fn).collect(Collectors.toList());
        return new ReportDataSet(dsName, items);
    }

    public Number sum(String field) {
        List<Object> items = current();
        Number ret = 0;
        for (Object item : items) {
            Object value = getFieldValue(item, field);
            if (!StringHelper.isEmptyObject(value)) {
                ret = MathHelper.add(ret, value);
            }
        }
        return ret;
    }

    public Number sumBy(Function<Object, Object> fn) {
        List<Object> items = current();
        Number ret = 0;
        for (Object item : items) {
            Object value = fn.apply(item);
            if (!StringHelper.isEmptyObject(value)) {
                ret = MathHelper.add(ret, value);
            }
        }
        return ret;
    }

    public Number avg(String field) {
        List<Object> items = current();
        Number ret = 0;
        for (Object item : items) {
            Object value = getFieldValue(item, field);
            if (!StringHelper.isEmptyObject(value)) {
                ret = MathHelper.add(ret, value);
            }
        }
        return MathHelper.divide(ret, items.size());
    }

    public Number avgBy(Function<Object, Object> fn) {
        List<Object> items = current();
        Number ret = 0;
        for (Object item : items) {
            Object value = fn.apply(item);
            if (!StringHelper.isEmptyObject(value)) {
                ret = MathHelper.add(ret, value);
            }
        }
        return MathHelper.divide(ret, items.size());
    }


    public Object max(String field) {
        List<Object> items = current();
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


    public Object maxBy(Function<Object, Object> fn) {
        List<Object> items = current();
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

    public Object min(String field) {
        List<Object> items = current();
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

    public Object minBy(Function<Object, Object> fn) {
        List<Object> items = current();
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

    public List<Object> current() {
        return items;
    }
}