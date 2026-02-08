/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import jakarta.annotation.Nonnull;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class ArrayHelper {
    public static Object[] EMPTY_OBJECTS = new Object[0];

    public static boolean isEmpty(Object array) {
        if (array == null)
            return true;

        return Array.getLength(array) <= 0;
    }

    /**
     * 指定位置设置数组元素。如果下标越界，则先扩展数据到指定长度
     *
     * @param array
     * @param i
     * @param o
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] assign(@Nonnull T[] array, int i, T o) {
        if (array.length <= i) {
            T[] newArray = (T[]) Array.newInstance(array.getClass().getComponentType(), i + 1);
            System.arraycopy(array, 0, newArray, 0, array.length);
            array = newArray;
        }
        array[i] = o;
        return array;
    }

    public static <T> T[] assign(T[] array, int i, T o, Class<?> componentType) {
        if (array == null) {
            Class<?> type = componentType == null ? o.getClass() : componentType;
            array = (T[]) Array.newInstance(type, i + 1);
        }
        return assign(array, i, o);
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] append(T[] array, T o, Class<?> componentType) {
        int i;
        if (array == null) {
            i = 0;
            Class<?> type = componentType == null ? o.getClass() : componentType;
            array = (T[]) Array.newInstance(type, 1);
            array[0] = o;
            return array;
        } else {
            i = array.length;
            if (componentType != null) {
                T[] newArray = (T[]) Array.newInstance(componentType, i + 1);
                System.arraycopy(array, 0, newArray, 0, array.length);
                newArray[i] = o;
                return newArray;
            }
        }
        return assign(array, i, o);
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] slice(T[] ary, int start, int end) {
        int n = end - start;
        T[] newArray = (T[]) Array.newInstance(ary.getClass().getComponentType(), n);
        System.arraycopy(ary, start, newArray, 0, n);
        return newArray;
    }

    public static <T> T[] slice(T[] ary, int start) {
        return slice(ary, start, ary.length);
    }

    public static Object[] concat(Object[] a, Object[] b) {
        if (a == EMPTY_OBJECTS || a.length == 0)
            return b;
        if (b == EMPTY_OBJECTS || b.length == 0)
            return a;

        Object[] ret = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, ret, a.length, b.length);
        return ret;
    }

    public static Object[] setupVarArgs(Object[] args, Class<?> arrayElementType, int argCount) {
        int count = argCount;
        int nargs = args.length;
        if (nargs > count) {
            Object[] newArgs = new Object[count];
            int pos = count - 1;
            Object vargs = Array.newInstance(arrayElementType, args.length - pos);
            System.arraycopy(args, 0, newArgs, 0, pos);
            System.arraycopy(args, pos, vargs, 0, args.length - pos);
            newArgs[pos] = vargs;
            return newArgs;
        } else if (nargs == count) {
            int pos = count - 1;
            if (!(args[pos] instanceof Object[])) {
                Object varArgs = Array.newInstance(arrayElementType, 1);
                Array.set(varArgs, 0, args[pos]);
                args[pos] = varArgs;
            }
            return args;
        } else if (nargs == count - 1) {
            Object[] newArgs = new Object[count];
            int pos = count - 1;
            System.arraycopy(args, 0, newArgs, 0, pos);
            newArgs[pos] = arrayElementType == Object.class ? EMPTY_OBJECTS : Array.newInstance(arrayElementType, 0);
            return newArgs;
        } else {
            return args;
        }
    }

    public static <T> int indexOf(T[] array, Object o) {
        if (array == null)
            return -1;
        for (int i = 0, n = array.length; i < n; i++) {
            if (Objects.equals(array[i], o))
                return i;
        }
        return -1;
    }

    public static <T> int indexOf(int[] array, int o) {
        if (array == null)
            return -1;
        for (int i = 0, n = array.length; i < n; i++) {
            if (array[i] == o)
                return i;
        }
        return -1;
    }

    public static <T> int indexOf(char[] array, char o) {
        if (array == null)
            return -1;
        for (int i = 0, n = array.length; i < n; i++) {
            if (array[i] == o)
                return i;
        }
        return -1;
    }

    public static <T> T get(T[] array, int i) {
        if (array == null || array.length <= i)
            return null;
        return array[i];
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(Collection<?> coll, Class<T> clazz) {
        if (coll == null)
            return null;
        T[] array = (T[]) Array.newInstance(clazz, coll.size());
        return coll.toArray(array);
    }

    public static <T> T[] iteratorToArray(Iterator<?> it, Class<T> clazz) {
        if (it == null)
            return null;
        List<Object> coll = new ArrayList<Object>();
        while (it.hasNext()) {
            coll.add(it.next());
        }
        return toArray(coll, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> toList(Object array) {
        if (array == null)
            return null;
        int len = Array.getLength(array);
        List<T> list = new ArrayList<T>(len);
        for (int i = 0; i < len; i++) {
            T o = (T) Array.get(array, i);
            list.add(o);
        }
        return list;
    }

    public static <T> T first(T[] array) {
        if (array == null || array.length <= 0)
            return null;
        return array[0];
    }

    public static <T> T last(T[] array) {
        if (array == null || array.length <= 0)
            return null;
        return array[array.length - 1];
    }
}
