/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections;

import java.util.*;
import java.util.function.BiFunction;

/**
 * 提供Javascript的Array相关函数
 */
public class ListFunctions extends SetFunctions {
    /**
     * forEach(callbackfn: (value: T, index: number, array: readonly T[]) => void, thisArg?: any): void;
     * <p>
     * 除了抛出异常以外，没有办法中止或跳出 forEach() 循环。如果你需要中止或跳出循环，forEach() 方法不是应当使用的工具。
     */
    // public static <T> void forEach(Iterable<T> list, BiConsumer<T, Integer> fn) {
    // int i = 0;
    // for (T value : list) {
    // fn.accept(value, i++);
    // }
    // }

    /**
     * pop(): T
     */
    public static <T> T pop(List<T> list) {
        return list.remove(list.size() - 1);
    }

    /**
     * push(...item: T): number
     * <p>
     * push并不负责拼接集合，push一个集合将使得该集合成为原集合中的一个元素，拼接集合应使用concat push改变原集合
     */
    public static <T> int push(List<T> list, T item) {
        list.add(item);
        return list.size();
    }

    public static <T> int push(List<T> list, T item, T... items) {
        list.add(item);
        Collections.addAll(list, items);
        return list.size();
    }

    /**
     * reduceRight(callbackfn:(item: T , anotherItem: T) => T): T
     */
    public static <R, T> R reduceRight(List<T> list, BiFunction<R, T, R> fnc, R initialValue) {
        if (list == null || list.size() == 0)
            return null;
        R ret = initialValue;
        for (int i = list.size() - 1; i >= 0; i--) {
            ret = fnc.apply(ret, list.get(i));
        }
        return ret;
    }

    /**
     * reverse(): T[]
     * <p>
     * reverse改变原集合,返回的是原集合 eg:var a = [1,2,3];var b = a.reverse(); a === b // true
     */
    public static <T> List<T> reverse(List<T> list) {
        Collections.reverse(list);
        return list;
    }

    /**
     * shift(): T
     */
    public static <T> T shift(List<T> list) {
        if (list.size() == 0)
            return null;
        return list.remove(0);
    }

    /**
     * slice(startIndex: number , endIndex?: number): T[]
     * <p>
     * slice不改变原集合
     */
    public static <T> List<T> slice(List<T> list, int startIndex, Integer endIndex) {
        if (startIndex < 0)
            startIndex = list.size() + startIndex;
        if (startIndex >= list.size())
            return new ArrayList<>(0);
        if (endIndex == null)
            return new ArrayList<>(list.subList(startIndex, list.size()));
        if (endIndex < 0)
            endIndex = list.size() + endIndex;
        if (endIndex > list.size())
            endIndex = list.size();
        if (endIndex < startIndex)
            return new ArrayList<>(0);
        return new ArrayList<>(list.subList(startIndex, endIndex));
    }


    /**
     * sort( callbackfn:(item: T , anotherItem: item) => number): T[]
     */
    // public static <T> List sort(List<T> list, Comparator<T> fnc) {
    // list.sort(fnc);
    // return list;
    // }

    /**
     * splice( index: number, number: number, ...value: T): T[]
     * <p>
     * splice并不负责拼接集合，splice一个集合将使得该集合成为原集合中的一个元素，拼接集合应使用concat splice改变原集合
     */
    public static <T> List<T> splice(List<T> list, int index, int number) {
        List<T> ret;
        // remove
        if (number != 0) {
            int endIndex = Math.min(list.size(), index + number);
            if (endIndex <= index)
                return new ArrayList<>(0);

            ret = new ArrayList<>(endIndex - index);
            for (int i = index; i < endIndex; i++) {
                ret.add(list.remove(index));
            }
        } else {
            ret = new ArrayList<>(0);
        }
        return ret;
    }

    public static <T> List<T> splice(List<T> list, int index, int number, T... values) {
        List<T> ret = splice(list, index, number);
        // add
        if (values != null) {
            expandList(list, index);
            list.addAll(index, Arrays.asList(values));
        }
        return ret;
    }

    static <T> void expandList(Collection<T> c, int index) {
        for (int i = c.size(); i < index; i++) {
            c.add(null);
        }
    }

    /**
     * unshift(...item: T): number
     * <p>
     * unshift并不负责拼接集合，unshift一个集合将使得该集合成为原集合中的一个元素，拼接集合应使用concat unshift改变原集合
     */
    public static <T> int unshift(List<T> list, T item) {
        list.add(0, item);
        return list.size();
    }

    public static <T> int unshift(List<T> list, T item, T... items) {
        if (items.length > 0) {
            list.addAll(0, Arrays.asList(items));
        }
        list.add(0, item);
        return list.size();
    }
}