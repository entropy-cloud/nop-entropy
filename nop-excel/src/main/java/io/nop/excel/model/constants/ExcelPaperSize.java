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

//package org.apache.poi.ss.usermodel;

import io.nop.api.core.exceptions.NopException;

import static io.nop.excel.ExcelErrors.ARG_PAPER_SIZE;
import static io.nop.excel.ExcelErrors.ERR_EXCEL_INVALID_PAPER_SIZE;
import static io.nop.excel.util.UnitsHelper.inchesToPoints;
import static io.nop.excel.util.UnitsHelper.mmToPoints;

/**
 * 与Excel定义保持一致。宽度值为in*72, mm*2.835。
 * <p>
 * When paperHeight, paperWidth, and paperUnits are specified, paperSize should be ignored.
 *
 * <p>
 * The enumeration value indicating the possible paper size for a sheet
 *
 * @author Daniele Montagni
 **/
public enum ExcelPaperSize {

    /**
     * Whatever the printer's default paper size is。缺省采用A4纸大小
     */
    DEFAULT(595.0F, 842.0F),

    /**
     * 1. US Letter 8 1/2 x 11 in
     */
    LETTER_PAPER(612.0F, 792.0F), //

    /**
     * 2. US Letter Small 8 1/2 x 11 in
     */
    LETTER_SMALL_PAPER(540.0F, 720.0F), // ?

    /**
     * 3. US Tabloid 11 x 17 in
     */
    TABLOID_PAPER(792.0F, 1224.0F), //

    /**
     * 4. US Ledger 17 x 11 in
     */
    LEDGER_PAPER(1224F, 792F), //

    /**
     * 5. US Legal 8 1/2 x 14 in
     */
    LEGAL_PAPER(612F, 1008F), //

    /**
     * 6. US Statement 5 1/2 x 8 1/2 in
     */
    STATEMENT_PAPER(396F, 612F), //

    /**
     * 7. US Executive 7 1/4 x 10 1/2 in
     */
    EXECUTIVE_PAPER(522.0F, 756.0F), //

    /**
     * 8. A3 - 297x420 mm
     */
    A3_PAPER(842.0F, 1191.0F), //

    /**
     * 9. A4 - 210x297 mm
     */
    A4_PAPER(595.0F, 842.0F), //

    /**
     * 10. A4 Small - 210x297 mm
     */
    A4_SMALL_PAPER(595.0F, 842.0F), //

    /**
     * 11. A5 - 148x210 mm
     */
    A5_PAPER(420.0F, 595.0F), //

    /**
     * 12. B4 (JIS) 250x354 mm
     */
    B4_PAPER(708.0F, 1000.0F), //

    /**
     * 13. B5 (JIS) 182x257 mm
     */
    B5_PAPER(498.0F, 708.0F), //

    /**
     * 14. Folio 8 1/2 x 13 in
     */
    FOLIO_PAPER(612F, 936F), //

    /**
     * 15. Quarto 215x275 mm
     */
    QUARTO_PAPER(535.0F, 697.0F), //

    /**
     * 16. 10 x 14 in
     */
    STANDARD_PAPER_10_14(720F, 1008F), //

    /**
     * 17. 11 x 17 in
     */
    STANDARD_PAPER_11_17(792.0F, 1224.0F),

    //==============以下为OOXML标准中有定义，但是POI中没有定义的内容==========================
    /**
     * 18. 8.5 x 11 in
     */
    NOTE_PAPER(inchesToPoints(8.5F), inchesToPoints(11.0F)), // 612.0F, 792.0F

    /**
     * 19. 3.875 x 8.875 in
     */
    ENVELOPE_9(inchesToPoints(3.875F), inchesToPoints(8.875F)), // 279.4F, 642.65F

    /**
     * 20. 4.125 x 9.5 in
     */
    ENVELOPE_10(inchesToPoints(4.125F), inchesToPoints(9.5F)), // 297.225F, 686.0F

    /**
     * 21. 4.5 x 10.375 in
     */
    ENVELOPE_11(inchesToPoints(4.5F), inchesToPoints(10.375F)), // 324.0F, 747.7F

    /**
     * 22. 4.75 x 11 in
     */
    ENVELOPE_12(inchesToPoints(4.75F), inchesToPoints(11.0F)), // 341.5F, 792.0F

    /**
     * 23. 5 x 11.5 in
     */
    ENVELOPE_14(inchesToPoints(5.0F), inchesToPoints(11.5F)), // 360.0F, 816.0F

    /**
     * 24. 17 x 22 in
     */
    PAPER_C(inchesToPoints(17.0F), inchesToPoints(22.0F)), // 1224.0F, 1584.0F

    /**
     * 25. 22 x 34 in
     */
    PAPER_D(inchesToPoints(22.0F), inchesToPoints(34.0F)), // 1584.0F, 2448.0F

    /**
     * 26. 34 x 44 in
     */
    PAPER_E(inchesToPoints(34.0F), inchesToPoints(44.0F)), // 2448.0F, 3168.0F

    /**
     * 27. 110 x 220 mm
     */
    ENVELOPE_DL(mmToPoints(110F), mmToPoints(220F)), // 311.85F, 623.7F

    /**
     * 28. 162 x 229 mm
     */
    ENVELOPE_C5(mmToPoints(162F), mmToPoints(229F)), // 459.27F, 649.515F

    /**
     * 29. 324 x 458 mm
     */
    ENVELOPE_C3(mmToPoints(324F), mmToPoints(458F)), // 918.94F, 1302.83F

    /**
     * 30. 229 x 324 mm
     */
    ENVELOPE_C4(mmToPoints(229F), mmToPoints(324F)), // 649.515F, 918.94F

    /**
     * 31. 114 x 162 mm
     */
    ENVELOPE_C6(mmToPoints(114F), mmToPoints(162F)), // 323.99F, 459.27F

    /**
     * 32. 114 x 229 mm
     */
    ENVELOPE_C65(mmToPoints(114F), mmToPoints(229F)), // 323.99F, 649.515F

    /**
     * 33. 250 x 353 mm
     */
    ENVELOPE_B4(mmToPoints(250F), mmToPoints(353F)), // 710.25F, 999.555F

    /**
     * 34. 176 x 250 mm
     */
    ENVELOPE_B5(mmToPoints(176F), mmToPoints(250F)), // 499.6F, 710.25F

    /**
     * 35. 176 x 125 mm
     */
    ENVELOPE_B6(mmToPoints(176F), mmToPoints(125F)), // 499.6F, 354.225F

    /**
     * 36. 110 x 230 mm
     */
    ENVELOPE_ITALY(mmToPoints(110F), mmToPoints(230F)), // 311.85F, 649.515F

    /**
     * 37. 3.875 x 7.5 in
     */
    ENVELOPE_MONARCH(inchesToPoints(3.875F), inchesToPoints(7.5F)), // 279.4F, 540.0F

    /**
     * 38. 3.625 x 6.5 in
     */
    ENVELOPE_6_3_4(inchesToPoints(3.625F), inchesToPoints(6.5F)), // 259.05F, 468.0F

    /**
     * 39. 14.875 x 11 in
     */
    FANFOLD_US_STANDARD(inchesToPoints(14.875F), inchesToPoints(11.0F)), // 1078.5F, 792.0F

    /**
     * 40. 8.5 x 12 in
     */
    FANFOLD_GERMAN_STANDARD(inchesToPoints(8.5F), inchesToPoints(12.0F)), // 612.0F, 864.0F

    /**
     * 41. 8.5 x 13 in
     */
    FANFOLD_GERMAN_LEGAL(inchesToPoints(8.5F), inchesToPoints(13.0F)), // 612.0F, 936.0F

    /**
     * 42. 250 x 353 mm
     */
    PAPER_B4(mmToPoints(250F), mmToPoints(353F)), // 710.25F, 999.555F

    /**
     * 43. 200 x 148 mm
     */
    POSTCARD_JAPANESE_DOUBLE(mmToPoints(200F), mmToPoints(148F)), // 566.0F, 421.83F

    /**
     * 44. 9 x 11 in
     */
    STANDARD_PAPER_9_11(inchesToPoints(9.0F), inchesToPoints(11.0F)), // 648.0F, 792.0F

    /**
     * 45. 10 x 11 in
     */
    STANDARD_PAPER_10_11(inchesToPoints(10.0F), inchesToPoints(11.0F)), // 720.0F, 792.0F

    /**
     * 46. 15 x 11 in
     */
    STANDARD_PAPER_15_11(inchesToPoints(15.0F), inchesToPoints(11.0F)), // 1080.0F, 792.0F

    /**
     * 47. 220 x 220 mm
     */
    ENVELOPE_INVITE(mmToPoints(220F), mmToPoints(220F)), // 620.7F, 620.7F

    /**
     * 48. 9.275 x 12 in
     */
    LETTER_EXTRA_PAPER(inchesToPoints(9.275F), inchesToPoints(12.0F)), // 670.7F, 864.0F

    /**
     * 49. 9.275 x 15 in
     */
    LEGAL_EXTRA_PAPER(inchesToPoints(9.275F), inchesToPoints(15.0F)), // 670.7F, 1080.0F

    /**
     * 50. 11.69 x 18 in
     */
    TABLOID_EXTRA_PAPER(inchesToPoints(11.69F), inchesToPoints(18.0F)), // 841.68F, 1296.0F

    /**
     * 51. 236 x 322 mm
     */
    A4_EXTRA_PAPER(mmToPoints(236F), mmToPoints(322F)), // 668.86F, 908.77F

    /**
     * 52. 8.275 x 11 in
     */
    LETTER_TRANSVERSE_PAPER(inchesToPoints(8.275F), inchesToPoints(11.0F)), // 596.65F, 792.0F

    /**
     * 53. 210 x 297 mm
     */
    A4_TRANSVERSE_PAPER(mmToPoints(210F), mmToPoints(297F)), // 595.35F, 841.68F

    /**
     * 54. 9.275 x 12 in
     */
    LETTER_EXTRA_TRANSVERSE_PAPER(inchesToPoints(9.275F), inchesToPoints(12.0F)), // 670.7F, 864.0F

    /**
     * 55. 227 x 356 mm
     */
    SUPER_A_SUPER_A_A4_PAPER(mmToPoints(227F), mmToPoints(356F)), // 641.045F, 1004.86F

    /**
     * 56. 305 x 487 mm
     */
    SUPER_B_SUPER_B_A3_PAPER(mmToPoints(305F), mmToPoints(487F)), // 861.175F, 1377.045F

    /**
     * 57. 8.5 x 12.69 in
     */
    LETTER_PLUS_PAPER(inchesToPoints(8.5F), inchesToPoints(12.69F)), // 612.0F, 907.88F

    /**
     * 58. 210 x 330 mm
     */
    A4_PLUS_PAPER(mmToPoints(210F), mmToPoints(330F)), // 595.35F, 929.55F

    /**
     * 59. 148 x 210 mm
     */
    A5_TRANSVERSE_PAPER(mmToPoints(148F), mmToPoints(210F)), // 417.82F, 595.35F

    /**
     * 60. 182 x 257 mm
     */
    JIS_B5_TRANSVERSE_PAPER(mmToPoints(182F), mmToPoints(257F)), // 511.07F, 725.545F

    /**
     * 61. 322 x 445 mm
     */
    A3_EXTRA_PAPER(mmToPoints(322F), mmToPoints(445F)), // 908.77F, 1256.075F

    /**
     * 62. 174 x 235 mm
     */
    A5_EXTRA_PAPER(mmToPoints(174F), mmToPoints(235F)), // 490.19F, 664.225F

    /**
     * 63. 201 x 276 mm
     */
    B5_EXTRA_PAPER(mmToPoints(201F), mmToPoints(276F)), // 566.035F, 780.26F

    /**
     * 64. 420 x 594 mm
     */
    A2_PAPER(mmToPoints(420F), mmToPoints(594F)), // 1188.7F, 1675.99F

    /**
     * 65. 297 x 420 mm
     */
    A3_TRANSVERSE_PAPER(mmToPoints(297F), mmToPoints(420F)), // 841.68F, 1188.7F

    /**
     * 66. 322 x 445 mm
     */
    A3_EXTRA_TRANSVERSE_PAPER(mmToPoints(322F), mmToPoints(445F)); // 908.77F, 1256.075F

    private final double width;
    private final double height;

    ExcelPaperSize(double width, double height) {
        this.width = width;
        this.height = height;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }


    public static ExcelPaperSize of(int paperSize) {
        if (paperSize < 0 || paperSize >= values().length)
            throw new NopException(ERR_EXCEL_INVALID_PAPER_SIZE)
                    .param(ARG_PAPER_SIZE, paperSize);
        return values()[paperSize];
    }
}