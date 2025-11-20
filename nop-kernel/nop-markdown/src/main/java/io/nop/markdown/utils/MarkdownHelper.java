package io.nop.markdown.utils;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.ITableView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    public static String escapeCell(String content) {
        if (content == null || content.isEmpty()) return " ";
        return content
                .replace("|", "\\|")  // 转义管道符
                .replace("\n", " ")   // 替换换行为空格
                .replace("\r", "");    // 移除回车符
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

    /**
     * 查找链接位置
     *
     * @param text         文本内容
     * @param includeImage 是否包含图片链接
     * @return 链接位置列表
     */
    public static List<IntRangeBean> findLinkPositions(String text, boolean includeImage) {
        List<IntRangeBean> result = new ArrayList<>();
        int len = text.length();
        int i = 0;

        while (i < len) {
            if (text.charAt(i) == '[') {
                // 检查是否是图片链接（前面有!）
                boolean isImage = (i > 0 && text.charAt(i - 1) == '!');

                // 如果是图片链接但不包含图片，跳过
                if (isImage && !includeImage) {
                    i++;
                    continue;
                }

                int altEnd = findCharNoNewline(text, i + 1, len, ']');
                if (altEnd != -1 && altEnd + 1 < len && text.charAt(altEnd + 1) == '(') {
                    int urlEnd = findCharNoNewline(text, altEnd + 2, len, ')');
                    if (urlEnd != -1) {
                        // 如果是图片链接，范围从 ! 开始；否则从 [ 开始
                        int begin = isImage ? i - 1 : i;
                        result.add(IntRangeBean.build(begin, urlEnd + 1));
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
            throw new IllegalArgumentException("posList and infos size must match");
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

    /**
     * linkPosList确定text内的一个区间范围，其中对应于链接。
     * 将这些位置的linkUrl替换为newUrls中对应的值
     */
    public static String changeLinkUrl(String text, List<IntRangeBean> linkPosList,
                                       List<String> newUrls) {
        if (text == null || linkPosList == null || newUrls == null) {
            return text;
        }

        // 如果范围个数与newUrls不匹配，抛出异常或处理
        if (linkPosList.size() != newUrls.size()) {
            throw new IllegalArgumentException("posList and newUrls size must match");
        }

        // 先排序范围，确保按照开始位置
        Collections.sort(linkPosList, Comparator.comparingInt(IntRangeBean::getBegin));

        StringBuilder sb = new StringBuilder();

        int currentIndex = 0; // 当前处理文本的位置

        for (int i = 0; i < linkPosList.size(); i++) {
            IntRangeBean range = linkPosList.get(i);
            String newUrl = newUrls.get(i);

            // 让范围合法
            int begin = Math.max(0, range.getBegin());
            int end = Math.min(text.length(), range.getEnd());

            // 复制中间未处理部分
            if (currentIndex < begin) {
                sb.append(text, currentIndex, begin);
            }

            // 处理链接部分，替换URL
            String linkText = text.substring(begin, end);
            int urlStartPos = linkText.indexOf("](");
            int urlEndPos = linkText.lastIndexOf(')');

            if (urlStartPos != -1 && urlEndPos != -1) {
                // 保留 ![alt]( 或 [text]( 部分
                sb.append(linkText, 0, urlStartPos + 2);
                // 插入新URL
                sb.append(newUrl);
                // 保留 ) 部分
                sb.append(linkText.substring(urlEndPos));
            } else {
                // 如果格式不对，保持原样
                sb.append(linkText);
            }

            currentIndex = end;
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

    public static String buildMappingTable(Collection<String> list, String sourceField, String targetField) {
        return MarkdownTableHelper.buildMappingTable(list, sourceField, targetField);
    }

    public static Map<String, String> parseMappingTable(SourceLocation loc, String text) {
        return MarkdownTableHelper.parseMappingTable(loc, text);
    }

    public static boolean isOrderedItem(String content) {
        int pos = content.indexOf('.');
        if (pos < 0)
            return false;
        return StringHelper.isInt(content.substring(0, pos).trim(), false);
    }

    public static List<Map<String, Object>> toRecordList(ITableView table) {
        return MarkdownTableHelper.toRecordList(table);
    }
}