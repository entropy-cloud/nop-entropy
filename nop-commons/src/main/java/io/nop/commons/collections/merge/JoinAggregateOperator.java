package io.nop.commons.collections.merge;

import io.nop.commons.collections.iterator.TransformIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class JoinAggregateOperator<T> implements AggregateIterator.AggregateOperator<T,
        JoinAggregateOperator.JoinResult<T>> {

    public static class JoinResult<T> {
        private final Object key;
        private final List<T> items = new ArrayList<>();

        public JoinResult(Object key) {
            this.key = key;
        }

        public boolean matchKey(Object key) {
            return Objects.equals(key, this.key);
        }

        public List<T> getItems() {
            return items;
        }

        public void addItem(T item) {
            items.add(item);
        }
    }

    private final Function<T, ?> keyGetter;
    private JoinResult<T> joinResult = null;

    public JoinAggregateOperator(Function<T, ?> keyGetter) {
        this.keyGetter = keyGetter;
    }

    public static <T> AggregateIterator<T, JoinResult<T>> mergeJoin(Iterator<T> it, Function<T, ?> keyGetter) {
        return new AggregateIterator<>(it, new JoinAggregateOperator<>(keyGetter));
    }

    public static <T, R> Iterator<R> mergeJoin(Iterator<T> it, Function<T, ?> keyGetter, Function<JoinResult<T>, R> transformer) {
        return new TransformIterator<>(mergeJoin(it, keyGetter), transformer);
    }

    @Override
    public JoinResult<T> aggregate(T value) {
        JoinResult<T> ret = null;
        Object key = keyGetter.apply(value);
        if (joinResult == null) {
            joinResult = new JoinResult<>(key);
        } else if (!joinResult.matchKey(key)) {
            ret = joinResult;
            joinResult = new JoinResult<>(key);
        }
        joinResult.addItem(value);
        return ret;
    }


    @Override
    public JoinResult<T> getFinalResult() {
        return joinResult;
    }
}
