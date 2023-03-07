/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.model.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;

import java.util.HashMap;
import java.util.Map;

public enum ExcelErrorValue {
    /**
     * Intended to indicate when two areas are required to intersect, but do not.
     * <p>
     * Example: In the case of SUM(B1 C1), the space between B1 and C1 is treated as the binary intersection operator,
     * when a comma was intended. end example]
     * </p>
     */
    NULL("#NULL!"),

    /**
     * Intended to indicate when a cell reference is invalid.
     * <p>
     * Example: If a formula contains a reference to a cell, and then the row or column containing that cell is deleted,
     * a #REF! error results. If a worksheet does not support 20,001 columns, OFFSET(A1,0,20000) will result in a #REF!
     * error.
     * </p>
     */
    REF("#REF!"), //

    /**
     * Intended to indicate when any number, including zero, is divided by zero. Note: However, any error code divided
     * by zero results in that error code.
     */
    DIV0("#DIV/0!"),

    /**
     * Intended to indicate when an argument to a function has a compatible type, but has a value that is outside the
     * domain over which that function is defined. (This is known as a domain error.)
     * <p>
     * Example: Certain calls to ASIN, ATANH, FACT, and SQRT might result in domain errors.
     * </p>
     * Intended to indicate that the result of a function cannot be represented in a value of the specified type,
     * typically due to extreme magnitude. (This is known as a range error.)
     * <p>
     * Example: FACT(1000) might result in a range error.
     * </p>
     */
    NUM("#NUM!"),
    /**
     * Intended to indicate when a designated value is not available.
     * <p>
     * Example: Some functions, such as SUMX2MY2, perform a series of operations on corresponding elements in two
     * arrays. If those arrays do not have the same number of elements, then for some elements in the longer array,
     * there are no corresponding elements in the shorter one; that is, one or more values in the shorter array are not
     * available.
     * </p>
     * This error value can be produced by calling the function NA
     */
    NA("#N/A"), //

    /**
     * Intended to indicate when what looks like a name is used, but no such name has been defined.
     * <p>
     * Example: XYZ/3, where XYZ is not a defined name. Total is & A10, where neither Total nor is is a defined name.
     * Presumably, "Total is " & A10 was intended. SUM(A1C10), where the range A1:C10 was intended.
     * </p>
     */
    NAME("#NAME?"), //

    /**
     * Intended to indicate when an incompatible type argument is passed to a function, or an incompatible type operand
     * is used with an operator.
     * <p>
     * Example: In the case of a function argument, text was expected, but a number was provided
     * </p>
     */
    VALUE("#VALUE!"),

    ERROR("#ERROR!");

    String text;

    ExcelErrorValue(String text) {
        this.text = text;
    }

    public String toString() {
        return text;
    }

    public String getText() {
        return text;
    }

    private static final Map<String, ExcelErrorValue> textMap = new HashMap<String, ExcelErrorValue>();

    static {
        for (ExcelErrorValue value : values()) {
            textMap.put(value.getText(), value);
        }
    }

    @StaticFactoryMethod
    public static ExcelErrorValue fromText(String text) {
        if (text == null)
            return null;

        return textMap.get(text);
    }
}