package io.nop.commons.collections;

import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SetFunctions {
    public static <T> boolean includes(Collection<T> list, T item) {
        if (list == null)
            return false;
        return list.contains(item);
    }

    /**
     * some( callbackfn:(value: T) => boolean): boolean
     */
    public static <T> boolean some(Collection<T> list, Predicate<T> fn) {
        for (T item : list) {
            if (fn.test(item))
                return true;
        }
        return false;
    }

    /**
     * concat(...array: (T || T[])): T[];
     */
    public static <T> List<T> concat(Collection<T> list, Object source) {
        if (source instanceof Collection) {
            Collection<T> c = (Collection<T>) source;
            List<T> ret = new ArrayList<>(list.size() + c.size());
            list.addAll(list);
            ret.addAll(c);
            return ret;
        } else {
            List<T> ret = new ArrayList<>(list.size() + 1);
            ret.add((T) source);
            return ret;
        }
    }

    public static <T> List<T> concat(Collection<T> list, Object sourceItem, Object... sources) {

        List<T> ret = new ArrayList<>();
        ret.addAll(list);
        if (sourceItem instanceof Collection) {
            ret.addAll((Collection<T>) sourceItem);
        } else {
            ret.add((T) sourceItem);
        }
        for (Object source : sources) {
            if (source instanceof Collection) {
                ret.addAll((Collection<T>) source);
            } else {
                ret.add((T) source);
            }

        }
        return ret;
    }

    /**
     * every( callbackfn:(value: T) => boolean): boolean
     */
    public static <T> boolean every(Iterable<T> list, Predicate<T> fn) {
        for (T item : list) {
            if (!fn.test(item))
                return false;
        }
        return true;
    }

    /**
     * filter( callbackfn:(value: T) => boolean): T[]
     */
    public static <T> List<T> filter(Iterable<T> list, Predicate<T> fn) {
        List<T> ret = new ArrayList<>();
        for (T item : list) {
            if (fn.test(item))
                ret.add(item);
        }
        return ret;
    }

    // /**
    // * indexOf(item: T): number
    // */
    // public static <T> int indexOf(List<T> list, T item) {
    // return list.indexOf(item);
    // }
    //
    // /**
    // * lastIndexOf(item: T): number
    // */
    // public static <T> int lastIndexOf(List<T> list, T item) {
    // return list.lastIndexOf(item);
    // }

    /**
     * map(callbackfn:(value: T, index: number) => K): K[]
     */
    public static <T, K> List<K> map(Collection<T> list, Function<T, K> fn) {

        List<K> ret = new ArrayList<>(list.size());
        for (T item : list) {
            ret.add(fn.apply(item));
        }
        return ret;
    }

    public static <T, K> List<K> flatMap(Collection<T> list, Function<T, ?> fn) {

        List<K> ret = new ArrayList<>(list.size());
        for (T item : list) {
            Object v = fn.apply(item);
            if (v instanceof Collection) {
                ret.addAll((Collection<? extends K>) v);
            } else if (v instanceof Stream) {
                List<K> mapped = ((Stream<K>) v).collect(Collectors.toList());
                ret.addAll(mapped);
            } else {
                ret.add((K) item);
            }
        }
        return ret;
    }

    public static <T> String join(Collection<T> list, String sep) {
        return StringHelper.join(list, sep);
    }

    /**
     * reduce(callbackfn:(item: T , anotherItem: T) => T): T
     */
    public static <R, T> R reduce(Collection<T> list, BiFunction<R, T, R> fnc, R initialValue) {
        if (list == null || list.size() == 0)
            return initialValue;
        Iterator<T> it = list.iterator();
        R ret = initialValue;
        while (it.hasNext()) {
            T item = it.next();
            ret = fnc.apply(ret, item);
        }
        return ret;
    }

    /**
     * toString(): string
     */
    public static String toString(Collection<?> list) {
        StringBuilder sb = new StringBuilder();
        Iterator it = list.iterator();
        while (it.hasNext()) {
            Object item = it.next();
            sb.append(item);
            if (it.hasNext()) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    public static boolean hasNext(Collection<?> list, int index) {
        return index <= list.size() - 1;
    }
}
