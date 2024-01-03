/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.functions;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.SafeNumberComparator;
import io.nop.commons.functional.IEqualsChecker;
import io.nop.commons.lang.IValueWrapper;
import io.nop.commons.mutable.MutableValue;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.report.core.XptConstants;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedCellSet;

import java.util.Iterator;
import java.util.Objects;

import static io.nop.report.core.XptErrors.ARG_EXPR;

@Locale("zh-CN")
public class ReportFunctions {

    @Description("求和。忽略所有非数值类型")
    public static Number SUM(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        Number ret = 0;

        while (it.hasNext()) {
            Object value = it.next();
            if (!(value instanceof Number))
                continue;
            ret = MathHelper.add(ret, value);
        }
        return ret;
    }

    @Description("求所有值的乘积。忽略所有非数值类型")
    public static Number PRODUCT(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        Number ret = 1;

        while (it.hasNext()) {
            Object value = it.next();
            if (!(value instanceof Number))
                continue;
            ret = MathHelper.multiply(ret, value);
        }
        return ret;
    }

    @Description("对数值单元格进行计数")
    public static Number COUNT(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        int count = 0;
        while (it.hasNext()) {
            Object value = it.next();
            // 仅对数值单元格计数，逻辑与Excel相同
            if (!(value instanceof Number))
                continue;
            count++;
        }

        return count;
    }

    @Description("对非空单元格进行计数")
    public static Number COUNTA(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        int count = 0;
        while (it.hasNext()) {
            Object value = it.next();
            if (StringHelper.isEmptyObject(value))
                continue;
            count++;
        }

        return count;
    }

    @Description("求平均值")
    public static Number AVERAGE(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        Number ret = 0;

        int count = 0;
        while (it.hasNext()) {
            Object value = it.next();
            if (!(value instanceof Number))
                continue;
            ret = MathHelper.add(ret, value);
            count++;
        }

        // count=0时Excel会显示除零错误
        return MathHelper.divide(ret, count);
    }

    @Description("求最小值")
    public static Number MIN(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        Object ret = null;

        while (it.hasNext()) {
            Object value = it.next();
            if (!(value instanceof Number))
                continue;
            if (ret == null) {
                ret = value;
            } else {
                ret = MathHelper.min(ret, value);
            }
        }

        return (Number) ret;
    }

    @Description("求最大值。忽略所有非数值类型")
    public static Number MAX(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        Object ret = null;

        while (it.hasNext()) {
            Object value = it.next();
            if (!(value instanceof Number))
                continue;
            if (ret == null) {
                ret = value;
            } else {
                ret = MathHelper.max(ret, value);
            }
        }

        return (Number) ret;
    }

    @Description("当第一个参数为null时，使用第二个参数作为返回值")
    public static Object NVL(@Name("value") Object value, @Name("defaultValue") Object defaultValue) {
        value = resolveValue(value);
        if (value == null) {
            value = resolveValue(defaultValue);
        }
        return value;
    }

    private static Object resolveValue(Object value) {
        if (value instanceof IValueWrapper)
            value = ((IValueWrapper) value).getValue();
        return value;
    }

    @Description("计算当前单元格的值在所有展开单元格的汇总值中所占的比例")
    @EvalMethod
    public static Number PROPORTION(IEvalScope scope,
                                    @Name("cell") ExpandedCellSet cell,
                                    @Name("range") @Optional ExpandedCellSet range) {
        IXptRuntime xptRt = IXptRuntime.fromScope(scope);
        Object value = cell.getValue();
        if (value == null)
            return null;

        Number v = ConvertHelper.toNumber(value, err -> new NopException(err).source(cell).param(ARG_EXPR, cell));

        String cellName = cell.getCellName();
        if (range == null) {
            ExpandedCell firstCell = xptRt.getNamedCell(cellName);
            // 利用第一个单元格的计算属性来缓存汇总结果
            Number sum = (Number) firstCell.getComputed(XptConstants.KEY_ALL_SUM,
                    c -> SUM(xptRt.getNamedCellSet(cellName)));
            return MathHelper.divide(v, sum);
        } else {
            ExpandedCell rangeCell = range.getCell();
            // 利用第一个单元格的计算属性来缓存汇总结果
            Number sum = (Number) rangeCell.getComputed(cellName + '_' + XptConstants.KEY_ALL_SUM,
                    c -> SUM(rangeCell.childSet(cellName, xptRt)));
            return MathHelper.divide(v, sum);
        }
    }

    @Description("排名")
    @EvalMethod
    public static Integer RANK(IEvalScope scope, @Name("cell") ExpandedCellSet cell,
                               @Name("range") @Optional ExpandedCellSet range) {
        IXptRuntime xptRt = IXptRuntime.fromScope(scope);
        ExpandedCell curCell = cell.getCell();
        Object value = curCell.getValue();
        if (value == null)
            return null;

        String cellName = cell.getCellName();
        if (range == null) {
            ExpandedCell firstCell = xptRt.getNamedCell(cellName);
            // 利用第一个单元格的计算属性来缓存汇总结果
            RankCompute.RankResult rankResult = (RankCompute.RankResult) firstCell.getComputed(XptConstants.KEY_RANK,
                    c -> computeRank(xptRt.getNamedCellSet(cellName)));
            return rankResult.getRank(curCell);
        } else {
            ExpandedCell rangeCell = range.getCell();
            RankCompute.RankResult rankResult = (RankCompute.RankResult) rangeCell.getComputed(
                    cellName + '_' + XptConstants.KEY_RANK,
                    c -> computeRank(rangeCell.childSet(cellName, xptRt)));
            return rankResult.getRank(curCell);
        }
    }

    static RankCompute.RankResult<Number> computeRank(ExpandedCellSet cells) {
        return RankCompute.computeRank(cells.getCells(), ExpandedCell::getNumberValue,
                SafeNumberComparator.DESC, (IEqualsChecker) Objects::equals);
    }

    @Description("累积汇总")
    @EvalMethod
    public static Number ACCSUM(IEvalScope scope, @Name("cell") ExpandedCellSet cell,
                                @Name("range") @Optional ExpandedCellSet range) {
        IXptRuntime xptRt = IXptRuntime.fromScope(scope);
        ExpandedCell curCell = cell.getCell();

        ExpandedCell rangeCell = null;
        MutableValue<Number> accValue;
        if (range == null) {
            rangeCell = xptRt.getNamedCell(curCell.getName());
            accValue = (MutableValue<Number>) rangeCell.getComputed(XptConstants.KEY_ACCSUM,
                    c -> new MutableValue<>(0));
        } else {
            rangeCell = range.getCell();
            accValue = (MutableValue<Number>) rangeCell.getComputed(curCell.getName() + "_" + XptConstants.KEY_ACCSUM,
                    c -> new MutableValue<>(0));
        }

        Number value = curCell.getNumberValue();
        if (value != null) {
            accValue.setValue(MathHelper.add(accValue.getValue(), value));
        }
        return accValue.getValue();
    }

//    @Description("计算环比")
//    public static Object MOM(@Name("range") ExpandedCellSet range, @Name("value") ExpandedCellSet value,
//                             @Name("shift") @Optional Integer shift) {
//        return null;
//    }
}