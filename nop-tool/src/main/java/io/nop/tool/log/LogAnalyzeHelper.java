package io.nop.tool.log;

import io.nop.commons.util.DateHelper;

public class LogAnalyzeHelper {
    public static boolean isLogMessageStart(String line) {
        if (line.length() < 20)
            return false;

        char c = line.charAt(10);
        if (c != 'T')
            return false;

        try {
            DateHelper.parseDate(line.substring(0, 10));
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
