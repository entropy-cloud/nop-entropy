/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.convert;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Function;

import static io.nop.api.core.ApiErrors.ARG_VALUE;
import static io.nop.api.core.util.ApiStringHelper.isAllDigit;
import static io.nop.api.core.util.ApiStringHelper.isAllZero;
import static io.nop.api.core.util.ApiStringHelper.isEmpty;
import static io.nop.api.core.util.ApiStringHelper.isEmptyObject;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@SuppressWarnings("PMD.TooManyStaticImports")
public class ConvertHelper {
    static final Logger LOG = LoggerFactory.getLogger(ConvertHelper.class);

    private static Map<Class<?>, Object> s_primitiveDefaults = new IdentityHashMap<>();
    private static Map<Class<?>, Class<?>> s_primitiveClasses = new IdentityHashMap<>();
    private static Map<String, Class<?>> s_nameToPrimitiveClasses = new HashMap<>();

    static {
        s_primitiveDefaults.put(boolean.class, false);
        s_primitiveDefaults.put(byte.class, (byte) 0);
        s_primitiveDefaults.put(char.class, (char) 0);
        s_primitiveDefaults.put(short.class, (short) 0);
        s_primitiveDefaults.put(int.class, 0);
        s_primitiveDefaults.put(long.class, 0L);
        s_primitiveDefaults.put(float.class, (float) 0);
        s_primitiveDefaults.put(double.class, (double) 0);

        s_primitiveClasses.put(Boolean.class, boolean.class);
        s_primitiveClasses.put(Byte.class, byte.class);
        s_primitiveClasses.put(Character.class, char.class);
        s_primitiveClasses.put(Short.class, short.class);
        s_primitiveClasses.put(Integer.class, int.class);
        s_primitiveClasses.put(Long.class, long.class);
        s_primitiveClasses.put(Float.class, float.class);
        s_primitiveClasses.put(Double.class, double.class);

        s_nameToPrimitiveClasses.put(boolean.class.getName(), boolean.class);
        s_nameToPrimitiveClasses.put(byte.class.getName(), byte.class);
        s_nameToPrimitiveClasses.put(char.class.getName(), char.class);
        s_nameToPrimitiveClasses.put(short.class.getName(), short.class);
        s_nameToPrimitiveClasses.put(int.class.getName(), int.class);
        s_nameToPrimitiveClasses.put(long.class.getName(), long.class);
        s_nameToPrimitiveClasses.put(float.class.getName(), float.class);
        s_nameToPrimitiveClasses.put(double.class.getName(), double.class);
    }

    public static Object getDefaultValueForType(String className) {
        Class<?> clazz = s_nameToPrimitiveClasses.get(className);
        if (clazz != null)
            return getDefault(clazz);
        return null;
    }

    public static Object getDefault(Class clazz) {
        if (clazz.isPrimitive())
            return s_primitiveDefaults.get(clazz);
        return null;
    }

    public static Class getPrimitiveClass(Class clazz) {
        return s_primitiveClasses.get(clazz);
    }

    public static <T> T convertConfigTo(Class<T> targetType, Object value, Function<ErrorCode, NopException> errorFactory) {
        if (value instanceof String) {
            if (targetType == List.class || targetType == Collection.class)
                return (T) ApiStringHelper.stripedSplit(value.toString(), ',');
            if (targetType == Set.class)
                return (T) new LinkedHashSet<>(ApiStringHelper.stripedSplit(value.toString(), ','));
        }
        return convertTo(targetType, value, errorFactory);
    }

    public static <T> T convertTo(Class<T> clazz, Object o,
                                  Function<ErrorCode, NopException> errorFactory) {
        if (o == null) {
            return (T) getDefault(clazz);
        }

        if (clazz == o.getClass())
            return (T) o;

        ITypeConverter fn = SysConverterRegistry.instance().getConverterByType(clazz);
        if (fn != null)
            return (T) fn.convert(o, errorFactory);

        if (clazz.isAssignableFrom(o.getClass()))
            return (T) o;

        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, clazz, o, errorFactory);
    }

    public static Void toVoid(Object object, Function<ErrorCode, NopException> errorFactory) {
        return null;
    }

    public static byte toPrimitiveByte(Object o, Function<ErrorCode, NopException> errorFactory) {
        Byte ret = toByte(o, errorFactory);
        if (ret == null)
            return (byte) 0;
        return ret;
    }

    public static boolean toPrimitiveBoolean(Object o) {
        return toPrimitiveBoolean(o, NopException::new);
    }

    public static boolean toPrimitiveBoolean(Object o, boolean defaultValue,
                                             Function<ErrorCode, NopException> errorFactory) {
        Boolean b = toBoolean(o, errorFactory);
        if (b == null)
            return defaultValue;
        return b;
    }

    public static boolean toPrimitiveBoolean(Object o, Function<ErrorCode, NopException> errorFactory) {
        Boolean ret = toBoolean(o, errorFactory);
        if (ret == null)
            return false;
        return ret;
    }

    public static char toPrimitiveChar(Object o, Function<ErrorCode, NopException> errorFactory) {
        Character ret = toChar(o, errorFactory);
        if (ret == null)
            return (char) 0;
        return ret;
    }

    public static short toPrimitiveShort(Object o, Function<ErrorCode, NopException> errorFactory) {
        Short ret = toShort(o, errorFactory);
        if (ret == null)
            return (short) 0;
        return ret;
    }

    public static int toPrimitiveInt(Object o, Function<ErrorCode, NopException> errorFactory) {
        Integer ret = toInt(o, errorFactory);
        if (ret == null)
            return 0;
        return ret;
    }

    public static int toPrimitiveInt(Object o, int defaultValue, Function<ErrorCode, NopException> errorFactory) {
        Integer ret = toInt(o, errorFactory);
        if (ret == null)
            return defaultValue;
        return ret;
    }

    public static long toPrimitiveLong(Object o, Function<ErrorCode, NopException> errorFactory) {
        Long ret = toLong(o, errorFactory);
        if (ret == null)
            return 0;
        return ret;
    }

    public static long toPrimitiveLong(Object o, long defaultValue,
                                       Function<ErrorCode, NopException> errorFactory) {
        Long ret = toLong(o, errorFactory);
        if (ret == null)
            return defaultValue;
        return ret;
    }

    public static float toPrimitiveFloat(Object o, Function<ErrorCode, NopException> errorFactory) {
        Float ret = toFloat(o, errorFactory);
        if (ret == null)
            return (float) 0;
        return ret;
    }

    public static double toPrimitiveDouble(Object o, Function<ErrorCode, NopException> errorFactory) {
        Double ret = toDouble(o, errorFactory);
        if (ret == null)
            return 0;
        return ret;
    }

    public static double toPrimitiveDouble(Object o, double defaultValue,
                                           Function<ErrorCode, NopException> errorFactory) {
        Double ret = toDouble(o, errorFactory);
        if (ret == null)
            return defaultValue;
        return ret;
    }


    public static String toString(Object o) {
        return toString(o, (String) null);
    }

    public static String toString(Object o, String defaultValue) {
        if (o == null)
            return defaultValue;
        if (o instanceof MonthDay)
            return monthDayToString((MonthDay) o);
        return o.toString();
    }


    public static Object toObject(Object o, Function<ErrorCode, NopException> errorFactory) {
        return o;
    }


    public static String toString(Object o, Function<ErrorCode, NopException> errorFactory) {
        return toString(o, (String) null);
    }

    public static Character toChar(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;

        if (o instanceof Character)
            return (Character) o;

        if (o instanceof Boolean)
            return booleanToChar((Boolean) o);

        if (o instanceof String)
            return stringToChar(o.toString(), errorFactory);

        if (o instanceof Number) {
            return (char) ((Number) o).intValue();
        }

        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Character.class, o, errorFactory);
    }

    public static boolean toFalsy(Object o) {
        if (o == null)
            return true;

        if (o instanceof Boolean)
            return !((Boolean) o).booleanValue();

        if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            return d == 0 || d == Double.NaN;
        }

        if (o instanceof String) {
            return ((String) o).isEmpty();
        }

        return false;
    }


    /**
     * 按照javascript的规定， false/0/null/""/undefined/NaN为假值
     */
    public static Boolean toFalsy(Object o, Function<ErrorCode, NopException> errorFactory) {
        return toFalsy(o);
    }

    public static boolean toTruthy(Object o) {
        return !toFalsy(o);
    }

    public static Boolean toTruthy(Object o, Function<ErrorCode, NopException> errorFactory) {
        return !toFalsy(o);
    }

    public static Boolean toBoolean(Object o) {
        return toBoolean(o, NopException::new);
    }

    public static Boolean toBoolean(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;

        if (o instanceof Boolean)
            return (Boolean) o;

        if (o instanceof Integer)
            return ((Integer) o) != 0;

        if (o instanceof Long)
            return ((Long) o) != 0;

        if (o instanceof Number) {
            return ((Number) o).doubleValue() != 0;
        }

        if (o instanceof String) {
            return stringToBoolean(o.toString(), errorFactory);
        }

        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Boolean.class, o, errorFactory);
    }

    public static Byte toByte(Object o) {
        return toByte(o, NopException::new);
    }

    public static Byte toByte(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;

        if (o instanceof Number) {
            return ((Number) o).byteValue();
        }

        if (o instanceof Boolean)
            return booleanToByte((Boolean) o);

        if (o instanceof String) {
            return stringToByte(o.toString(), errorFactory);
        }

        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Byte.class, o, errorFactory);
    }

    public static Short toShort(Object o) {
        return toShort(o, NopException::new);
    }

    public static Short toShort(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;

        if (o instanceof Number) {
            return ((Number) o).shortValue();
        }

        if (o instanceof Boolean)
            return booleanToShort((Boolean) o);

        if (o instanceof String) {
            return stringToShort(o.toString(), errorFactory);
        }

        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Short.class, o, errorFactory);
    }

    public static Integer toInteger(Object o, Function<ErrorCode, NopException> errorFactory) {
        return toInt(o, errorFactory);
    }

    public static Character toCharacter(Object o, Function<ErrorCode, NopException> errorFactory) {
        return toChar(o, errorFactory);
    }

    public static Integer toInt(Object o) {
        return toInt(o, NopException::new);
    }

    public static Long toLong(Object o) {
        return toLong(o, NopException::new);
    }

    public static BigInteger toBigInteger(Object o) {
        return toBigInteger(o, NopException::new);
    }

    public static Double toDouble(Object o) {
        return toDouble(o, NopException::new);
    }

    public static BigDecimal toBigDecimal(Object o) {
        return toBigDecimal(o, NopException::new);
    }

    public static Integer toInt(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;

        if (o instanceof Number) {
            return ((Number) o).intValue();
        }

        if (o instanceof Boolean)
            return booleanToInt((Boolean) o);

        if (o instanceof String) {
            return stringToInt(o.toString(), errorFactory);
        }

        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Integer.class, o, errorFactory);
    }

    public static Long toLong(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;

        if (o instanceof Number) {
            return ((Number) o).longValue();
        }

        if (o instanceof Boolean)
            return booleanToLong((Boolean) o);

        if (o instanceof String) {
            return stringToLong(o.toString(), errorFactory);
        }

        if (o instanceof Timestamp)
            return timestampToLong((Timestamp) o);

        if (o instanceof LocalDateTime)
            return localDateTimeToMillis((LocalDateTime) o);

        if (o instanceof LocalDate)
            return localDateToMillis((LocalDate) o);

        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Long.class, o, errorFactory);
    }

    public static Float toFloat(Object o) {
        return toFloat(o, NopException::new);
    }

    public static Float toFloat(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;

        if (o instanceof Number) {
            return ((Number) o).floatValue();
        }

        if (o instanceof Boolean)
            return booleanToFloat((Boolean) o);

        if (o instanceof String) {
            return stringToFloat(o.toString(), errorFactory);
        }

        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Float.class, o, errorFactory);
    }

    public static Double toDouble(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;

        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }

        if (o instanceof Boolean)
            return booleanToDouble((Boolean) o);

        if (o instanceof String) {
            return stringToDouble(o.toString(), errorFactory);
        }

        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Double.class, o, errorFactory);
    }


    public static BigDecimal toBigDecimal(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;

        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }

        if (o instanceof BigInteger)
            return new BigDecimal((BigInteger) o);

        if (o instanceof Integer)
            return BigDecimal.valueOf((Integer) o);

        if (o instanceof Long)
            return BigDecimal.valueOf((Long) o);

        if (o instanceof Short)
            return BigDecimal.valueOf((Short) o);

        if (o instanceof Number)
            return stringToBigDecimal(o.toString(), errorFactory);

        if (o instanceof Boolean)
            return booleanToBigDecimal((Boolean) o);

        if (o instanceof Character)
            return BigDecimal.valueOf((int) (Character) o);

        if (o instanceof String) {
            return stringToBigDecimal(o.toString(), errorFactory);
        }
        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, BigDecimal.class, o, errorFactory);
    }

    public static LocalDate toLocalDate(Object o) {
        return toLocalDate(o, NopException::new);
    }

    public static LocalDate toLocalDate(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof LocalDate)
            return (LocalDate) o;
        if (o instanceof LocalDateTime)
            return ((LocalDateTime) o).toLocalDate();
        if (o instanceof Timestamp)
            return timestampToLocalDate((Timestamp) o);
        if (o instanceof Date)
            return millisToLocalDate(((Date) o).getTime());
        if (o instanceof Long)
            return millisToLocalDate((Long) o);
        if (o instanceof String)
            return stringToLocalDate(o.toString(), errorFactory);

        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, LocalDate.class, o, errorFactory);
    }

    public static LocalTime toLocalTime(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof LocalTime)
            return (LocalTime) o;
        if (o instanceof String)
            return stringToLocalTime(o.toString(), errorFactory);

        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, LocalTime.class, o, errorFactory);
    }

    public static LocalDateTime toLocalDateTime(Object o) {
        return toLocalDateTime(o, NopException::new);
    }

    public static LocalDateTime toLocalDateTime(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof LocalDateTime)
            return (LocalDateTime) o;
        if (o instanceof LocalDate)
            return LocalDateTime.of((LocalDate) o, LocalTime.of(0, 0));
        if (o instanceof Timestamp)
            return timestampToLocalDateTime((Timestamp) o);
        if (o instanceof Date)
            return millisToLocalDateTime(((Date) o).getTime());
        if (o instanceof String)
            return stringToLocalDateTime(o.toString(), errorFactory);
        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, LocalDateTime.class, o, errorFactory);
    }

    public static byte booleanToByte(boolean b) {
        return b ? (byte) 1 : (byte) 0;
    }

    public static Timestamp toTimestamp(Object o) {
        return toTimestamp(o, NopException::new);
    }

    public static Timestamp toTimestamp(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof Timestamp)
            return (Timestamp) o;

        if (o instanceof Long)
            return new Timestamp((Long) o);

        if (o instanceof Date)
            return new Timestamp(((Date) o).getTime());

        if (o instanceof LocalDateTime)
            return localDateTimeToTimestamp((LocalDateTime) o);

        if (o instanceof LocalDate)
            return localDateToTimestamp((LocalDate) o);

        if (o instanceof String) {
            return stringToTimestamp(o.toString(), errorFactory);
        }

        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Timestamp.class, o, errorFactory);
    }

    public static Number toNumber(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof Number)
            return (Number) o;
        if (o instanceof String)
            return stringToNumber(o.toString(), errorFactory);
        if (o instanceof Timestamp)
            return timestampToLong((Timestamp) o);
        if (o instanceof LocalDate)
            return localDateToMillis((LocalDate) o);
        if (o instanceof LocalDateTime)
            return localDateTimeToMillis((LocalDateTime) o);
        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Number.class, o, errorFactory);
    }

    public static String toStripedString(Object o, Function<ErrorCode, NopException> errorFactory) {
        String str = toString(o, errorFactory);
        if (str == null)
            return null;
        return ApiStringHelper.strip(str);
    }

    public static byte[] toBytes(Object o) {
        return toBytes(o, NopException::new);
    }

    public static byte[] toBytes(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof byte[])
            return (byte[]) o;
        if (o instanceof IByteArrayView)
            return ((IByteArrayView) o).toByteArray();
        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, byte[].class, o, errorFactory);
    }

    public static <T> T defaults(Object value, T defaultValue) {
        return value != null ? (T) value : defaultValue;
    }

    public static boolean byteToBoolean(byte value) {
        return value != 0;
    }

    public static char booleanToChar(boolean b) {
        return b ? '1' : '0';
    }

    /**
     * javascript中字符串'1'为true, 字符串'2'为false
     */
    public static boolean charToBoolean(char c) {
        return c == '1' || c == 'y' || c == 'Y';
    }

    public static short booleanToShort(boolean b) {
        return b ? (short) 1 : (short) 0;
    }


    public static boolean shortToBoolean(short value) {
        return value != 0;
    }


    public static int booleanToInt(boolean b) {
        return b ? 1 : 0;
    }

    public static boolean intToBoolean(int value) {
        return value != 0;
    }

    public static long booleanToLong(boolean b) {
        return b ? 1 : 0;
    }

    public static boolean longToBoolean(long value) {
        return value != 0;
    }

    public static float booleanToFloat(boolean b) {
        return b ? (float) 1 : (float) 0;
    }

    public static boolean floatToBoolean(float value) {
        return value != 0;
    }

    public static double booleanToDouble(boolean b) {
        return b ? (double) 1 : (double) 0;
    }

    public static boolean doubleToBoolean(double value) {
        return value != 0;
    }

    public static BigDecimal booleanToBigDecimal(boolean b) {
        return new BigDecimal(booleanToInt(b));
    }

    public static Boolean bigDecimalToBoolean(BigDecimal v) {
        if (v == null)
            return null;
        return v.intValue() != 0;
    }

    public static Boolean stringToBoolean(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;
        boolean b = str.equals("1") || str.equals("true") || str.equalsIgnoreCase("Y");
        if (b)
            return true;
        if (str.equals("0") || str.equals("false") || str.equalsIgnoreCase("n"))
            return false;
        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Boolean.class, str, errorFactory);
    }

    public static Byte stringToByte(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;
        Short value = stringToShort(str, errorFactory);
        if (value == null)
            return null;
        return value.byteValue();
    }

    public static Character stringToChar(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;
        if (str.length() > 1) {
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Character.class, str, errorFactory);
        }
        return str.charAt(0);
    }

    public static Short stringToShort(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;
        try {
            return Short.parseShort(str);
        } catch (Exception e) {
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Short.class, str, errorFactory);
        }
    }

    public static Integer stringToInt(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, e, Integer.class, str, errorFactory);
        }
    }

    public static Long stringToLong(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;

        if (str.charAt(0) != '-') {
            char c = str.charAt(str.length() - 1);
            if (c == 'G' || c == 'g') {
                str = str.substring(0, str.length() - 1);
                long value = (long) (stringToNumber(str, errorFactory).doubleValue() * 1024 * 1024 * 1024L);
                return value;
            } else if (c == 'M' || c == 'm') {
                str = str.substring(0, str.length() - 1);
                long value = (long) (stringToNumber(str, errorFactory).doubleValue() * 1024 * 1024L);
                return value;
            } else if (c == 'K' || c == 'k') {
                str = str.substring(0, str.length() - 1);
                long value = (long) (stringToNumber(str, errorFactory).doubleValue() * 1024L);
                return value;
            }
        }

        try {

            return Long.parseLong(str);
        } catch (Exception e) {
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, e, Long.class, str, errorFactory);
        }
    }

    public static long stringToPrimitiveLong(String str, Function<ErrorCode, NopException> errorFactory) {
        Long value = stringToLong(str, errorFactory);
        if (value == null)
            return 0;
        return value;
    }

    public static Long timestampToLong(Timestamp ts) {
        if (ts == null)
            return null;
        return ts.getTime();
    }

    public static Timestamp longToTimestamp(Long value) {
        if (value == null)
            return null;
        return new Timestamp(value);
    }

    public static Float stringToFloat(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;
        try {
            return Float.parseFloat(str);
        } catch (Exception e) {
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, e, Float.class, str, errorFactory);
        }
    }

    public static Double stringToDouble(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;
        try {
            return Double.parseDouble(str);
        } catch (Exception e) {
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, e, Double.class, str, errorFactory);
        }
    }

    public static BigDecimal stringToBigDecimal(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;
        try {
            return new BigDecimal(str);
        } catch (Exception e) {
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, e, BigDecimal.class, str, errorFactory);
        }
    }

    public static LocalDate stringToLocalDate(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;

        if (str.length() == 19 && str.endsWith(" 00:00:00")) {
            str = str.substring(0, 10);
        }
        try {
            return LocalDate.parse(str, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, e, LocalDate.class, str, errorFactory);
        }
    }

    public static LocalTime stringToLocalTime(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;

        try {
            return LocalTime.parse(str, DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (Exception e) {
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, e, LocalTime.class, str, errorFactory);
        }
    }

    private static DateTimeFormatter INSTANT_FORMAT = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral('T')
            .appendValue(HOUR_OF_DAY, 2).appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2).appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .appendFraction(NANO_OF_SECOND, 0, 9, true)
            .appendLiteral('Z')
            .toFormatter();

    private static DateTimeFormatter TIMESTAMP_FORMAT = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral(' ')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .toFormatter();

    public static LocalDateTime stringToLocalDateTime(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;

        // 仅包含日期部分
        if(str.length() == 10){
            LocalDate date = stringToLocalDate(str,errorFactory);
            return date.atStartOfDay();
        }

        try {
            if (str.endsWith("Z")) {
                return LocalDateTime.parse(str, INSTANT_FORMAT);
            }
            if (str.indexOf('T') > 0)
                return LocalDateTime.parse(str, DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            return LocalDateTime.parse(str, TIMESTAMP_FORMAT);
        } catch (Exception e) {
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, e, LocalDateTime.class, str, errorFactory);
        }
    }

    public static Long localDateTimeToMillis(LocalDateTime value) {
        if (value == null)
            return null;
        long offset = TimeZone.getDefault().getRawOffset();
        return value.toInstant(ZoneOffset.UTC).toEpochMilli() - offset;
    }

    public static LocalDateTime millisToLocalDateTime(Long value) {
        if (value == null)
            return null;

        return new Timestamp(value).toLocalDateTime();
    }

    public static Long localDateToMillis(LocalDate value) {
        if (value == null)
            return null;
        return localDateTimeToMillis(value.atStartOfDay());
    }

    public static LocalDate millisToLocalDate(Long value) {
        if (value == null)
            return null;

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(value);

        return LocalDate.of(cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DATE));
    }

    public static Timestamp localDateTimeToTimestamp(LocalDateTime date) {
        if (date == null)
            return null;
        return Timestamp.valueOf(date);
    }

    public static Timestamp localDateToTimestamp(LocalDate date) {
        if (date == null)
            return null;

        LocalDateTime time = LocalDateTime.of(date, LocalTime.of(0, 0));
        return localDateTimeToTimestamp(time);
    }

    public static Timestamp stringToTimestamp(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;
        if (isAllDigit(str))
            return longToTimestamp(stringToLong(str, errorFactory));

        LocalDateTime dt = stringToLocalDateTime(str, errorFactory);
        return localDateTimeToTimestamp(dt);
    }

    public static LocalDate timestampToLocalDate(Timestamp value) {
        if (value == null)
            return null;
        return value.toLocalDateTime().toLocalDate();
    }

    public static LocalDateTime timestampToLocalDateTime(Timestamp value) {
        if (value == null)
            return null;
        return value.toLocalDateTime();
    }

    public static Number stringToNumber(String val, Function<ErrorCode, NopException> errorFactory) {
        if (val == null || val.length() <= 0)
            return null;

        if (val.startsWith("--")) {
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Number.class, val, errorFactory);
        }

        char lastChar = val.charAt(val.length() - 1);

        if (val.startsWith("0x") || val.startsWith("-0x")) {
            if (lastChar == 'l' || lastChar == 'L') {
                try {
                    return Long.decode(val.substring(0, val.length() - 1));
                } catch (Exception e) {
                    return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, e, Long.class, val, errorFactory);
                }
            }
            try {
                return Integer.decode(val);
            } catch (Exception e) {
                return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, e, Integer.class, val, errorFactory);
            }
        }

        String mant;
        String dec;
        String exp;
        int decPos = val.indexOf('.');
        int expPos = val.indexOf('e') + val.indexOf('E') + 1;

        if (decPos > -1) {
            if (expPos > -1) {
                if (expPos < decPos) {
                    return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Number.class, val, errorFactory);
                }
                dec = val.substring(decPos + 1, expPos);
            } else {
                dec = val.substring(decPos + 1);
            }
            mant = val.substring(0, decPos);
        } else {
            if (expPos > -1) {
                mant = val.substring(0, expPos);
            } else {
                mant = val;
            }
            dec = null;
        }
        if (!Character.isDigit(lastChar)) {
            if (expPos > -1 && expPos < val.length() - 1) {
                exp = val.substring(expPos + 1, val.length() - 1);
            } else {
                exp = null;
            }
            // Requesting a specific type..
            String numeric = val.substring(0, val.length() - 1);
            boolean allZeros = isAllZero(mant) && isAllZero(exp);
            switch (lastChar) {
                case 'l':
                case 'L':
                    if (dec == null && exp == null && isAllDigit(numeric)
                            && (numeric.charAt(0) == '-' || Character.isDigit(numeric.charAt(0)))) {
                        try {
                            return Long.valueOf(numeric);
                        } catch (NumberFormatException nfe) {
                            // Too big for a long
                            LOG.trace("nop.api.try-convert-to-long-fail", nfe);
                        }
                        return new BigInteger(numeric);
                    }
                    return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null,
                            Number.class, numeric, errorFactory);
                case 'f':
                case 'F':
                    try {
                        Float f = Float.valueOf(numeric);
                        if (!(f.isInfinite() || f.floatValue() == 0.0F && !allZeros)) {
                            // If it's too big for a float or the float value = 0
                            // and the string
                            // has non-zeros in it, then float doens't have the
                            // presision we want
                            return f;
                        }

                    } catch (NumberFormatException nfe) {
                        LOG.trace("nop.api.try-convert-to-float-fail", nfe);
                    }
                    // Fall through
                case 'd':
                case 'D':
                    try {
                        Double d = Double.valueOf(numeric);
                        if (!(d.isInfinite() || d.floatValue() == 0.0D && !allZeros)) {
                            return d;
                        }
                    } catch (NumberFormatException nfe) {
                        LOG.trace("nop.api.try-convert-to-double-fail", nfe);
                    }
                    try {
                        return new BigDecimal(numeric);
                    } catch (NumberFormatException e) {
                        LOG.trace("nop.api.try-convert-to-decimal-fail", e);
                    }
                    // Fall through
                default:
                    return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Number.class, val, errorFactory);

            }
        } else {
            // User doesn't have a preference on the return type, so let's start
            // small and go from there...
            if (expPos > -1 && expPos < val.length() - 1) {
                exp = val.substring(expPos + 1, val.length());
            } else {
                exp = null;
            }
            if (dec == null && exp == null) {
                // Must be an int,long,bigint
                try {
                    return Integer.decode(val);
                } catch (NumberFormatException nfe) {
                    LOG.trace("nop.api.try-convert-to-integer-fail", nfe);
                }
                try {
                    return Long.valueOf(val);
                } catch (NumberFormatException nfe) {
                    LOG.trace("nop.api.try-convert-to-long-fail", nfe);
                }
                return new BigInteger(val);

            } else {
                // Must be a float,double,BigDec
                boolean allZeros = isAllZero(mant) && isAllZero(exp);
                try {
                    Double d = Double.valueOf(val);
                    if (!(d.isInfinite() || d.doubleValue() == 0.0D && !allZeros)) {
                        return d;
                    }
                } catch (NumberFormatException nfe) {
                    LOG.trace("nop.api.try-convert-to-double-fail", nfe);
                }
                return new BigDecimal(val);
            }
        }
    }

    public static <T> List<T> toList(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof List)
            return (List<T>) o;
        if (o instanceof Collection)
            return new ArrayList<T>((Collection) o);
        if (o instanceof Object[]) {
            return Arrays.asList((T[]) o);
        }
        if (o instanceof ICollectionView)
            return ((ICollectionView) o).toList();
        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, List.class, o, errorFactory);
    }

    public static <T> Collection<T> toCollection(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof Collection)
            return (Collection) o;
        if (o instanceof Object[]) {
            return Arrays.asList((T[]) o);
        }
        if (o instanceof ICollectionView)
            return ((ICollectionView) o).toCollection();
        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Collection.class, o, errorFactory);
    }

    public static <T> Set<T> toSet(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof Set)
            return (Set<T>) o;
        if (o instanceof Collection)
            return new LinkedHashSet<T>((Collection) o);
        if (o instanceof Object[]) {
            return new LinkedHashSet<>(Arrays.asList((T[]) o));
        }
        if (o instanceof ICollectionView)
            return ((ICollectionView) o).toSet();
        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Set.class, o, errorFactory);
    }

    public static <K, V> Map<K, V> toMap(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof Map)
            return (Map<K, V>) o;
        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Map.class, o, errorFactory);
    }

    public static Set<String> toCsvSet(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof String)
            return toSet(ApiStringHelper.stripedSplit(o.toString(), ','), errorFactory);
        return toSet(o, errorFactory);
    }

    public static Set<String> toCsvSet(Object value) {
        return toCsvSet(value, NopException::new);
    }

    public static List<String> toCsvList(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof String)
            return ApiStringHelper.stripedSplit(o.toString(), ',');
        return toList(o, errorFactory);
    }

    public static BigInteger toBigInteger(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (isEmptyObject(o))
            return null;
        if (o instanceof BigInteger)
            return (BigInteger) o;
        if (o instanceof BigDecimal)
            return ((BigDecimal) o).toBigInteger();
        if (o instanceof Number)
            return BigInteger.valueOf(((Number) o).longValue());
        if (o instanceof String) {
            try {
                return new BigInteger(o.toString());
            } catch (Exception e) {
                return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, e, BigInteger.class, o, errorFactory);
            }
        }
        if (o instanceof Boolean)
            return BigInteger.valueOf(booleanToLong((Boolean) o));

        if (o instanceof Character) {
            return BigInteger.valueOf((int) (Character) o);
        }
        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, BigInteger.class, o, errorFactory);
    }

    public static Duration toDuration(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (isEmptyObject(o))
            return null;

        if (o instanceof Duration)
            return (Duration) o;

        if (o instanceof String) {
            return stringToDuration(o.toString(), errorFactory);
        }

        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Duration.class, o, errorFactory);
    }

    public static <T> T handleError(ErrorCode errorCode, Throwable cause, Class<T> targetType, Object value,
                                    Function<ErrorCode,
                                            NopException> creator) {
        NopException e = creator.apply(errorCode);
        if (e == null) {
            LOG.debug("nop.convert-fail:error={},targetType={},value={}",
                    errorCode.getErrorCode(), targetType, value, cause);
            return null;
        }

        e.cause(cause).param(ARG_VALUE, value);
        if (targetType != null) {
            e.param(ApiErrors.ARG_TARGET_TYPE, targetType.getSimpleName());
        }
        if (value != null) {
            e.param(ApiErrors.ARG_SRC_TYPE, value.getClass());
        }
        throw e;
    }

    public static Duration stringToDuration(String s, Function<ErrorCode, NopException> errorFactory) {
        if (ApiStringHelper.isBlank(s))
            return null;

        if (s.indexOf('P') >= 0) {
            try {
                return Duration.parse(s);
            } catch (Exception e) {
                return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, e, Duration.class, s, errorFactory);
            }
        }

        try {
            if (s.endsWith("ns")) {
                return Duration.ofNanos(stringToPrimitiveLong(s.substring(0, s.length() - 2).trim(),
                        errorFactory));
            }

            if (s.endsWith("us")) {
                return Duration.ofNanos(
                        NANOSECONDS.convert(stringToPrimitiveLong(s.substring(0, s.length() - 2).trim(),
                                        errorFactory),
                                MICROSECONDS));
            }

            if (s.endsWith("ms")) {
                return Duration.ofMillis(stringToPrimitiveLong(s.substring(0, s.length() - 2).trim(),
                        errorFactory));
            }

            if (s.endsWith("s")) {
                return Duration.ofMillis(toMillis(s.substring(0, s.length() - 1).trim(), 1000L, errorFactory));
            }

            if (s.endsWith("m")) {
                return Duration.ofMillis(toMillis(s.substring(0, s.length() - 1).trim(), 1000 * 60L, errorFactory));
            }

            if (s.endsWith("h")) {
                return Duration.ofMillis(toMillis(s.substring(0, s.length() - 1).trim(), 1000 * 60 * 60L, errorFactory));
            }

            if (s.endsWith("d")) {
                return Duration.ofMillis(toMillis(s.substring(0, s.length() - 1).trim(), 1000 * 60 * 60 * 24L, errorFactory));
            }

            return Duration.ofMillis(stringToLong(s, errorFactory));
        } catch (Exception e) {
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, e, Duration.class, s, errorFactory);
        }
    }

    static long toMillis(String s, long factor, Function<ErrorCode, NopException> errorFactory) {
        if (s.indexOf('.') < 0)
            return stringToLong(s, errorFactory) * factor;
        return (long) (stringToDouble(s, errorFactory) * factor);
    }

    public static Iterator<?> toIterator(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof Iterable)
            return ((Iterable) o).iterator();
        if (o instanceof Iterator)
            return (Iterator) o;
        if (o instanceof Object[])
            return Arrays.asList((Object[]) o).iterator();
        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, Iterator.class, o, errorFactory);
    }

    public static String monthDayToString(MonthDay monthDay) {
        StringBuilder sb = new StringBuilder();
        if (monthDay.getMonthValue() < 10) {
            sb.append('0');
        }
        sb.append(monthDay.getMonthValue());
        sb.append('-');
        if (monthDay.getDayOfMonth() < 10) {
            sb.append('0');
        }
        sb.append(monthDay.getMonthValue());
        return sb.toString();
    }

    public static MonthDay toMonthDay(Object o, Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return null;
        if (o instanceof MonthDay)
            return (MonthDay) o;
        if (o instanceof String)
            return stringToMonthDay((String) o, errorFactory);
        return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, MonthDay.class, o, errorFactory);
    }

    public static MonthDay stringToMonthDay(String str, Function<ErrorCode, NopException> errorFactory) {
        if (isEmpty(str))
            return null;

        int pos = str.indexOf('-');
        if (pos < 0)
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, MonthDay.class, str, errorFactory);

        String month = str.substring(0, pos);
        String day = str.substring(pos + 1);
        if (month.compareTo("00") < 0 || month.compareTo("12") > 0
                || day.compareTo("00") < 0 || day.compareTo("31") > 0)
            return handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, MonthDay.class, str, errorFactory);

        int monthValue = Integer.parseInt(month);
        int dayValue = Integer.parseInt(day);

        return MonthDay.of(monthValue, dayValue);
    }
}