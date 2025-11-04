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

// copy code from POI CellReference

package io.nop.core.model.table.utils;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.CellPosition;

import java.util.Locale;

import static io.nop.core.CoreErrors.ARG_CELL_POS;

public class CellReferenceHelper {
    static final char ABSOLUTE_REFERENCE_MARKER = '$';

    /**
     * Takes in a 0-based base-10 column and returns a ALPHA-26 representation. eg {@code convertNumToColString(3)}
     * returns {@code "D"}
     */
    public static String convertNumToColString(int col) {
        // Excel counts column A as the 1st column, we
        // treat it as the 0th one
        int excelColNum = col + 1;

        StringBuilder colRef = new StringBuilder(2);
        int colRemain = excelColNum;

        while (colRemain > 0) {
            int thisPart = colRemain % 26;
            if (thisPart == 0) {
                thisPart = 26;
            }
            colRemain = (colRemain - thisPart) / 26;

            // The letter A is at 65
            char colChar = (char) (thisPart + 64);
            colRef.insert(0, colChar);
        }

        return colRef.toString();
    }

    public static boolean isPartAbsolute(String part) {
        return part.charAt(0) == ABSOLUTE_REFERENCE_MARKER;
    }

    /**
     * takes in a column reference portion of a CellRef and converts it from ALPHA-26 number format to 0-based base 10.
     * 'A' -&gt; 0 'Z' -&gt; 25 'AA' -&gt; 26 'IV' -&gt; 255
     *
     * @return zero based column index
     */
    public static int convertColStringToIndex(String ref) {
        if (StringHelper.isEmpty(ref))
            return -1;

        int retval = 0;
        char[] refs = ref.toUpperCase(Locale.ROOT).toCharArray();
        for (int k = 0; k < refs.length; k++) {
            char thechar = refs[k];
            if (thechar == ABSOLUTE_REFERENCE_MARKER) {
                if (k != 0) {
                    throw new IllegalArgumentException("Bad col ref format '" + ref + "'");
                }
                continue;
            }

            // Character is uppercase letter, find relative value to A
            retval = (retval * 26) + (thechar - 'A' + 1);
        }
        return retval - 1;
    }

    public static CellPosition parsePositionABString(String abStr) {
        if (StringHelper.isEmpty(abStr))
            return null;

        if (CellPosition.NONE_NAME.equals(abStr) || CellPosition.NONE_STRING.equals(abStr))
            return CellPosition.NONE;

        String str = abStr;

        int pos = abStr.indexOf(ABSOLUTE_REFERENCE_MARKER);
        if (pos > 0)
            abStr = abStr.substring(pos + 1);

        int p = 0;
        for (int i = 0, n = abStr.length(); i < n; i++, p++) {
            char c = abStr.charAt(i);
            if (c <= '9' && c >= '0')
                break;
        }
        String colStr = abStr.substring(0, p);
        String rowStr = abStr.substring(p);

        int colIndex = convertColStringToIndex(colStr);
        int rowIndex = rowStr.isEmpty() ? -1
                : ConvertHelper.toInt(rowStr, err -> new NopException(err).param(ARG_CELL_POS, str)) - 1;
        return CellPosition.of(rowIndex, colIndex);
    }

    /**
     * 要求必须是 大写字母 + 数字这种组合形式，不允许使用小写字母
     *
     * @param abStr Excel单元格的位置表达式，例如A3
     */
    public static boolean isABString(String abStr) {
        if (StringHelper.isEmpty(abStr))
            return false;

        int p = 0;
        for (int i = 0, n = abStr.length(); i < n; i++, p++) {
            char c = abStr.charAt(i);
            if (c <= '9' && c >= '0')
                break;
        }

        String colStr = abStr.substring(0, p);
        String rowStr = abStr.substring(p);

        if (!isColStr(colStr))
            return false;

        if (rowStr.length() <= 0 || rowStr.length() > 8)
            return false;

        return StringHelper.isAllDigit(rowStr);
    }

    private static boolean isColStr(String str) {
        if (str.length() <= 0 || str.length() > 5)
            return false;

        for (int i = 0, n = str.length(); i < n; i++) {
            char c = str.charAt(0);
            if (c > 'Z' || c < 'A')
                return false;
        }
        return true;
    }

    public static CellPosition parsePositionRCString(String str) {
        return null;
    }
}