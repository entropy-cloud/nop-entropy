package io.nop.stream.core.util;

import io.nop.api.core.annotations.core.Internal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Internal
public class ClassNameValidator {

    private static final List<String> ALLOWED_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            "io.nop.stream.",
            "io.nop.commons.",
            "io.nop.core.",
            "io.nop.dao.",
            "java.",
            "javax.",
            "jakarta.",
            "[L",
            "[B",
            "[I",
            "[J",
            "[F",
            "[D",
            "[S",
            "[C",
            "[Z"
    ));

    public static void validateClassName(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Class name must not be null or empty");
        }
        for (String prefix : ALLOWED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return;
            }
        }
        throw new SecurityException("Class not allowed for dynamic loading: " + className +
                ". Allowed prefixes: " + ALLOWED_PREFIXES);
    }
}
