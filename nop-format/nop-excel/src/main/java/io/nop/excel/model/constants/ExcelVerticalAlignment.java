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
 * This enumeration value indicates the type of vertical alignment for a cell, i.e., whether it is aligned top, bottom,
 * vertically centered, justified or distributed.
 */
public enum ExcelVerticalAlignment implements IExcelEnumValue {
    /**
     * The vertical alignment is aligned-to-top.
     */
    TOP("top"),

    /**
     * The vertical alignment is centered across the height of the cell.
     */
    CENTER("center", "middle", "center"),

    /**
     * The vertical alignment is aligned-to-bottom. (typically the default value)
     */
    BOTTOM("bottom"),

    /**
     * <p>
     * When text direction is horizontal: the vertical alignment of lines of text is distributed vertically, where each
     * line of text inside the cell is evenly distributed across the height of the cell, with flush top and bottom
     * margins.
     * </p>
     * <p>
     * When text direction is vertical: similar behavior as horizontal justification. The alignment is justified (flush
     * top and bottom in this case). For each line of text, each line of the wrapped text in a cell is aligned to the
     * top and bottom (except the last line). If no single line of text wraps in the cell, then the text is not
     * justified.
     * </p>
     */
    JUSTIFY("justify"),

    /**
     * <p>
     * When text direction is horizontal: the vertical alignment of lines of text is distributed vertically, where each
     * line of text inside the cell is evenly distributed across the height of the cell, with flush top
     * </p>
     * <p>
     * When text direction is vertical: behaves exactly as distributed horizontal alignment. The first words in a line
     * of text (appearing at the top of the cell) are flush with the top edge of the cell, and the last words of a line
     * of text are flush with the bottom edge of the cell, and the line of text is distributed evenly from top to
     * bottom.
     * </p>
     */
    DISTRIBUTED("distributed");

    String excelText;
    String cssText;
    String wmlText;

    ExcelVerticalAlignment(String text) {
        this.cssText = text.toLowerCase();
        this.excelText = text;
        this.wmlText = this.cssText;
    }

    ExcelVerticalAlignment(String text, String cssText, String wmlText) {
        this.excelText = text;
        this.cssText = cssText;
        this.wmlText = wmlText;
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

    public short getCode() {
        return (short) ordinal();
    }

    public static ExcelVerticalAlignment forInt(int code) {
        if (code < 0 || code >= values().length) {
            throw new IllegalArgumentException("Invalid VerticalAlignment code: " + code);
        }
        return values()[code];
    }

    static ExcelEnumMap<ExcelVerticalAlignment> s_map = new ExcelEnumMap<ExcelVerticalAlignment>(values());

    @StaticFactoryMethod
    public static ExcelVerticalAlignment fromExcelText(String text) {
        return s_map.fromExcelText(text);
    }

    public static ExcelVerticalAlignment fromCssText(String text) {
        return s_map.fromCssText(text);
    }

    public static ExcelVerticalAlignment fromWmlText(String text) {
        return s_map.fromWmlText(text);
    }
}