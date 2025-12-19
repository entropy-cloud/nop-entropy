package io.nop.excel.util;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.dict.DictProvider;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.excel.ExcelErrors;
import io.nop.excel.model.ExcelDataValidation;
import io.nop.excel.model.constants.ExcelDataValidationErrorStyle;
import io.nop.excel.model.constants.ExcelDataValidationOperator;
import io.nop.excel.model.constants.ExcelDataValidationType;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static io.nop.excel.ExcelConstants.KEY_EXCEL_VALIDATION_FORMULA;
import static io.nop.excel.ExcelConstants.KEY_UI_PLACEHOLDER;

/**
 * Excel数据验证辅助类，根据Schema定义生成Excel数据验证规则
 */
public class ExcelDataValidationHelper {
    static final Double MIN_INTEGER_VALUE = (double) Integer.MIN_VALUE;

    public static ExcelDataValidation newDataValidation(IObjPropMeta propMeta, String sqref) {
        if (StringHelper.isEmpty(sqref))
            throw new IllegalArgumentException("invalid sqref:" + sqref);

        ExcelDataValidation validation = newDataValidation(propMeta.getSchema(),
                propMeta.isMandatory(), propMeta.getName(), sqref);
        String placeholder = (String) propMeta.prop_get(KEY_UI_PLACEHOLDER);
        if (placeholder == null)
            placeholder = propMeta.getDescription();
        validation.setPrompt(placeholder);
        return validation;
    }

    /**
     * 根据Schema定义创建Excel数据验证规则
     *
     * @param schema   字段模式定义
     * @param fieldName 属性名称
     * @param sqref    验证范围（如：A1:B10）
     * @return Excel数据验证对象
     */
    public static ExcelDataValidation newDataValidation(ISchema schema, boolean mandatory,
                                                        String fieldName, String sqref) {
        ExcelDataValidation validation = new ExcelDataValidation();
        validation.sqref(sqref);

        if (schema == null) {
            return validation;
        }

        // 设置是否允许空值
        validation.setAllowBlank(!mandatory);

        Map<String, Object> params = new HashMap<>();
        params.put(ExcelErrors.ARG_FIELD_NAME, fieldName);
        if (schema.getPrecision() != null) {
            params.put(ExcelErrors.ARG_PRECISION, schema.getPrecision());
        }
        if (schema.getScale() != null) {
            params.put(ExcelErrors.ARG_SCALE, schema.getScale());
        }

        // 设置基本验证属性
        setBasicValidationProperties(validation, schema, params);

        // 根据Schema类型设置验证规则
        setValidationRules(validation, schema, params);

        return validation;
    }

    /**
     * 设置基本验证属性
     */
    private static void setBasicValidationProperties(ExcelDataValidation validation, ISchema schema,
                                                     Map<String, Object> params) {

        // 设置错误提示信息
        String errorTitle = resolveError(ExcelErrors.ERR_EXCEL_VALIDATION_TITLE, params);
        validation.setErrorTitle(errorTitle);


        String errorMsg = resolveError(ExcelErrors.ERR_EXCEL_VALIDATION_FIELD_VALUE_INVALID, params);
        validation.setError(errorMsg);

        // 设置错误样式
        validation.setErrorStyle(ExcelDataValidationErrorStyle.STOP);

        // 设置是否显示错误和输入提示
        validation.setShowErrorMessage(true);
        validation.setShowInputMessage(true);
    }

    static String resolveError(ErrorCode errorCode, Map<String, Object> params) {
        String desc = ErrorMessageManager.instance().getErrorDescription(null, errorCode.getErrorCode(), params);
        if (desc == null) {
            desc = ErrorMessageManager.instance().resolveDescription(null, errorCode.getDescription(), params);
        }
        return desc;
    }

    /**
     * 根据Schema类型设置验证规则
     */
    private static void setValidationRules(ExcelDataValidation validation, ISchema schema,
                                           Map<String, Object> params) {
        // 优先处理字典验证
        if (schema.getDict() != null) {
            setDictValidation(validation, schema);
            return;
        }

        // 处理正则表达式验证
        String formula = (String) schema.prop_get(KEY_EXCEL_VALIDATION_FORMULA);
        if (formula != null) {
            setCustomValidation(validation, formula, params);
            return;
        }

        // 根据数据类型设置验证
        StdDataType stdDataType = schema.getStdDataType();
        if (stdDataType != null) {
            switch (stdDataType) {
                case INT:
                case LONG:
                case BYTE:
                case SHORT:
                case BIGINT:
                    setIntegerValidation(validation, schema, params);
                    break;
                case FLOAT:
                case DOUBLE:
                case DECIMAL:
                    setDecimalValidation(validation, schema, params);
                    break;
                case DATE:
                    setDateValidation(validation, schema, params);
                    break;
                case DATETIME:
                case TIMESTAMP:
                    setDateTimeValidation(validation, schema, params);
                    break;
                case TIME:
                    setTimeValidation(validation, schema, params);
                    break;
                case STRING:
                case CHAR:
                    setTextValidation(validation, schema, params);
                    break;
                case BOOLEAN:
                    setBooleanValidation(validation, schema, params);
                    break;
                default:
                    // 默认文本验证
                    setTextValidation(validation, schema, params);
                    break;
            }
        }
    }

    /**
     * 设置字典验证
     */
    private static void setDictValidation(ExcelDataValidation validation, ISchema schema) {
        String dictName = schema.getDict();
        DictBean dict = DictProvider.instance().getDict(null, dictName, null, null);

        if (dict != null && dict.getOptions() != null && !dict.getOptions().isEmpty()) {
            validation.setType(ExcelDataValidationType.LIST);
            validation.setListOptions(dict.getStringValues());
        }
    }

    /**
     * 设置正则表达式验证
     */
    private static void setCustomValidation(ExcelDataValidation validation, String formula, Map<String, Object> params) {

        if (formula != null) {
            validation.setType(ExcelDataValidationType.CUSTOM);
            validation.setFormula1(formula);

            String errorMsg = resolveError(ExcelErrors.ERR_EXCEL_VALIDATION_PATTERN_MISMATCH, params);
            validation.setError(errorMsg);
        }
    }


    /**
     * 设置整数验证
     */
    private static void setIntegerValidation(ExcelDataValidation validation, ISchema schema, Map<String, Object> params) {
        validation.setType(ExcelDataValidationType.WHOLE);
        Number min = schema.getMin();
        Number max = schema.getMax();

        StdDataType stdDataType = schema.getStdDataType();
        if (stdDataType == null)
            stdDataType = StdDataType.INT;

        if (min == null) {
            switch (stdDataType) {
                case BYTE:
                    min = (double) Byte.MIN_VALUE;
                    break;
                case SHORT:
                    min = (double) Short.MIN_VALUE;
                    break;
                case INT:
                    min = (double) Integer.MIN_VALUE;
                    break;
                case LONG:
                case BIGINT:
                    min = (double) Long.MIN_VALUE;
                    break;
                default:
                    min = MIN_INTEGER_VALUE;
            }
        }

        // 如果没有指定最大值，根据数据类型设置相应的最大值
        if (max == null) {
            switch (stdDataType) {
                case BYTE:
                    max = (double) Byte.MAX_VALUE;
                    break;
                case SHORT:
                    max = (double) Short.MAX_VALUE;
                    break;
                case INT:
                    max = (double) Integer.MAX_VALUE;
                    break;
                case LONG:
                case BIGINT:
                    max = (double) Long.MAX_VALUE;
                    break;
                default:
                    // 保持原有的默认行为，不设置最大值
            }
        }

        // 设置最小值
        validation.setOperator(ExcelDataValidationOperator.GREATER_THAN_OR_EQUAL);
        validation.setFormula1(String.valueOf(min.longValue()));

        // 设置最大值
        if (max != null) {
            validation.setOperator(ExcelDataValidationOperator.BETWEEN);
            validation.setFormula2(String.valueOf(max.longValue()));
        }

        String errorMsg = resolveError(ExcelErrors.ERR_EXCEL_VALIDATION_INTEGER_REQUIRED, params);
        validation.setError(errorMsg);
    }

    /**
     * 设置小数验证
     */
    private static void setDecimalValidation(ExcelDataValidation validation, ISchema schema, Map<String, Object> params) {
        validation.setType(ExcelDataValidationType.DECIMAL);

        Double min = schema.getMin();
        if (min == null)
            min = MIN_INTEGER_VALUE;

        // 设置最小值
        validation.setOperator(ExcelDataValidationOperator.GREATER_THAN_OR_EQUAL);
        validation.setFormula1(String.valueOf(min));

        // 设置最大值
        if (schema.getMax() != null) {
            validation.setOperator(ExcelDataValidationOperator.BETWEEN);
            validation.setFormula2(String.valueOf(schema.getMax()));
        }

        // 设置精度
        if (schema.getPrecision() != null && schema.getScale() != null) {
            String errorMsg = resolveError(ExcelErrors.ERR_EXCEL_VALIDATION_DECIMAL_PRECISION_INVALID, params);
            validation.setError(errorMsg);
        } else {
            String errorMsg = resolveError(ExcelErrors.ERR_EXCEL_VALIDATION_DECIMAL_REQUIRED, params);
            validation.setError(errorMsg);
        }
    }

    /**
     * 设置日期验证
     */
    private static void setDateValidation(ExcelDataValidation validation, ISchema schema, Map<String, Object> params) {
        validation.setType(ExcelDataValidationType.DATE);

        // 设置日期范围
        Object minValue = schema.getMinValue();
        if (minValue == null)
            minValue = DateHelper.MIN_EXCEL_DATE;

        validation.setOperator(ExcelDataValidationOperator.GREATER_THAN_OR_EQUAL);
        validation.setFormula1(formulaDateForExcel(minValue));

        if (schema.getMaxValue() != null) {
            validation.setOperator(ExcelDataValidationOperator.BETWEEN);
            validation.setFormula2(formulaDateForExcel(schema.getMaxValue()));
        }

        String errorMsg = resolveError(ExcelErrors.ERR_EXCEL_VALIDATION_DATE_REQUIRED, params);
        validation.setError(errorMsg);
    }

    /**
     * 设置日期时间验证
     */
    private static void setDateTimeValidation(ExcelDataValidation validation, ISchema schema, Map<String, Object> params) {
        Object minValue = schema.getMinValue();
        if (minValue == null)
            minValue = DateHelper.MIN_EXCEL_DATE_TIME;

        // 日期时间验证使用自定义公式
        validation.setType(ExcelDataValidationType.CUSTOM);

        // 非常奇怪的做法，公式中只要引用第一个单元格就会自动作用于每个单元格
        String firstCell = validation.getFirstCellRef();

        StringBuilder formula = new StringBuilder();
        formula.append("=AND(ISNUMBER(").append(firstCell).append(')');

        String min = formatDateTimeForExcel(minValue);
        formula.append(",").append(firstCell).append(">=").append(min);

        if (schema.getMaxValue() != null) {
            String max = formatDateTimeForExcel(schema.getMaxValue());
            formula.append(",").append(firstCell).append("<=").append(max);
        }

        formula.append(")");
        validation.setFormula1(formula.toString());

        String errorMsg = resolveError(ExcelErrors.ERR_EXCEL_VALIDATION_DATETIME_REQUIRED, params);
        validation.setError(errorMsg);
    }

    /**
     * 设置时间验证
     */
    private static void setTimeValidation(ExcelDataValidation validation, ISchema schema, Map<String, Object> params) {
        validation.setType(ExcelDataValidationType.TIME);

        Object minValue = schema.getMinValue();
        if (minValue == null)
            minValue = LocalTime.MIN;

        // 设置时间范围
        validation.setOperator(ExcelDataValidationOperator.GREATER_THAN_OR_EQUAL);
        validation.setFormula1(formulaTimeForExcel(minValue));

        if (schema.getMaxValue() != null) {
            validation.setOperator(ExcelDataValidationOperator.BETWEEN);
            validation.setFormula2(formulaTimeForExcel(schema.getMaxValue()));
        }

        String errorMsg = resolveError(ExcelErrors.ERR_EXCEL_VALIDATION_TIME_REQUIRED, params);
        validation.setError(errorMsg);
    }

    /**
     * 设置文本验证
     */
    private static void setTextValidation(ExcelDataValidation validation, ISchema schema, Map<String, Object> params) {
        // 设置文本长度验证
        if (schema.getMinLength() != null || schema.getMaxLength() != null) {
            validation.setType(ExcelDataValidationType.TEXT_LENGTH);
            if (schema.getMinLength() != null && schema.getMaxLength() != null) {
                validation.setOperator(ExcelDataValidationOperator.BETWEEN);
                validation.setFormula1(String.valueOf(schema.getMinLength()));
                validation.setFormula2(String.valueOf(schema.getMaxLength()));
            } else if (schema.getMinLength() != null) {
                validation.setOperator(ExcelDataValidationOperator.GREATER_THAN_OR_EQUAL);
                validation.setFormula1(String.valueOf(schema.getMinLength()));
            } else {
                validation.setOperator(ExcelDataValidationOperator.LESS_THAN_OR_EQUAL);
                validation.setFormula1(String.valueOf(schema.getMaxLength()));
            }

            String errorMsg = resolveError(ExcelErrors.ERR_EXCEL_VALIDATION_TEXT_LENGTH_INVALID, params);
            validation.setError(errorMsg);
        } else if (schema.getStdDataType() == StdDataType.CHAR) {
            // 对于Char类型，如果没有指定长度，则默认限制为1个字符
            validation.setType(ExcelDataValidationType.TEXT_LENGTH);
            validation.setOperator(ExcelDataValidationOperator.EQUAL);
            validation.setFormula1("1");
            
            String errorMsg = resolveError(ExcelErrors.ERR_EXCEL_VALIDATION_TEXT_LENGTH_INVALID, params);
            validation.setError(errorMsg);
        }
    }

    /**
     * 设置布尔值验证
     */
    private static void setBooleanValidation(ExcelDataValidation validation, ISchema schema, Map<String, Object> params) {
        validation.setType(ExcelDataValidationType.LIST);
        validation.setFormula1("\"TRUE,FALSE\"");
        String errorMsg = resolveError(ExcelErrors.ERR_EXCEL_VALIDATION_BOOLEAN_REQUIRED, params);
        validation.setError(errorMsg);
    }

    /**
     * 格式化日期为Excel公式格式
     */
    private static String formulaDateForExcel(Object value) {
        LocalDate date = ConvertHelper.toLocalDate(value);
        return "=DATE(" + date.getYear() + "," +
                date.getMonthValue() + "," +
                date.getDayOfMonth() + ")";
    }

    /**
     * 格式化时间为Excel公式格式
     */
    private static String formulaTimeForExcel(Object value) {
        LocalTime time = ConvertHelper.toLocalTime(value, NopEvalException::new);
        return "=TIME(" + time.getHour() + "," +
                time.getMinute() + "," +
                time.getSecond() + ")";

    }

    /**
     * 格式化日期时间为Excel公式格式
     */
    private static String formatDateTimeForExcel(Object value) {
        LocalDateTime dateTime = ConvertHelper.toLocalDateTime(value);
        return "DATE(" + dateTime.getYear() + "," +
                dateTime.getMonthValue() + "," +
                dateTime.getDayOfMonth() + ")+TIME(" +
                dateTime.getHour() + "," +
                dateTime.getMinute() + "," +
                dateTime.getSecond() + ")";
    }
}
