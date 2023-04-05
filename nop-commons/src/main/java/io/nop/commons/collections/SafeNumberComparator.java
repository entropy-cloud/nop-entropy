package io.nop.commons.collections;

import io.nop.commons.util.MathHelper;

import java.util.Comparator;

public class SafeNumberComparator implements Comparator<Number> {
    public static Comparator<Number> ASC = new SafeNumberComparator();

    public static Comparator<Number> DESC = ASC.reversed();

    @Override
    public int compare(Number o1, Number o2) {
        if (o1 == o2)
            return 0;
        if (o1 == null)
            return -1;
        if (o2 == null)
            return 1;
        return MathHelper.compareWithConversion(o1, o2);
    }
}