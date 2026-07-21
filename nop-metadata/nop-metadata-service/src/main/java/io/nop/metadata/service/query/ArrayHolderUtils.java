package io.nop.metadata.service.query;

import java.util.List;
import java.util.Map;

public class ArrayHolderUtils {
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>>[] newArrayHolder() {
        return (List<Map<String, Object>>[]) new List<?>[1];
    }

    private ArrayHolderUtils() {
    }
}
