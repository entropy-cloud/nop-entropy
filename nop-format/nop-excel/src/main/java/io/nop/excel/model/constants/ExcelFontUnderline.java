package io.nop.excel.model.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;

/* ====================================================================
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==================================================================== */

//package org.apache.poi.ss.usermodel;

/**
 * the different types of possible underline formatting
 *
 * @author Gisella Bronzetti
 */
public enum ExcelFontUnderline implements IExcelEnumValue {

    /**
     * Single-line underlining under each character in the cell. The underline is drawn through the descenders of
     * characters such as g and p..
     */
    SINGLE(1, "single"),

    /**
     * Double-line underlining under each character in the cell. underlines are drawn through the descenders of
     * characters such as g and p.
     */
    DOUBLE(2, "double"),

    /**
     * Single-line accounting underlining under each character in the cell. The underline is drawn under the descenders
     * of characters such as g and p.
     */
    SINGLE_ACCOUNTING(3, "singleAccounting"),

    /**
     * Double-line accounting underlining under each character in the cell. The underlines are drawn under the
     * descenders of characters such as g and p.
     */
    DOUBLE_ACCOUNTING(4, "doubleAccounting"),

    /**
     * No underline.
     */
    NONE(5, "");

    private int value;

    String excelText;
    String cssText;
    String wmlText;

    ExcelFontUnderline(int val, String text) {
        value = val;
        this.excelText = text;
        this.cssText = text.toLowerCase();
        this.wmlText = text.toLowerCase();
    }

    public String toString() {
        return excelText;
    }

    public String getExcelText() {
        return excelText;
    }

    public String getCssText() {
        return cssText;
    }

    public String getWmlText() {
        return wmlText;
    }

    public int getValue() {
        return value;
    }

    public byte getByteValue() {
        switch (this) {
            case DOUBLE:
                return ExcelModelConstants.U_DOUBLE;
            case DOUBLE_ACCOUNTING:
                return ExcelModelConstants.U_DOUBLE_ACCOUNTING;
            case SINGLE_ACCOUNTING:
                return ExcelModelConstants.U_SINGLE_ACCOUNTING;
            case NONE:
                return ExcelModelConstants.U_NONE;
            case SINGLE:
                return ExcelModelConstants.U_SINGLE;
            default:
                return ExcelModelConstants.U_SINGLE;
        }
    }

    private static ExcelFontUnderline[] _table = new ExcelFontUnderline[6];

    static {
        for (ExcelFontUnderline c : values()) {
            _table[c.getValue()] = c;
        }
    }

    public static ExcelFontUnderline fromValue(int value) {
        return _table[value];
    }

    public static ExcelFontUnderline fromByteValue(byte value) {
        ExcelFontUnderline val;
        switch (value) {
            case ExcelModelConstants.U_DOUBLE:
                val = ExcelFontUnderline.DOUBLE;
                break;
            case ExcelModelConstants.U_DOUBLE_ACCOUNTING:
                val = ExcelFontUnderline.DOUBLE_ACCOUNTING;
                break;
            case ExcelModelConstants.U_SINGLE_ACCOUNTING:
                val = ExcelFontUnderline.SINGLE_ACCOUNTING;
                break;
            case ExcelModelConstants.U_SINGLE:
                val = ExcelFontUnderline.SINGLE;
                break;
            default:
                val = ExcelFontUnderline.NONE;
                break;
        }
        return val;
    }

    static ExcelEnumMap<ExcelFontUnderline> s_map = new ExcelEnumMap<>(values());

    static {
        s_map.addExcelText("none", NONE);
    }

    @StaticFactoryMethod
    public static ExcelFontUnderline fromExcelText(String text) {
        return s_map.fromExcelText(text);
    }

    public static ExcelFontUnderline fromCssText(String text) {
        return s_map.fromCssText(text);
    }

    public static ExcelFontUnderline fromWmlText(String text) {
        return s_map.fromWmlText(text);
    }
}
