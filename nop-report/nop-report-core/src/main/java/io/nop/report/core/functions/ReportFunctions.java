/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static io.nop.report.core.XptErrors.ARG_EXPR;

@Locale("zh-CN")
public class ReportFunctions {

    @Description("求和。忽略所有非数值类型")
    public static Number SUM(@Name("values") Object... values) {
        if (values == null || values.length == 0)
            return null;

        Number ret = 0;
        for (Object valueItem : values) {
            Iterator<Object> it = CollectionHelper.toIterator(valueItem, true);


            while (it.hasNext()) {
                Object value = it.next();
                if (!(value instanceof Number))
                    continue;
                ret = MathHelper.add(ret, value);
            }
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

    @Description("两列相乘求和")
    public static Number SUMPRODUCT(@Name("valuesA") Object valuesA, @Name("valuesB") Object valuesB) {
        if (valuesA == null || valuesB == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(valuesA, true);
        Iterator<Object> it2 = CollectionHelper.toIterator(valuesB, true);
        Number ret = 0;

        while (it.hasNext() && it2.hasNext()) {
            Object value = it.next();
            Object value2 = it2.next();
            if (!(value instanceof Number))
                continue;
            if (!(value2 instanceof Number))
                continue;
            ret = MathHelper.add(ret, MathHelper.multiply(value, value2));
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

    //============以下函数为AI生成
    @Description("计算标准差")
    public static Number STDEV(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        List<Number> numbers = new ArrayList<>();

        while (it.hasNext()) {
            Object value = it.next();
            if (value instanceof Number) {
                numbers.add((Number) value);
            }
        }

        if (numbers.size() < 2)
            return null;

        // 计算平均值
        double sum = 0;
        for (Number num : numbers) {
            sum += num.doubleValue();
        }
        double mean = sum / numbers.size();

        // 计算方差
        double variance = 0;
        for (Number num : numbers) {
            variance += Math.pow(num.doubleValue() - mean, 2);
        }
        variance /= (numbers.size() - 1); // 样本标准差使用n-1

        return Math.sqrt(variance);
    }

    @Description("计算方差")
    public static Number VAR(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        List<Number> numbers = new ArrayList<>();

        while (it.hasNext()) {
            Object value = it.next();
            if (value instanceof Number) {
                numbers.add((Number) value);
            }
        }

        if (numbers.size() < 2)
            return null;

        // 计算平均值
        double sum = 0;
        for (Number num : numbers) {
            sum += num.doubleValue();
        }
        double mean = sum / numbers.size();

        // 计算方差
        double variance = 0;
        for (Number num : numbers) {
            variance += Math.pow(num.doubleValue() - mean, 2);
        }
        return variance / (numbers.size() - 1); // 样本方差使用n-1
    }

    @Description("条件计数")
    public static Number COUNTIF(@Name("range") Object range,
                                 @Name("condition") Object condition) {
        if (range == null || condition == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(range, true);
        int count = 0;

        // 将条件转换为可比较的形式
        Object conditionValue = resolveValue(condition);

        while (it.hasNext()) {
            Object value = it.next();
            value = resolveValue(value);

            if (matchesCondition(value, conditionValue)) {
                count++;
            }
        }

        return count;
    }

    @Description("条件求和")
    public static Number SUMIF(@Name("range") Object range,
                               @Name("condition") Object condition,
                               @Name("sumRange") @Optional Object sumRange) {
        if (range == null || condition == null)
            return null;

        Iterator<Object> rangeIt = CollectionHelper.toIterator(range, true);
        Iterator<Object> sumIt = sumRange != null ?
                CollectionHelper.toIterator(sumRange, true) : rangeIt;

        Object conditionValue = resolveValue(condition);
        Number sum = 0;

        while (rangeIt.hasNext() && sumIt.hasNext()) {
            Object rangeValue = rangeIt.next();
            Object sumValue = sumIt.next();

            rangeValue = resolveValue(rangeValue);
            sumValue = resolveValue(sumValue);

            if (matchesCondition(rangeValue, conditionValue) && sumValue instanceof Number) {
                sum = MathHelper.add(sum, sumValue);
            }
        }

        return sum;
    }

    @Description("连接文本")
    public static String CONCAT(@Name("values") Object... values) {
        if (values == null || values.length == 0)
            return null;

        StringBuilder sb = new StringBuilder();
        for (Object valueItem : values) {
            Iterator<Object> it = CollectionHelper.toIterator(valueItem, true);

            while (it.hasNext()) {
                Object value = it.next();
                if (value != null) {
                    sb.append(value.toString());
                }
            }
        }

        return sb.toString();
    }

    @Description("条件判断")
    public static Object IF(@Name("condition") Object condition,
                            @Name("trueValue") Object trueValue,
                            @Name("falseValue") Object falseValue) {
        condition = resolveValue(condition);
        boolean boolCondition = ConvertHelper.toTruthy(condition);

        if (boolCondition) {
            return resolveValue(trueValue);
        } else {
            return resolveValue(falseValue);
        }
    }

    // 辅助方法：判断值是否匹配条件
    private static boolean matchesCondition(Object value, Object condition) {
        if (condition instanceof String) {
            String condStr = (String) condition;
            // 支持通配符匹配
            if (condStr.contains("*") || condStr.contains("?")) {
                return StringHelper.matchSimplePattern(value != null ? value.toString() : "", condStr);
            }
            // 支持比较操作符
            if (condStr.startsWith(">") || condStr.startsWith("<") ||
                    condStr.startsWith(">=") || condStr.startsWith("<=") ||
                    condStr.startsWith("=") || condStr.startsWith("<>")) {
                return compareWithOperator(value, condStr);
            }
        }

        // 默认相等比较
        return Objects.equals(value, condition);
    }

    // 辅助方法：使用操作符比较
    private static boolean compareWithOperator(Object value, String condition) {
        try {
            String op = condition.substring(0, condition.indexOf(condition.replaceAll("[^<>=]", "").charAt(0)) + 1);
            String condValueStr = condition.substring(op.length()).trim();

            if (value instanceof Number && StringHelper.isNumber(condValueStr)) {
                double numValue = ((Number) value).doubleValue();
                double condValue = Double.parseDouble(condValueStr);

                switch (op) {
                    case ">": return numValue > condValue;
                    case "<": return numValue < condValue;
                    case ">=": return numValue >= condValue;
                    case "<=": return numValue <= condValue;
                    case "=": return numValue == condValue;
                    case "<>": return numValue != condValue;
                    default: return false;
                }
            }
        } catch (Exception e) {
            // 解析失败，回退到字符串比较
        }

        // 字符串比较
        String valueStr = value != null ? value.toString() : "";
        String condValueStr = condition.replaceFirst("[<>=]+", "").trim();

        int comparison = valueStr.compareTo(condValueStr);
        switch (condition.replaceAll("[^<>=]", "")) {
            case ">": return comparison > 0;
            case "<": return comparison < 0;
            case ">=": return comparison >= 0;
            case "<=": return comparison <= 0;
            case "=": return comparison == 0;
            case "<>": return comparison != 0;
            default: return false;
        }
    }

    @Description("计算中位数")
    public static Number MEDIAN(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        List<Double> numbers = new ArrayList<>();

        while (it.hasNext()) {
            Object value = it.next();
            if (value instanceof Number) {
                numbers.add(((Number) value).doubleValue());
            }
        }

        if (numbers.isEmpty())
            return null;

        // 排序
        numbers.sort(Double::compareTo);

        int size = numbers.size();
        if (size % 2 == 1) {
            // 奇数个元素，取中间值
            return numbers.get(size / 2);
        } else {
            // 偶数个元素，取中间两个值的平均
            return (numbers.get(size / 2 - 1) + numbers.get(size / 2)) / 2.0;
        }
    }
}