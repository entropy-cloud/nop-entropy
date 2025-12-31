package io.nop.chart.export.data;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * Chart data type processor for handling various data types
 */
public class ChartDataTypeProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ChartDataTypeProcessor.class);

    // 常用日期格式
    private static final String[] DATE_FORMATS = {
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "dd-MM-yyyy",
            "dd.MM.yyyy"
    };

    /**
     * 转换值为数字类型
     *
     * @param value 原始值
     * @return 数字值，转换失败返回null
     */
    public Number convertToNumber(Object value) {
        return ConvertHelper.toNumber(value, NopException::new);
    }

    /**
     * 转换值为字符串类型
     *
     * @param value 原始值
     * @return 字符串值
     */
    public String convertToString(Object value) {
        return ConvertHelper.toString(value, NopException::new);
    }

    /**
     * 转换值为日期类型
     *
     * @param value 原始值
     * @return 日期值，转换失败返回null
     */
    public LocalDate convertToDate(Object value) {
        return ConvertHelper.toLocalDate(value, NopException::new);
    }

    /**
     * 检测值的数据类型
     *
     * @param value 值
     * @return 数据类型
     */
    public DataType detectDataType(Object value) {
        if (value == null) {
            return DataType.NULL;
        }

        if (value instanceof Number) {
            return DataType.NUMBER;
        }

        if (value instanceof Date || value instanceof LocalDate || value instanceof LocalDateTime) {
            return DataType.DATE;
        }

        if (value instanceof Boolean) {
            return DataType.BOOLEAN;
        }

        if (value instanceof String) {
            String str = ((String) value).trim();

            if (StringHelper.isNumber(str))
                return DataType.NUMBER;

            // 检查是否为日期
            for (String format : DATE_FORMATS) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(format);
                    sdf.parse(str);
                    return DataType.DATE;
                } catch (ParseException e) {
                    // 继续检查
                }
            }

            // 检查是否为布尔值
            if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) {
                return DataType.BOOLEAN;
            }

            return DataType.STRING;
        }

        return DataType.OBJECT;
    }

    /**
     * 格式化数字
     *
     * @param number 数字
     * @return 格式化后的字符串
     */
    private String formatNumber(Number number) {
        if (number instanceof Integer || number instanceof Long) {
            return number.toString();
        }

        if (number instanceof Float || number instanceof Double) {
            double value = number.doubleValue();
            if (value == Math.floor(value)) {
                // 整数值
                return String.valueOf((long) value);
            } else {
                // 小数值，保留合适的精度
                return String.format("%.2f", value);
            }
        }

        if (number instanceof BigDecimal) {
            return ((BigDecimal) number).stripTrailingZeros().toPlainString();
        }

        return number.toString();
    }

    /**
     * 数据类型枚举
     */
    public enum DataType {
        NULL,
        STRING,
        NUMBER,
        DATE,
        BOOLEAN,
        OBJECT
    }
}