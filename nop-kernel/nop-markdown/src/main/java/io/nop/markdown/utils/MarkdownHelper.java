package io.nop.markdown.utils;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MarkdownHelper {
    public static String removeStyle(String text) {
        if (text.startsWith("___") && text.endsWith("___")) {
            text = text.substring(3, text.length() - 3);
        }
        if (text.startsWith("**") && text.endsWith("**")) {
            text = text.substring(2, text.length() - 2);
        }

        if (text.startsWith("*") && text.endsWith("*")) {
            text = text.substring(1, text.length() - 1);
        }
        return text;
    }


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
        return findLinkPositions(text);
    }

    public static List<IntRangeBean> findLinkPositions(String text) {
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
        return getLinkUrl(text, rangeBean);
    }

    public static String getLinkUrl(String text, IntRangeBean rangeBean) {
        String subText = text.substring(rangeBean.getBegin(), rangeBean.getEnd());
        int beginPos = subText.indexOf("](");
        int endPos = subText.lastIndexOf(')');
        return subText.substring(beginPos + 2, endPos).trim();
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

            String url = getImageUrl(text, range);
            sb.append("\n> AI_SUMMARY_FOR: ").append(url).append("\n");
            sb.append(StringHelper.split(info.trim(), '\n').stream().map(line -> "> " + line).collect(Collectors.joining("\n")));
            sb.append("\n\n");
        }

        // 复制剩余部分
        if (currentIndex < text.length()) {
            sb.append(text.substring(currentIndex));
        }

        return sb.toString();
    }

    public static boolean containsCodeBlock(String text) {
        return text.contains("\n```");
    }

    public static boolean containsTable(String text) {
        return findTable(text) >= 0;
    }

    public static int findTable(String text) {
        return MarkdownTableHelper.findTable(text);
    }
}