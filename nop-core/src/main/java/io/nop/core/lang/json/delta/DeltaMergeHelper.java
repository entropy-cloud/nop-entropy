/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.delta;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.CoreConstants;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class DeltaMergeHelper {
    public static class MatchData {
        public int aIndex;
        public int bIndex;
        public Object diffValue;
        public boolean same;
    }

    /**
     * 合并结果满足如下要求
     * <ul>
     * 1. 保持b中元素的顺序
     * </ul>
     * <ul>
     * 2. 尽量保持a中元素的顺序
     * </ul>
     * <ul>
     * 3. b中新增的元素排在a中元素的后面
     * </ul>
     *
     * <p>
     * 基本算法为：
     * <ul>
     * 1. 将b追加到a的后面，合并成一个长的列表
     * </ul>
     * <ul>
     * 2. 寻找重复的基准元素，在保持b中元素顺序的情况下，移动a中的元素。
     * </ul>
     * <ul>
     * 3. b中新增的元素应该插入到紧邻的基准元素的后面。
     * </ul>
     *
     * <p>
     * 例如 a=[a1,a2,a3,a4,a5]与b=[b1, a2, b3] 合并， 先得到 all = [a1,a2,a3,a4,a5, b1,a2,b3], 然后发现a2重复，需要以a2元素为基准移动a中的元素
     * 得到[a1,b1,a2,b3,a3,a4,a5]，b3在b中紧接着a2，所以在移动后的序列中也紧接着a2。
     *
     * <p>
     * 如果a与b=[a1, b1, a3, b3]合并，则先得到[a1,a2,a3,a4,a5, a1,b1,a3,b3], 发现a1,a3重复，移动后得到[a1,b1,a2,a3,b3,a4,a5]。
     *
     * <p>
     * 基准元素用于定位时可以理解为代表它以及它的后续元素（直到遇到另外一个基准元素为止）。
     *
     * <p>
     * 如果a与b=[a3,b1,a1]合并，则先得到[a1,a2,a3,a4,a5, a3,b1,a1], 现在b中a3和a1颠倒了顺序，先移动a1得到[a3,a4,a5, a3,b1,a1,a2]，再移动a3得到
     * [a3,b1,a4,a5,a1,a2]
     *
     * @param aCount   列表a的长度
     * @param bIndexes 列表b的元素对应列表a中元素的下标。如果小于0，则表示列表b中新增的元素
     */
    public static <T> List<MatchData> mergeList(int aCount, int[] bIndexes) {
        int bCount = bIndexes.length;
        int matched = countMatched(bIndexes);

        List<MatchData> ret = new ArrayList<>(aCount + bCount - matched);

        if (matched == aCount) {
            // b完全包含a
            for (int i = 0; i < bCount; i++) {
                MatchData m = new MatchData();
                m.bIndex = i;
                m.aIndex = bIndexes[i];
                ret.add(m);
            }
        } else if (matched == 0) {
            // a 和 b完全不匹配
            for (int i = 0; i < aCount; i++) {
                MatchData m = new MatchData();
                m.aIndex = i;
                m.bIndex = -1;
                ret.add(m);
            }
            for (int i = 0; i < bCount; i++) {
                MatchData m = new MatchData();
                m.aIndex = -1;
                m.bIndex = i;
                ret.add(m);
            }
        } else {
            // 建立 a中元素和b中元素的映射表
            int[] aIndexes = new int[aCount];
            Arrays.fill(aIndexes, -1);

            for (int i = 0; i < bCount; i++) {
                int aIndex = bIndexes[i];
                if (aIndex >= 0) {
                    aIndexes[aIndex] = i;
                }
            }

            mergeList(aIndexes, bIndexes, ret);
        }
        return ret;
    }

    /**
     * 根据合并结果aIndexes，反向推导出差量定义
     *
     * @param bCount   列表b的长度
     * @param aIndexes 列表a的元素对应列表b中元素的下标。如果小于0，则表示列表a中新增的元素
     * @return 如果bIndex<0 ， 则表示是新增 ， 如果aIndex和bIndex都不为空 ， 则表示匹配节点
     */
    public static List<MatchData> diff(int[] aIndexes, int bCount) {
        int[] bIndexes = new int[bCount];
        Arrays.fill(bIndexes, -1);

        List<MatchData> list = new ArrayList<>(bCount + aIndexes.length);

        for (int i = 0, n = aIndexes.length; i < n; i++) {
            MatchData matched = new MatchData();
            int bIndex = aIndexes[i];
            matched.bIndex = bIndex;
            matched.aIndex = i;
            list.add(matched);

            if (bIndex >= 0) {
                bIndexes[bIndex] = i;
            }
        }

        // 插入被删除的节点
        for (int i = 0; i < bCount; i++) {
            // 在b中不存在，则表示需要被删除
            if (bIndexes[i] < 0) {
                int idx = i == 0 ? -1 : findByBIndex(list, i - 1);
                MatchData matched = new MatchData();
                matched.bIndex = i;
                matched.aIndex = -1;
                list.add(idx + 1, matched);
            }
        }
        return list;
    }

    private static int findByBIndex(List<MatchData> list, int bIndex) {
        for (int i = 0, n = list.size(); i < n; i++) {
            if (list.get(i).bIndex == bIndex)
                return i;
        }
        return 0;
    }

    private static int countMatched(int[] bIndexes) {
        int count = 0;
        for (int i = 0, n = bIndexes.length; i < n; i++) {
            if (bIndexes[i] >= 0)
                count++;
        }
        return count;
    }

    private static void mergeList(int[] aIndexes, int[] bIndexes, List<MatchData> ret) {
        int aCount = aIndexes.length;
        int bCount = bIndexes.length;

        for (int i = 0; i < bCount; i++) {
            MatchData m = new MatchData();
            m.bIndex = i;
            m.aIndex = bIndexes[i];
            ret.add(m);
        }

        int pos = 0;

        for (int i = 0; i < aCount; i++) {
            int bIndex = aIndexes[i];
            if (bIndex < 0) {
                MatchData m = new MatchData();
                m.aIndex = i;
                m.bIndex = -1;
                ret.add(pos, m);
                pos++;
            } else {
                // 查找匹配的元素在返回列表中的当前下标
                pos = findInsertPos(ret, bIndex);
            }
        }
    }

    static int findInsertPos(List<MatchData> list, int bIndex) {
        for (int i = bIndex, n = list.size(); i < n; i++) {
            MatchData m = list.get(i);
            if (m.bIndex == bIndex) {
                // 跳过新插入的记录
                for (int j = i + 1; j < n; j++) {
                    m = list.get(j);
                    // aIndex小于0表示在列表b中存在，而在列表a中不存在
                    if (m.aIndex < 0) {
                        i++;
                    } else {
                        break;
                    }
                }
                return i + 1;
            }
        }
        return -1;
    }

    public static <T> List<T> buildResult(List<T> aList, List<T> bList, List<MatchData> matched) {
        List<T> ret = new ArrayList<>(matched.size());
        for (MatchData m : matched) {
            int aIndex = m.aIndex;
            if (aIndex >= 0) {
                ret.add(aList.get(aIndex));
            } else {
                ret.add(bList.get(m.bIndex));
            }
        }
        return ret;
    }

    /**
     * 返回对象的唯一key
     */
    public static Pair<String, String> buildUniqueKey(Map<String, Object> map) {
        String keyAttr = ConvertHelper.toString(map.get(CoreConstants.ATTR_X_UNIQUE_ATTR));
        if (!StringHelper.isEmpty(keyAttr)) {
            String key = ConvertHelper.toString(map.get(keyAttr));
            if (StringHelper.isEmpty(key))
                return null;
            return Pair.of(keyAttr, key);
        }

        String key = ConvertHelper.toString(map.get(CoreConstants.ATTR_X_ID));
        if (!StringHelper.isEmpty(key))
            return Pair.of(CoreConstants.ATTR_X_ID, key);

        key = ConvertHelper.toString(map.get(CoreConstants.ATTR_V_ID));
        if (!StringHelper.isEmpty(key)) {
            return Pair.of(CoreConstants.ATTR_V_ID, key);
        }

        key = ConvertHelper.toString(map.get(CoreConstants.ATTR_ID));
        if (!StringHelper.isEmpty(key)) {
            return Pair.of(CoreConstants.ATTR_ID, key);
        }

        key = ConvertHelper.toString(map.get(CoreConstants.ATTR_NAME));
        if (!StringHelper.isEmpty(key)) {
            return Pair.of(CoreConstants.ATTR_NAME, key);
        }

        return null;
    }

    public static Pair<String, String> buildUniqueKey(XNode node) {
        String keyAttr = node.attrText(CoreConstants.ATTR_X_UNIQUE_ATTR);
        if (!StringHelper.isEmpty(keyAttr)) {
            String key = node.attrText(keyAttr);
            if (StringHelper.isEmpty(key))
                return null;
            return Pair.of(keyAttr, key);
        }

        String key = node.attrText(CoreConstants.ATTR_X_ID);
        if (key != null)
            return Pair.of(CoreConstants.ATTR_X_ID, key);

        key = node.attrText(CoreConstants.ATTR_V_ID);
        if (key != null) {
            return Pair.of(CoreConstants.ATTR_V_ID, key);
        }

        key = node.attrText(CoreConstants.ATTR_ID);
        if (key != null) {
            return Pair.of(CoreConstants.ATTR_ID, key);
        }

        key = node.attrText(CoreConstants.ATTR_NAME);
        if (key != null) {
            return Pair.of(CoreConstants.ATTR_NAME, key);
        }

        return null;
    }

    public static boolean containsUniqueKey(Map<String, Object> map) {
        String key = ConvertHelper.toString(map.get(CoreConstants.ATTR_X_ID));
        if (!StringHelper.isEmpty(key))
            return true;

        key = ConvertHelper.toString(map.get(CoreConstants.ATTR_V_ID));
        if (!StringHelper.isEmpty(key)) {
            return true;
        }

        key = ConvertHelper.toString(map.get(CoreConstants.ATTR_ID));
        if (!StringHelper.isEmpty(key)) {
            return true;
        }

        key = ConvertHelper.toString(map.get(CoreConstants.ATTR_NAME));
        if (!StringHelper.isEmpty(key)) {
            return true;
        }

        return false;
    }
}