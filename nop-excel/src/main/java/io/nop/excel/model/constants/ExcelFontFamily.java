package io.nop.excel.model.constants;

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

import io.nop.api.core.annotations.core.StaticFactoryMethod;

import java.util.HashMap;
import java.util.Map;

/**
 * The font family this font belongs to. A font family is a set of fonts having common stroke width and serif
 * characteristics. The font name overrides when there are conflicting values.
 *
 * @author Gisella Bronzetti
 */
public enum ExcelFontFamily {

    // NOT_APPLICABLE(0,null), //
    ROMAN(1, "roman"), //
    SWISS(2, "swiss"), //
    MODERN(3, "modern"), //
    SCRIPT(4, "script"), //
    DECORATIVE(5, "decorative");

    private final int family;
    private final String text;

    ExcelFontFamily(int value, String excelText) {
        family = value;
        this.text = excelText;
    }

    /**
     * Returns index of this font family
     *
     * @return index of this font family
     */
    public int getValue() {
        return family;
    }

    public String getText() {
        return text;
    }

    public String toString() {
        return text;
    }

    static final Map<String, ExcelFontFamily> s_map = new HashMap<>();

    static {
        for (ExcelFontFamily family : values()) {
            s_map.put(family.getText(), family);
        }
    }

    @StaticFactoryMethod
    public static ExcelFontFamily fromText(String text) {
        return s_map.get(text);
    }

    public static ExcelFontFamily fromCode(int family) {
        if (family < 1 || family > 5)
            return null;
        return values()[family - 1];
    }
}