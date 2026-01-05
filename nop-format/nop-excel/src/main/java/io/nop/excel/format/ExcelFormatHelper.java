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
package io.nop.excel.format;

import io.nop.commons.text.formatter.FastIntegerFormatter;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import org.apache.poi.ss.usermodel.BuiltinFormats;
//import org.apache.poi.ss.usermodel.DateUtil;

/**
 * 从POI项目拷贝的代码
 */
public class ExcelFormatHelper {

    /**
     * Pattern to find a number format: "0" or "#"
     */
    private static final Pattern NUM_PATTERN = Pattern.compile("[0#]+");

    /**
     * Pattern to find days of week as text "ddd...."
     */
    private static final Pattern DAYS_AS_TEXT = Pattern.compile("([d]{3,})", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern to find "AM/PM" marker
     */
    private static final Pattern AM_PM_PATTERN = Pattern.compile("((A|P)[M/P]*)", Pattern.CASE_INSENSITIVE);

    /**
     * A regex to find patterns like [$$-1009] and [$?-452].
     */
    private static final Pattern SPECIAL_PATTERN_GROUP = Pattern.compile("(\\[\\$[^-\\]]*-[0-9A-Z]+\\])");

    /** <em>General</em> format for whole numbers. */
    // private static final Format generalWholeNumFormat = new
    // DecimalFormat("#");

    /**
     * <em>General</em> format for decimal numbers.
     */
    // private static final Format generalDecimalNumFormat = new
    // DecimalFormat("#.##########");

    /**
     * 这里只缓存少量规定格式的format，不需要每个format都缓存。另外也不需要线程安全。假定除了系统初始化阶段不会动态设置format
     */
    private static final Map<String, Format> FORMATS = new HashMap<>();

    static {
        // init built-in formats
        Format zipFormat = ZipPlusFourFormat.INSTANCE;
        addFormat("00000\\-0000", zipFormat);
        addFormat("00000-0000", zipFormat);

        Format phoneFormat = PhoneFormat.INSTANCE;
        // allow for format string variations
        addFormat("[<=9999999]###\\-####;\\(###\\)\\ ###\\-####", phoneFormat);
        addFormat("[<=9999999]###-####;(###) ###-####", phoneFormat);
        addFormat("###\\-####;\\(###\\)\\ ###\\-####", phoneFormat);
        addFormat("###-####;(###) ###-####", phoneFormat);

        Format ssnFormat = SSNFormat.INSTANCE;
        addFormat("000\\-00\\-0000", ssnFormat);
        addFormat("000-00-0000", ssnFormat);
    }

    public static Format getFormat(String formatStr) {
        if (formatStr == null || "General".equals(formatStr) || "@".equals(formatStr))
            return null;
        Format format = FORMATS.get(formatStr);
        if (format != null) {
            return format;
        }
        try {
            format = createFormat(formatStr);
            return format;
        } catch (Exception e) {
            return null;
        }
    }

    private static Format createFormat(String sFormat) {
        int formatIndex = BuiltinFormats.getBuiltinFormat(sFormat);

        // remove color formatting if present
        String formatStr = sFormat.replaceAll("\\[[a-zA-Z]*\\]", "");

        // try to extract special characters like currency
        Matcher m = SPECIAL_PATTERN_GROUP.matcher(formatStr);
        while (m.find()) {
            String match = m.group();
            String symbol = match.substring(match.indexOf('$') + 1, match.indexOf('-'));
            if (symbol.indexOf('$') > -1) {
                StringBuffer sb = new StringBuffer();
                sb.append(symbol.substring(0, symbol.indexOf('$')));
                sb.append('\\');
                sb.append(symbol.substring(symbol.indexOf('$'), symbol.length()));
                symbol = sb.toString();
            }
            formatStr = m.replaceAll(symbol);
            m = SPECIAL_PATTERN_GROUP.matcher(formatStr);
        }

        if (formatStr == null || formatStr.trim().length() == 0) {
            return null;
        }

        if (ExcelDateHelper.isADateFormat(formatIndex, formatStr)) {
            return createDateFormat(formatStr);
        }
        if (NUM_PATTERN.matcher(formatStr).find()) {
            return createNumberFormat(formatStr);
        }
        return null;
    }

    private static Format createDateFormat(String pFormatStr) {
        String formatStr = pFormatStr;
        formatStr = formatStr.replaceAll("\\\\-", "-");
        formatStr = formatStr.replaceAll("\\\\,", ",");
        formatStr = formatStr.replaceAll("\\\\ ", " ");
        formatStr = formatStr.replaceAll(";@", "");
        boolean hasAmPm = false;
        Matcher amPmMatcher = AM_PM_PATTERN.matcher(formatStr);
        while (amPmMatcher.find()) {
            formatStr = amPmMatcher.replaceAll("@");
            hasAmPm = true;
            amPmMatcher = AM_PM_PATTERN.matcher(formatStr);
        }
        formatStr = formatStr.replaceAll("@", "a");

        Matcher dateMatcher = DAYS_AS_TEXT.matcher(formatStr);
        if (dateMatcher.find()) {
            String match = dateMatcher.group(0);
            formatStr = dateMatcher.replaceAll(match.toUpperCase().replaceAll("D", "E"));
        }

        // Convert excel date format to SimpleDateFormat.
        // Excel uses lower case 'm' for both minutes and months.
        // From Excel help:
        /*
         * The "m" or "mm" code must appear immediately after the "h" or"hh"
         * code or immediately before the "ss" code; otherwise, Microsoft Excel
         * displays the month instead of minutes."
         */

        StringBuffer sb = new StringBuffer();
        char[] chars = formatStr.toCharArray();
        boolean mIsMonth = true;
        List<Integer> ms = new ArrayList<Integer>();
        for (int j = 0; j < chars.length; j++) {
            char c = chars[j];
            if (c == 'h' || c == 'H') {
                mIsMonth = false;
                if (hasAmPm) {
                    sb.append('h');
                } else {
                    sb.append('H');
                }
            } else if (c == 'm') {
                if (mIsMonth) {
                    sb.append('M');
                    ms.add(Integer.valueOf(sb.length() - 1));
                } else {
                    sb.append('m');
                }
            } else if (c == 's' || c == 'S') {
                sb.append('s');
                // if 'M' precedes 's' it should be minutes ('m')
                for (int i = 0; i < ms.size(); i++) {
                    int index = ((Integer) ms.get(i)).intValue();
                    if (sb.charAt(index) == 'M') {
                        sb.replace(index, index + 1, "m");
                    }
                }
                mIsMonth = true;
                ms.clear();
            } else if (Character.isLetter(c)) {
                mIsMonth = true;
                ms.clear();
                if (c == 'y' || c == 'Y') {
                    sb.append('y');
                } else if (c == 'd' || c == 'D') {
                    sb.append('d');
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        formatStr = sb.toString();

        try {
            return new SimpleDateFormat(formatStr);
        } catch (IllegalArgumentException iae) {

            // the pattern could not be parsed correctly,
            // so fall back to the default number format
            return null;
        }

    }

    private static Format createNumberFormat(String formatStr) {
        StringBuffer sb = new StringBuffer(formatStr);
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            // handle (#,##0_);
            if (c == '(') {
                int idx = sb.indexOf(")", i);
                if (idx > -1 && sb.charAt(idx - 1) == '_') {
                    sb.deleteCharAt(idx);
                    sb.deleteCharAt(idx - 1);
                    sb.deleteCharAt(i);
                    i--;
                }
            } else if (c == ')' && i > 0 && sb.charAt(i - 1) == '_') {
                sb.deleteCharAt(i);
                sb.deleteCharAt(i - 1);
                i--;
                // remove quotes and back slashes
            } else if (c == '\\' || c == '"') {
                sb.deleteCharAt(i);
                i--;

                // for scientific/engineering notation
            } else if (c == '+' && i > 0 && sb.charAt(i - 1) == 'E') {
                sb.deleteCharAt(i);
                i--;
            }
        }

        try {
            return new DecimalFormat(sb.toString());
        } catch (IllegalArgumentException iae) {

            // the pattern could not be parsed correctly,
            // so fall back to the default number format
            return null;
        }
    }

    public static void addFormat(String excelFormatStr, Format format) {
        FORMATS.put(excelFormatStr, format);
    }

    private static final class SSNFormat extends Format {
        private static final long serialVersionUID = 5842426239071043434L;
        public static final Format INSTANCE = new SSNFormat();
        private static final Format DF = FastIntegerFormatter.fromPattern("000000000");

        private SSNFormat() {
            // enforce singleton
        }

        /**
         * Format a number as an SSN
         */
        public static String format(Number num) {
            String result = DF.format(num);
            StringBuffer sb = new StringBuffer();
            sb.append(result.substring(0, 3)).append('-');
            sb.append(result.substring(3, 5)).append('-');
            sb.append(result.substring(5, 9));
            return sb.toString();
        }

        public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
            return toAppendTo.append(format((Number) obj));
        }

        public Object parseObject(String source, ParsePosition pos) {
            return DF.parseObject(source, pos);
        }
    }

    /**
     * Format class for Excel Zip + 4 format. This class mimics Excel's built-in
     * formatting for Zip + 4.
     *
     * @author James May
     */
    private static final class ZipPlusFourFormat extends Format {
        private static final long serialVersionUID = 1422822964748552552L;
        public static final Format INSTANCE = new ZipPlusFourFormat();
        private static final Format DF = FastIntegerFormatter.fromPattern("000000000");

        private ZipPlusFourFormat() {
            // enforce singleton
        }

        /**
         * Format a number as Zip + 4
         */
        public static String format(Number num) {
            String result = DF.format(num);
            StringBuffer sb = new StringBuffer();
            sb.append(result.substring(0, 5)).append('-');
            sb.append(result.substring(5, 9));
            return sb.toString();
        }

        public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
            return toAppendTo.append(format((Number) obj));
        }

        public Object parseObject(String source, ParsePosition pos) {
            return DF.parseObject(source, pos);
        }
    }

    /**
     * Format class for Excel phone number format. This class mimics Excel's
     * built-in phone number formatting.
     *
     * @author James May
     */
    private static final class PhoneFormat extends Format {
        private static final long serialVersionUID = -820798622022983780L;
        public static final Format INSTANCE = new PhoneFormat();
        private static final Format DF = FastIntegerFormatter.fromPattern("##########");

        private PhoneFormat() {
            // enforce singleton
        }

        /**
         * Format a number as a phone number
         */
        public static String format(Number num) {
            String result = DF.format(num);
            StringBuffer sb = new StringBuffer();
            String seg1, seg2, seg3;
            int len = result.length();
            if (len <= 4) {
                return result;
            }

            seg3 = result.substring(len - 4, len);
            seg2 = result.substring(Math.max(0, len - 7), len - 4);
            seg1 = result.substring(Math.max(0, len - 10), Math.max(0, len - 7));

            if (seg1 != null && seg1.trim().length() > 0) {
                sb.append('(').append(seg1).append(") ");
            }
            if (seg2 != null && seg2.trim().length() > 0) {
                sb.append(seg2).append('-');
            }
            sb.append(seg3);
            return sb.toString();
        }

        public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
            if (!(obj instanceof Number)) {
                throw new IllegalArgumentException("Object to format must be a Number");
            }
            return toAppendTo.append(format((Number) obj));
        }

        public Object parseObject(String source, ParsePosition pos) {
            return DF.parseObject(source, pos);
        }
    }
}