package io.nop.commons.text;

import java.util.Collection;

public class EditDistance {
    public static int calculate(String word1, String word2) {
        int m = word1.length();
        int n = word2.length();

        int[] dp = new int[n + 1];

        // 初始化第一行（空字符串转换为word2的前j个字符）
        for (int j = 0; j <= n; j++) {
            dp[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            int prevDiagonal = dp[0]; // 保存左上角的值
            dp[0] = i; // 相当于word1的前i个字符转换为空字符串

            for (int j = 1; j <= n; j++) {
                int temp = dp[j]; // 保存当前值，下一次迭代会成为prevDiagonal
                if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    dp[j] = prevDiagonal;
                } else {
                    dp[j] = 1 + Math.min(Math.min(
                                    dp[j],     // 相当于上一行的值（删除）
                                    dp[j - 1]), // 当前行的前一个值（插入）
                            prevDiagonal // 替换
                    );
                }
                prevDiagonal = temp;
            }
        }

        return dp[n];
    }

    /**
     * 从集合中选择与目标单词最相似的单词
     *
     * @param words       待选择的单词集合，不应为null但可以为空
     * @param word        目标匹配单词，不应为null
     * @param maxDistance 允许的最大距离阈值，只有距离小于等于此值的单词才会被考虑
     * @return 如果集合中包含目标单词，则直接返回目标单词；否则返回集合中与目标单词距离最小
     * 且在maxDistance范围内的单词。如果没有符合条件的单词则返回null。
     * @throws NullPointerException 如果words或word参数为null
     */
    public static String choose(Collection<String> words, String word, int maxDistance) {
        if (words.contains(word))
            return word;

        int minDistance = Integer.MAX_VALUE;
        String minWord = null;

        for (String w : words) {
            int d = calculate(w, word);
            if (d <= maxDistance) {
                if (d < minDistance) {
                    minDistance = d;
                    minWord = w;
                }
            }
        }

        return minWord;
    }
}