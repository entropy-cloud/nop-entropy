package io.nop.commons.util;

public class VersionComparer {
    public static int compareVersions(String v1, String v2) {
        int i = 0, j = 0;
        int len1 = v1.length(), len2 = v2.length();

        while (i < len1 || j < len2) {
            i = skipLeadingZeros(v1, i, len1);
            j = skipLeadingZeros(v2, j, len2);

            int cmp = compareNumberSegments(v1, v2, i, j, len1, len2);
            if (cmp != 0) return cmp;

            i = skipSegment(v1, i, len1);
            j = skipSegment(v2, j, len2);
        }
        return 0;
    }

    private static int skipLeadingZeros(String s, int pos, int len) {
        while (pos < len && s.charAt(pos) == '0') pos++;
        return pos;
    }

    private static int compareNumberSegments(String v1, String v2,
                                             int i, int j,
                                             int len1, int len2) {
        int start1 = i, start2 = j;
        while (i < len1 && v1.charAt(i) != '.') i++;
        while (j < len2 && v2.charAt(j) != '.') j++;

        // Compare segment lengths
        int lenSeg1 = i - start1;
        int lenSeg2 = j - start2;
        if (lenSeg1 != lenSeg2) return lenSeg1 - lenSeg2;

        // Compare digit by digit
        while (start1 < i && start2 < j) {
            char c1 = v1.charAt(start1);
            char c2 = v2.charAt(start2);
            if (c1 != c2) return c1 - c2;
            start1++;
            start2++;
        }
        return 0;
    }

    private static int skipSegment(String s, int pos, int len) {
        while (pos < len && s.charAt(pos) != '.') pos++;
        return pos < len ? pos + 1 : pos;
    }
}
