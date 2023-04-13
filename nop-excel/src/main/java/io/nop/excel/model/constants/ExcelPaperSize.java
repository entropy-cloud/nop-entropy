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

/**
 * 与Excel定义保持一致。宽度值为in*72, mm*2.835
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
     * US Letter 8 1/2 x 11 in
     */
    LETTER_PAPER(612.0F, 792.0F), //

    /**
     * US Letter Small 8 1/2 x 11 in
     */
    LETTER_SMALL_PAPER(540.0F, 720.0F), // ?

    /**
     * US Tabloid 11 x 17 in
     */
    TABLOID_PAPER(792.0F, 1224.0F), //

    /**
     * US Ledger 17 x 11 in
     */
    LEDGER_PAPER(1224F, 792F), //

    /**
     * US Legal 8 1/2 x 14 in
     */
    LEGAL_PAPER(612F, 1008F), //

    /**
     * US Statement 5 1/2 x 8 1/2 in
     */
    STATEMENT_PAPER(396F, 612F), //

    /**
     * US Executive 7 1/4 x 10 1/2 in
     */
    EXECUTIVE_PAPER(522.0F, 756.0F), //

    /**
     * A3 - 297x420 mm
     */
    A3_PAPER(842.0F, 1191.0F), //

    /**
     * A4 - 210x297 mm
     */
    A4_PAPER(595.0F, 842.0F), //

    /**
     * A4 Small - 210x297 mm
     */
    A4_SMALL_PAPER(595.0F, 842.0F), //

    /**
     * A5 - 148x210 mm
     */
    A5_PAPER(420.0F, 595.0F), //

    /**
     * B4 (JIS) 250x354 mm
     */
    B4_PAPER(708.0F, 1000.0F), //

    /**
     * B5 (JIS) 182x257 mm
     */
    B5_PAPER(498.0F, 708.0F), //

    /**
     * Folio 8 1/2 x 13 in
     */
    FOLIO_PAPER(612F, 936F), //

    /**
     * Quarto 215x275 mm
     */
    QUARTO_PAPER(535.0F, 697.0F), //

    /**
     * 10 x 14 in
     */
    STANDARD_PAPER_10_14(720F, 1008F), //

    /**
     * 11 x 17 in
     */
    STANDARD_PAPER_11_17(792.0F, 1224.0F);

    private final float width;
    private final float height;

    ExcelPaperSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public static ExcelPaperSize of(int paperSize) {
        if (paperSize < 0 || paperSize >= values().length)
            throw new NopException(ERR_EXCEL_INVALID_PAPER_SIZE)
                    .param(ARG_PAPER_SIZE, paperSize);
        return values()[paperSize];
    }
}