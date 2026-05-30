package io.nop.stream.core.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

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
            "[Lio.nop.",
            "[Ljava.",
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
            throw new StreamException(ERR_STREAM_CLASS_NOT_ALLOWED).param(ARG_CLASS_NAME, "null or empty");
        }
        for (String prefix : ALLOWED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return;
            }
        }
        throw new StreamException(ERR_STREAM_CLASS_NOT_ALLOWED).param(ARG_CLASS_NAME, className);
    }
}
