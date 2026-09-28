package io.nop.jpath;

import java.util.Set;

/**
 * Abstract property accessor that shields JsonPath/jq from differences between Map,
 * DynamicObject, and JavaBean.
 */
public interface JsonAccessor {

    /**
     * Get a property value from the target object.
     *
     * @return property value, or null if not found
     */
    Object getProperty(Object target, String propName);

    /**
     * Set a property value on the target object.
     */
    void setProperty(Object target, String propName, Object value);

    /**
     * Check if the target has the named property.
     */
    boolean hasProperty(Object target, String propName);

    /**
     * Get all property names of the target.
     */
    Set<String> getPropertyNames(Object target);

    /**
     * Check if the target is a map-like object (Map, DynamicObject, or bean).
     */
    boolean isMap(Object target);

    /**
     * Check if the target is an array-like object (Collection or array).
     */
    boolean isArray(Object target);

    /**
     * Get the size of an array or map-like object.
     */
    int size(Object target);

    /**
     * Get an array element by index.
     */
    Object getArrayItem(Object target, int index);

    /**
     * Set an array element by index.
     */
    void setArrayItem(Object target, int index, Object value);

    /**
     * Get the type name for error messages.
     */
    String getTypeName(Object target);
}
