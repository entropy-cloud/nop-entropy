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
package io.nop.ooxml.xlsx.model;

import java.util.HashMap;
import java.util.Map;

public enum ThemeElement {
    LT1(0, "lt1"),
    DK1(1, "dk1"),
    LT2(2, "lt2"),
    DK2(3, "dk2"),
    ACCENT1(4, "accent1"),
    ACCENT2(5, "accent2"),
    ACCENT3(6, "accent3"),
    ACCENT4(7, "accent4"),
    ACCENT5(8, "accent5"),
    ACCENT6(9, "accent6"),
    HLINK(10, "hlink"),
    FOLHLINK(11, "folHlink");

    public static ThemeElement fromIndex(int idx) {
        if (idx >= values().length || idx < 0) return null;
        return values()[idx];
    }

    ThemeElement(int idx, String name) {
        this.idx = idx;
        this.name = name;
    }

    private final int idx;
    private final String name;

    public int getIdx() {
        return idx;
    }

    public String getName() {
        return name;
    }

    static final Map<String, Integer> indexMap = new HashMap<>();

    static {
        for (ThemeElement element : values()) {
            if (element.getName() != null) {
                indexMap.put(element.getName(), element.getIdx());
            }
        }
    }

    public static int getIndex(String name) {
        Integer idx = indexMap.get(name);
        return idx == null ? -1 : idx;
    }
}