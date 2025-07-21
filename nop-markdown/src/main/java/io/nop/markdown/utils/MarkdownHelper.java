package io.nop.markdown.utils;

import io.nop.api.core.beans.IntRangeBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MarkdownHelper {


    // 查找 ]，遇到换行直接返回-1
    private static int findCharNoNewline(String text, int from, int to, char ch) {
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') return -1;
            if (c == ch) return i;
        }
        return -1;
    }

    public static List<IntRangeBean> findImagePositions(String text) {
        List<IntRangeBean> result = new ArrayList<>();
        int len = text.length();
        int i = 0;
        while (i < len - 1) {
            if (text.charAt(i) == '!' && text.charAt(i + 1) == '[') {
                int altEnd = findCharNoNewline(text, i + 2, len, ']');
                if (altEnd != -1 && altEnd + 1 < len && text.charAt(altEnd + 1) == '(') {
                    int urlEnd = findCharNoNewline(text, altEnd + 2, len, ')');
                    if (urlEnd != -1) {
                        result.add(IntRangeBean.build(i, urlEnd + 1));
                        i = urlEnd + 1;
                        continue;
                    }
                }
            }
            i++;
        }
        return result;
    }

    public static String getImageUrl(String text, IntRangeBean rangeBean) {
        String subText = text.substring(rangeBean.getBegin(), rangeBean.getEnd());
        int beginPos = subText.indexOf('(');
        int endPos = subText.lastIndexOf(')');
        return subText.substring(beginPos + 1, endPos).trim();
    }

    /**
     * imagePosList确定text内的一个区间范围，其中对应于图片链接。IntRangeBean表示的范围是[begin,end)，
     * 在end处先插入一个\n，然后插入对应的总结信息，然后再插入一个\m
     */
    public static String addImageSummarization(String text, List<IntRangeBean> imagePosList,
                                               List<String> infos) {
        if (text == null || imagePosList == null || infos == null) {
            return text;
        }

        // 如果范围个数与infos不匹配，抛出异常或处理
        if (imagePosList.size() != infos.size()) {
            throw new IllegalArgumentException("范围数和总结信息数必须相等");
        }

        // 先排序范围，确保按照开始位置
        Collections.sort(imagePosList, Comparator.comparingInt(IntRangeBean::getBegin));

        StringBuilder sb = new StringBuilder();

        int currentIndex = 0; // 当前处理文本的位置

        for (int i = 0; i < imagePosList.size(); i++) {
            IntRangeBean range = imagePosList.get(i);
            String info = infos.get(i);

            // 让范围合法
            int begin = Math.max(0, range.getBegin());
            int end = Math.min(text.length(), range.getEnd());

            // 复制中间未处理部分
            if (currentIndex < begin) {
                sb.append(text, currentIndex, begin);
                currentIndex = begin;
            }

            // 复制图片链接区域
            if (end > currentIndex) {
                sb.append(text, currentIndex, end);
                currentIndex = end;
            }

            // 在end位置插入总结信息
            sb.append("\n").append(info).append("\n");
        }

        // 复制剩余部分
        if (currentIndex < text.length()) {
            sb.append(text.substring(currentIndex));
        }

        return sb.toString();
    }
}
