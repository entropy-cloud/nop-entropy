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
package io.nop.excel.util;

// copy from POI

/**
 * width = Truncate([{Number of Characters} * {Maximum Digit Width} + {5 pixel padding}] / {Maximum Digit Width} * 256) / 256
 * <p>
 * Excel每个字体的缺省宽度（Default Font Width)。Excel中输入的列宽是DFW的整数被。padding=DFW/4 向上取整。
 * 对于宋体48号字体字符宽度32像素。Excel缺省列宽必须完整显示8个字符。
 * 273 = 8 * 32 + (32/4)*2 + 1
 * Excel规定每列的像素宽度必须是8的整数倍（为了在滚动时提高渲染性能）, 273向上取整为280像素。
 *
 * @author Yegor Kozlov
 */
public class UnitsHelper {
    /**
     * In Escher absolute distances are specified in English Metric Units (EMUs), occasionally referred to as A units;
     * there are 360000 EMUs per centimeter, 914400 EMUs per inch, 12700 EMUs per point.
     */
    public static final int EMU_PER_PIXEL = 9525;
    public static final int EMU_PER_POINT = 12700;
    public static final int EMU_PER_CENTIMETER = 360000;

    /**
     * Master DPI (576 pixels per inch). Used by the reference coordinate system in PowerPoint (HSLF)
     */
    public static final int MASTER_DPI = 576;

    /**
     * Pixels DPI (96 pixels per inch)
     */
    public static final int PIXEL_DPI = 96;

    /**
     * Points DPI (72 pixels per inch)
     */
    public static final int POINT_DPI = 72;

    public static final double POINT_TO_PIXEL = PIXEL_DPI * 1.0 / POINT_DPI;

    public static final double PIXEL_TO_POINT = 1 / POINT_TO_PIXEL;

    /**
     * Width of one "standard character" of the default font in pixels. Same for Calibri and Arial. "Standard character"
     * defined as the widest digit character in the given font. Copied from XSSFWorkbook, since that isn't available
     * here.
     * <p>
     * Note this is only valid for workbooks using the default Excel font.
     * <p>
     * Would be nice to eventually support arbitrary document default fonts.
     */
    public static final float DEFAULT_CHARACTER_WIDTH = 7.0017f; // 单位为px

    public static final double DEFAULT_CHARACTER_WIDTH_IN_PT = 6.5;

    /**
     * Column widths are in fractional characters, this is the EMU equivalent. One character is defined as the widest
     * value for the integers 0-9 in the default font.
     */
    public static final int EMU_PER_CHARACTER = (int) (EMU_PER_PIXEL * DEFAULT_CHARACTER_WIDTH);

    public static final int POINT_PER_CHARACTER = (int) (EMU_PER_POINT * DEFAULT_CHARACTER_WIDTH);

    /**
     * width of 1px in columns with default width in units of 1/256 of a character width
     */
    private static final double PX_DEFAULT = 32.00f;
    /**
     * width of 1px in columns with overridden width in units of 1/256 of a character width
     */
    // private static final double PX_MODIFIED = 36.56f;

    /**
     * 1pt = 1/72 inch = 1/28.35 cm
     * <p>
     * Converts points to EMUs
     *
     * @param points points
     * @return EMUs
     */
    public static int pointsToEMU(double points) {
        return (int) Math.rint(EMU_PER_POINT * points);
    }

    /**
     * Converts pixels to EMUs
     *
     * @param pixels pixels
     * @return EMUs
     */
    public static int pixelToEMU(int pixels) {
        return pixels * EMU_PER_PIXEL;
    }

    /**
     * Converts EMUs to points
     *
     * @param emu emu
     * @return points
     */
    public static double emuToPoints(int emu) {
        return (double) emu / EMU_PER_POINT;
    }

    /**
     * Converts a value of type FixedPoint to a floating point
     *
     * @param fixedPoint value in fixed point notation
     * @return floating point (double)
     * @see <a href="http://msdn.microsoft.com/en-us/library/dd910765(v=office.12).aspx">[MS-OSHARED] - 2.2.1.6
     * FixedPoint</a>
     */
    public static double fixedPointToDouble(int fixedPoint) {
        int i = (fixedPoint >> 16);
        int f = fixedPoint & 0xFFFF;
        return (i + f / 65536d);
    }

    /**
     * Converts a value of type floating point to a FixedPoint
     *
     * @param floatPoint value in floating point notation
     * @return fixedPoint value in fixed points notation
     * @see <a href="http://msdn.microsoft.com/en-us/library/dd910765(v=office.12).aspx">[MS-OSHARED] - 2.2.1.6
     * FixedPoint</a>
     */
    public static int doubleToFixedPoint(double floatPoint) {
        double fractionalPart = floatPoint % 1d;
        double integralPart = floatPoint - fractionalPart;
        int i = (int) Math.floor(integralPart);
        int f = (int) Math.rint(fractionalPart * 65536d);
        return (i << 16) | (f & 0xFFFF);
    }

    public static double masterToPoints(int masterDPI) {
        double points = masterDPI;
        points *= POINT_DPI;
        points /= MASTER_DPI;
        return points;
    }

    public static int pointsToMaster(double points) {
        points *= MASTER_DPI;
        points /= POINT_DPI;
        return (int) Math.rint(points);
    }

    public static int pointsToPixel(double points) {
        points *= POINT_TO_PIXEL;
        return (int) Math.rint(points);
    }

    public static double pixelToPoints(int pixel) {
        double points = pixel * PIXEL_TO_POINT;
        return points;
    }

    public static int pointsToTwip(double points) {
        return (int) points * 20;
    }

    public static double twipToPoints(int twips) {
        return twips / 20d;
    }

    public static int charactersToEMU(double characters) {
        return (int) (characters * EMU_PER_CHARACTER);
    }

    /**
     * 一个列宽单位等于“Normal”样式中一个字符的宽度。对于比例字体，则使用字符“0”（零）的宽度
     * <p>
     * 字符像素宽度 = 字体宽度 * 字符个数 + 边距。
     * <p>
     * POI创建的Excel, 缺省字体为Arial, 字体宽度为7, 边距为5, 而一般宋体11号字和12号字，字体宽度为8,边距为5
     *
     * @param columnWidth specified in 256ths of a standard character
     * @return equivalent EMUs
     */
    public static int columnWidthToEMU(int columnWidth) {
        return charactersToEMU(columnWidth / 256d);
    }

    public static double columnWidthToPoints(int columnWidth, int defaultColumnWidth) {
        double px = PX_DEFAULT; // (columnWidth == defaultColumnWidth ? PX_DEFAULT : PX_MODIFIED);
        return columnWidth / px / PIXEL_DPI * POINT_DPI;
    }

    public static double defaultColumnWidthToPoints(int defaultColumnWidth) {
        return defaultColumnWidth * 256 / PX_DEFAULT / PIXEL_DPI * POINT_DPI;
    }

    private static final short EXCEL_COLUMN_WIDTH_FACTOR = 256;
    private static final int UNIT_OFFSET_LENGTH = 7;

    /**
     * See <a href=
     * "http://apache-poi.1045710.n5.nabble.com/Excel-Column-Width-Unit-Converter-pixels-excel-column-width-units-td2301481.html"
     * >here</a> for Xio explanation and details
     */
    public static int getColumnWidthInPx(int widthUnits) {
        int pixels = (widthUnits / EXCEL_COLUMN_WIDTH_FACTOR) * UNIT_OFFSET_LENGTH;

        int offsetWidthUnits = widthUnits % EXCEL_COLUMN_WIDTH_FACTOR;
        pixels += Math.round(offsetWidthUnits / ((float) EXCEL_COLUMN_WIDTH_FACTOR / UNIT_OFFSET_LENGTH));

        return pixels;
    }

    /**
     * @param twips (1/20th of a point) typically used for row heights
     * @return equivalent EMUs
     */
    public static int twipsToEMU(int twips) {
        return (int) (twips / 20d * EMU_PER_POINT);
    }

    public static double inchesToPoints(double inches) {
        return inches * 72.0F;
    }

    public static double pointsToInches(double points) {
        return points / 72.0F;
    }

    public static double mmToPoints(double mm) {
        return mm * 2.835F;
    }

    public static double pointsToMm(double points) {
        return points / 2.835F;
    }

//    private static final FontRenderContext fontRenderContext = new FontRenderContext(null, true, true);
//
//    public static double getDefaultCharWidth() {
//        AttributedString str = new AttributedString("0");
//        //str.addAttribute(TextAttribute.FONT, Font.getFont("Microsoft YaHei UI"),0,1);
//        str.addAttribute(TextAttribute.SIZE, "11");
//        str.addAttribute(TextAttribute.FAMILY, "2");
//        TextLayout layout = new TextLayout(str.getIterator(), fontRenderContext);
//        return layout.getAdvance();
//    }

    public static void main(String[] args) {
//        System.out.println(getDefaultCharWidth());
//
//        System.out.println("x=" + (20.921875 * 6.5 * PIXEL_TO_POINT));

        int v = columnWidthToEMU(8);
        System.out.println(v);

        double d = defaultColumnWidthToPoints(8);
        System.out.println("d=" + d);

        d = columnWidthToPoints(8 * 256, 8 * 256);
        System.out.println(d);

        int p = pointsToPixel(54);

        System.out.println("p=" + p);

        d = pixelToPoints(p);
        System.out.println("d=" + d);

        d = getColumnWidthInPx(8 * 256);
        System.out.println("d3=" + d);
    }
}