package io.nop.commons.collections.iterator;

import io.nop.commons.collections.IterableIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * 高阶抽象：按用户给的规则把 Iterator<T> 聚合成 Iterator<List<T>>。
 *
 * @param <T> 元素类型
 */
public final class BatchingIterator<T> implements IterableIterator<List<T>> {

    /**
     * 源迭代器
     */
    private final PushbackIterator<T> source;

    /**
     * 策略接口：决定什么时候结束当前批次并开始新批次
     */
    private final BatchStrategy<T> strategy;

    /**
     * 正在构建的当前批次
     */
    private final List<T> currentBatch = new ArrayList<>();

    /**
     * 策略内部状态（可变）
     */
    private final Object strategyState;

    /**
     * 构造器
     *
     * @param source   源迭代器
     * @param strategy 聚合策略
     */
    public BatchingIterator(Iterator<T> source, BatchStrategy<T> strategy) {
        this.source = new PushbackIterator<>(Objects.requireNonNull(source));
        this.strategy = Objects.requireNonNull(strategy);
        this.strategyState = strategy.initialState();
    }

    @Override
    public boolean hasNext() {
        return !currentBatch.isEmpty() || source.hasNext();
    }

    @Override
    public List<T> next() {
        if (!hasNext()) throw new NoSuchElementException();

        currentBatch.clear();
        while (source.hasNext()) {
            T element = source.next();
            BatchStrategy.Decision decision = strategy.shouldFinishBatch(element, currentBatch, strategyState);
            switch (decision) {
                case ACCEPT_AND_FINISH:
                    currentBatch.add(element);
                    return cloneAndClear(currentBatch);  // 拷贝一份，防止外部修改
                case ACCEPT_AND_CONTINUE:
                    currentBatch.add(element);
                    break;
                case REJECT_AND_FINISH:
                    // 把元素推回源迭代器（使用 PeekingIterator 技巧）
                    source.pushback(element);
                    return cloneAndClear(currentBatch);
            }
        }
        return cloneAndClear(currentBatch);  // 源已耗尽，返回剩余
    }

    List<T> cloneAndClear(List<T> list) {
        List<T> ret = new ArrayList<>(list);
        list.clear();
        return ret;
    }

    /* ---------------- 策略接口 ---------------- */

    public interface BatchStrategy<T> {
        enum Decision {
            ACCEPT_AND_CONTINUE,
            ACCEPT_AND_FINISH,
            REJECT_AND_FINISH
        }

        /**
         * 创建策略内部可变状态对象，可为 null
         */
        default Object initialState() {
            return null;
        }

        /**
         * 判断当前批次是否应该结束
         *
         * @param nextElement  源迭代器下一个元素
         * @param currentBatch 当前已收集的批次（不包含 nextElement）
         * @param state        策略内部状态
         * @return 决策
         */
        Decision shouldFinishBatch(T nextElement, List<T> currentBatch, Object state);
    }

    /* ---------------- 小工具 ---------------- */

    /**
     * 把元素推回流迭代器最前面
     */
    private static <E> Iterator<E> pushBack(Iterator<E> source, E element) {
        List<E> tmp = new ArrayList<>();
        tmp.add(element);
        source.forEachRemaining(tmp::add);
        return tmp.iterator();
    }
}