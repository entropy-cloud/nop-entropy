/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.functions;

import io.nop.commons.functional.IEqualsChecker;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class RankCompute {

    public static class RankResult<T> {
        private final List<T> list;
        private final int[] ranks;

        public RankResult(List<T> list, int[] ranks) {
            this.list = list;
            this.ranks = ranks;
        }

        public int[] getRanks() {
            return ranks;
        }

        public List<T> getList() {
            return list;
        }

        public int getRank(T item) {
            int index = list.indexOf(item);
            if (index < 0)
                return -1;
            return ranks[index];
        }
    }

    public static <T, V> RankResult<T> computeRank(List<T> list,
                                                   Function<T, V> getter,
                                                   Comparator<V> comparator,
                                                   IEqualsChecker<V> equalsChecker) {
        RankItem<V>[] items = _computeRank(list, getter, comparator, equalsChecker);
        int[] ranks = new int[list.size()];
        for (RankItem<V> item : items) {
            ranks[item.index] = item.rank;
        }
        return new RankResult<>(list, ranks);
    }

    private static <T, V> RankItem<V>[] _computeRank(List<T> list,
                                                     Function<T, V> getter,
                                                     Comparator<V> comparator,
                                                     IEqualsChecker<V> equalsChecker) {
        RankItem<V>[] items = new RankItem[list.size()];
        for (int i = 0, n = list.size(); i < n; i++) {
            RankItem<V> item = new RankItem<>();
            item.value = getter.apply(list.get(i));
            item.index = i;
            items[i] = item;
        }
        Arrays.sort(items, (o1, o2) -> comparator.compare(o1.value, o2.value));

        RankItem<V> prev = null;
        int count = 0;
        for (RankItem<V> item : items) {
            count++;
            if (prev != null && equalsChecker.isEquals(prev.value, item.value)) {
                item.rank = prev.rank;
            } else {
                prev = item;
                item.rank = count;
            }
        }

        return items;
    }

    public static class RankItem<V> {
        public V value;
        public int index;
        public int rank;
    }
}