/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Helper class for safely setting properties on chart model objects.
 * Provides type conversion and error handling for chart model property setting.
 */
public class ChartPropertyHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChartPropertyHelper.class);
    
    /**
     * Safely sets a property on a model object using reflection with comprehensive error handling.
     * This method provides robust property setting for chart model population, handling cases
     * where property setters don't exist, type conversion fails, or access is denied.
     * 
     * @param model the model object to set the property on (must not be null)
     * @param propertyName the name of the property (will be converted to setter method name)
     * @param value the value to set (will be converted to appropriate type)
     * @param valueType the expected type of the value for conversion
     * @return true if the property was set successfully, false otherwise
     */
    public static boolean setModelPropertySafely(Object model, String propertyName, Object value, Class<?> valueType) {
        if (model == null || propertyName == null) {
            return false;
        }
        
        try {
            // Convert property name to setter method name
            String setterName = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            
            // Convert value to target type
            Object convertedValue = convertValueSafely(value, valueType, propertyName);
            if (convertedValue == null && value != null) {
                LOG.debug("ChartPropertyHelper.setModelPropertySafely: value conversion failed for property '{}' on {}", 
                    propertyName, model.getClass().getSimpleName());
                return false;
            }
            
            // Find and invoke setter method
            Method setter = model.getClass().getMethod(setterName, valueType);
            setter.invoke(model, convertedValue);
            LOG.debug("ChartPropertyHelper.setModelPropertySafely: successfully set property '{}' to '{}' on {}", 
                propertyName, convertedValue, model.getClass().getSimpleName());
            return true;
            
        } catch (NoSuchMethodException e) {
            LOG.debug("ChartPropertyHelper.setModelPropertySafely: no setter method for property '{}' on {}: {}", 
                propertyName, model.getClass().getSimpleName(), e.getMessage());
            return false;
        } catch (IllegalAccessException e) {
            LOG.debug("ChartPropertyHelper.setModelPropertySafely: access denied for property '{}' on {}: {}", 
                propertyName, model.getClass().getSimpleName(), e.getMessage());
            return false;
        } catch (java.lang.reflect.InvocationTargetException e) {
            LOG.warn("ChartPropertyHelper.setModelPropertySafely: error invoking setter for property '{}' on {}: {}", 
                propertyName, model.getClass().getSimpleName(), e.getCause().getMessage());
            return false;
        } catch (Exception e) {
            LOG.warn("ChartPropertyHelper.setModelPropertySafely: unexpected error setting property '{}' on {}: {}", 
                propertyName, model.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Safely converts a value to the target type with appropriate error handling.
     */
    private static Object convertValueSafely(Object value, Class<?> targetType, String propertyName) {
        if (value == null) {
            return null;
        }
        
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        
        try {
            if (targetType == String.class) {
                return value.toString();
            }
            
            if (targetType == Boolean.class || targetType == boolean.class) {
                return convertToBoolean(value);
            }
            
            if (targetType == Integer.class || targetType == int.class) {
                return convertToInteger(value);
            }
            
            if (targetType == Long.class || targetType == long.class) {
                return convertToLong(value);
            }
            
            if (targetType == Double.class || targetType == double.class) {
                return convertToDouble(value);
            }
            
            if (targetType == Float.class || targetType == float.class) {
                return convertToFloat(value);
            }
            
            if (targetType.isEnum()) {
                return convertToEnum(value, targetType);
            }
            
            LOG.debug("ChartPropertyHelper.convertValueSafely: no specific conversion for {} to {}, using toString", 
                value.getClass().getSimpleName(), targetType.getSimpleName());
            return value.toString();
            
        } catch (Exception e) {
            LOG.warn("ChartPropertyHelper.convertValueSafely: error converting '{}' to {} for property '{}': {}", 
                value, targetType.getSimpleName(), propertyName, e.getMessage());
            return null;
        }
    }
    
    private static Boolean convertToBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        
        String str = value.toString().toLowerCase().trim();
        switch (str) {
            case "1":
            case "true":
            case "yes":
            case "on":
                return Boolean.TRUE;
            case "0":
            case "false":
            case "no":
            case "off":
                return Boolean.FALSE;
            default:
                return null;
        }
    }
    
    private static Integer convertToInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private static Long convertToLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private static Double convertToDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private static Float convertToFloat(Object value) {
        if (value == null) return null;
        if (value instanceof Float) return (Float) value;
        if (value instanceof Number) return ((Number) value).floatValue();
        
        try {
            return Float.parseFloat(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private static Object convertToEnum(Object value, Class<?> enumType) {
        if (value == null) return null;
        if (enumType.isAssignableFrom(value.getClass())) return value;
        
        String enumName = value.toString().trim();
        
        try {
            return Enum.valueOf((Class<Enum>) enumType, enumName);
        } catch (IllegalArgumentException e) {
            for (Object enumConstant : enumType.getEnumConstants()) {
                if (((Enum<?>) enumConstant).name().equalsIgnoreCase(enumName)) {
                    return enumConstant;
                }
            }
            return null;
        }
    }
    
    /**
     * Validates and normalizes a color value.
     */
    public static String validateAndNormalizeColor(String color) {
        if (color == null || color.trim().isEmpty()) {
            return null;
        }
        
        String normalizedColor = color.trim();
        
        if (!normalizedColor.startsWith("#")) {
            normalizedColor = "#" + normalizedColor;
        }
        
        if (normalizedColor.length() == 7 && normalizedColor.matches("#[0-9A-Fa-f]{6}")) {
            return normalizedColor.toUpperCase();
        }
        
        if (normalizedColor.length() == 4 && normalizedColor.matches("#[0-9A-Fa-f]{3}")) {
            String expanded = "#" + 
                normalizedColor.charAt(1) + normalizedColor.charAt(1) +
                normalizedColor.charAt(2) + normalizedColor.charAt(2) +
                normalizedColor.charAt(3) + normalizedColor.charAt(3);
            return expanded.toUpperCase();
        }
        
        return null;
    }
    
    /**
     * Validates a numeric value and ensures it's within reasonable bounds.
     */
    public static Double validateNumericRange(Double value, double min, double max, String propertyName) {
        if (value == null) {
            return null;
        }
        
        if (value < min || value > max) {
            LOG.warn("ChartPropertyHelper.validateNumericRange: value {} for property '{}' is out of range [{}, {}]", 
                value, propertyName, min, max);
            return null;
        }
        
        return value;
    }
    
    /**
     * Validates a string value and ensures it's not null or empty.
     */
    public static String validateStringValue(String value, String propertyName) {
        if (value == null) {
            return null;
        }
        
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            LOG.debug("ChartPropertyHelper.validateStringValue: empty string value for property '{}'", propertyName);
            return null;
        }
        
        return trimmed;
    }
}