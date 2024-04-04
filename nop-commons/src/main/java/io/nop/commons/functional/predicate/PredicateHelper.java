package io.nop.commons.functional.predicate;

import java.util.List;
import java.util.function.Predicate;

public class PredicateHelper {
    public static <T> Predicate<T> buildAndPredicate(List<Predicate<T>> list) {
        if (list == null || list.isEmpty())
            return null;

        if (list.size() == 1)
            return list.get(0);

        // 从列表中获取最后一个Predicate作为起始点
        Predicate<T> result = list.get(list.size() - 1);

        for (int i = list.size() - 2; i >= 0; i--) {
            result = new AndPredicate<>(list.get(i), result);
        }
        return result;
    }

    public static <T> Predicate<T> buildOrPredicate(List<Predicate<T>> list) {
        if (list == null || list.isEmpty())
            return null;

        if (list.size() == 1)
            return list.get(0);

        // 从列表中获取最后一个Predicate作为起始点
        Predicate<T> result = list.get(list.size() - 1);

        for (int i = list.size() - 2; i >= 0; i--) {
            result = new OrPredicate<>(list.get(i), result);
        }
        return result;
    }
}
