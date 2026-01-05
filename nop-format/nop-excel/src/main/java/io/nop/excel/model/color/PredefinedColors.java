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
package io.nop.excel.model.color;

import io.nop.commons.util.StringHelper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum PredefinedColors {

    BLACK(0x08, -1, 0x000000),
    BROWN(0x3C, -1, 0x993300),
    OLIVE_GREEN(0x3B, -1, 0x333300),
    DARK_GREEN(0x3A, -1, 0x003300),
    DARK_TEAL(0x38, -1, 0x003366),
    DARK_BLUE(0x12, 0x20, 0x000080),
    INDIGO(0x3E, -1, 0x333399),
    GREY_80_PERCENT(0x3F, -1, 0x333333),
    ORANGE(0x35, -1, 0xFF6600),
    DARK_YELLOW(0x13, -1, 0x808000),
    GREEN(0x11, -1, 0x008000),
    TEAL(0x15, 0x26, 0x008080),
    BLUE(0x0C, 0x27, 0x0000FF),
    BLUE_GREY(0x36, -1, 0x666699),
    GREY_50_PERCENT(0x17, -1, 0x808080),
    RED(0x0A, -1, 0xFF0000),
    LIGHT_ORANGE(0x34, -1, 0xFF9900),
    LIME(0x32, -1, 0x99CC00),
    SEA_GREEN(0x39, -1, 0x339966),
    AQUA(0x31, -1, 0x33CCCC),
    LIGHT_BLUE(0x30, -1, 0x3366FF),
    VIOLET(0x14, 0x24, 0x800080),
    GREY_40_PERCENT(0x37, -1, 0x969696),
    PINK(0x0E, 0x21, 0xFF00FF),
    GOLD(0x33, -1, 0xFFCC00),
    YELLOW(0x0D, 0x22, 0xFFFF00),
    BRIGHT_GREEN(0x0B, -1, 0x00FF00),
    TURQUOISE(0x0F, 0x23, 0x00FFFF),
    DARK_RED(0x10, 0x25, 0x800000),
    SKY_BLUE(0x28, -1, 0x00CCFF),
    PLUM(0x3D, 0x19, 0x993366),
    GREY_25_PERCENT(0x16, -1, 0xC0C0C0),
    ROSE(0x2D, -1, 0xFF99CC),
    LIGHT_YELLOW(0x2B, -1, 0xFFFF99),
    LIGHT_GREEN(0x2A, -1, 0xCCFFCC),
    LIGHT_TURQUOISE(0x29, 0x1B, 0xCCFFFF),
    PALE_BLUE(0x2C, -1, 0x99CCFF),
    LAVENDER(0x2E, -1, 0xCC99FF),
    WHITE(0x09, -1, 0xFFFFFF),
    CORNFLOWER_BLUE(0x18, -1, 0x9999FF),
    LEMON_CHIFFON(0x1A, -1, 0xFFFFCC),
    MAROON(0x19, -1, 0x7F0000),
    ORCHID(0x1C, -1, 0x660066),
    CORAL(0x1D, -1, 0xFF8080),
    ROYAL_BLUE(0x1E, -1, 0x0066CC),
    LIGHT_CORNFLOWER_BLUE(0x1F, -1, 0xCCCCFF),
    TAN(0x2F, -1, 0xFFCC99),

    /**
     * Special Default/Normal/Automatic color.<p>
     * <i>Note:</i> This class is NOT in the default Map returned by HSSFColor.
     * The index is a special case which is interpreted in the various setXXXColor calls.
     */
    AUTOMATIC(0x40, -1, 0x000000);

    private int rgb;
    private int index;
    private int index2;
    private String argb;

    PredefinedColors(int index, int index2, int rgb) {
        this.rgb = rgb;
        this.index = index;
        this.index2 = index2;
        this.argb = "FF" + (StringHelper.leftPad(Integer.toHexString(getRed()), 2, '0') +
                StringHelper.leftPad(Integer.toHexString(getGreen()), 2, '0') +
                StringHelper.leftPad(Integer.toHexString(getBlue()), 2, '0')).toUpperCase(Locale.ROOT);
    }

    public String toString() {
        return argb;
    }

    public int getRed() {
        return (rgb >> 16) & 0xFF;
    }

    public int getGreen() {
        return (rgb >> 8) & 0xFF;
    }

    public int getBlue() {
        return rgb & 0xFF;
    }

    public int getAlpha() {
        return (rgb >> 24) & 0xFF;
    }

    public int getRgb() {
        return rgb;
    }

    public String getArgb() {
        return argb;
    }

    public int getIndex() {
        return index;
    }

    public int getIndex2() {
        return index2;
    }

    public static Integer getColorIndex(String argb) {
        return INDEX_MAP.get(argb);
    }

    public static PredefinedColors getByIndex(int index) {
        return INDEX_COLORS.get(index);
    }

    static final Map<Integer, PredefinedColors> INDEX_COLORS = new HashMap<>();
    static final Map<String, Integer> INDEX_MAP = new HashMap<>();

    static {
        for (PredefinedColors value : values()) {
            Integer index1 = value.getIndex();
            if (!INDEX_COLORS.containsKey(index1)) {
                INDEX_COLORS.put(index1, value);
            }
            Integer index2 = value.getIndex2();
            if (index2 != -1 && !INDEX_COLORS.containsKey(index2)) {
                INDEX_COLORS.put(index2, value);
            }
            INDEX_MAP.put(value.getArgb(), value.getIndex());
        }
    }

    public static void main(String[] args) {
        String argb = PredefinedColors.ORCHID.getArgb();
        System.out.println(argb);

        PredefinedColors color = PredefinedColors.getByIndex(64);
        System.out.println(color.getArgb());
    }
}