package io.nop.excel.model.constants;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

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
 * dashed 为较粗的虚线，dotted为较细的虚线
 */
public enum ExcelLineStyle implements IExcelEnumValue {

    /**
     * No border (default)
     */
    NONE(0x0, "none", "none", "none"),

    /**
     * Thin border
     */
    SINGLE(0x1, "thin", "solid", "single"),

    /**
     * Medium border
     */
    MEDIUM(0x2, "medium", "solid", "medium"),

    /**
     * dash border
     */
    DASHED(0x3, "dashed", "dashed", "dashed"),

    /**
     * dot border
     */
    DOTTED(0x4, "dotted", "dotted", "dotted"),

    /**
     * Thick border
     */
    THICK(0x5, "thick", "solid", "thick"),

    /**
     * double-line border
     */
    DOUBLE(0x6, "double", "double", "double"),

    /**
     * hair-line border
     */
    HAIR(0x7, "hair", "dotted", "dotted"),

    /**
     * Medium dashed border
     */
    // MEDIUM_DASHED(0x8, "MediumDashed", "dashed solid", "mediumdashed"),

    /**
     * dash-dot border
     */
    DASH_DOT(0x9, "dashDot", "dashed", "dotDash"),

    /**
     * medium dash-dot border
     */
    MEDIUM_DASH_DOT(0xA, "mediumDashDot", "dashed dotted", "mediumdashdot"),

    /**
     * dash-dot-dot border
     */
    DASH_DOT_DOT(0xB, "dashDotDot", "dashed", "dotDotDash"),

    /**
     * medium dash-dot-dot border
     */
    MEDIUM_DASH_DOT_DOT(0xC, "mediumDashDotDot", "dashed dotted",
            "mediumdashdotdot"),

    /**
     * slanted dash-dot border
     */
    SLANTED_DASH_DOT(0xD, "slantDashDot", "dashed", "dotDash");

    private final short code;

    String cssText;
    String excelText;
    String wmlText;

    ExcelLineStyle(int code, String excelText, String cssText, String wmlText) {
        this.code = (short) code;
        this.cssText = cssText;
        this.excelText = excelText;
        this.wmlText = wmlText;
    }

    public String getCssText() {
        return cssText;
    }

    public String getExcelText() {
        return excelText;
    }

    public String getWmlText() {
        return wmlText;
    }

    public short getCode() {
        return code;
    }

    private static final ExcelLineStyle[] _table = new ExcelLineStyle[0xD + 1];
    private static final short[] _weight = new short[0xD + 1];

    static {
        for (ExcelLineStyle c : values()) {
            _table[c.getCode()] = c;
            _weight[c.getCode()] = 1;
        }

        _table[0x2] = SINGLE;
        _weight[0x2] = 2;
        _table[0x5] = SINGLE;
        _weight[0x5] = 3;
        _table[0x8] = DASHED;
        _weight[0x8] = 2;
        _table[0xA] = DASH_DOT;
        _weight[0xA] = 2;
        _table[0xC] = DASH_DOT_DOT;
        _weight[0xC] = 2;
    }

    public int getWeight() {
        return _weight[getCode()];
    }

    public String toString() {
        return excelText;
    }

    public static ExcelLineStyle fromCode(short code) {
        return _table[code];
    }

    public static int getWeightFromCode(short code) {
        return _weight[code];
    }

    private static ExcelEnumMap<ExcelLineStyle> s_map = new ExcelEnumMap<ExcelLineStyle>(values());

    @StaticFactoryMethod
    public static ExcelLineStyle fromExcelText(String text) {
        if (StringHelper.isEmpty(text))
            return NONE;
        return s_map.fromExcelText(text);
    }

    public static ExcelLineStyle fromCssText(String text) {
        return s_map.fromCssText(text);
    }

    public static ExcelLineStyle fromWmlText(String text) {
        return s_map.fromWmlText(text);
    }
}
