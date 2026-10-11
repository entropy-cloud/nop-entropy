package io.nop.refactor.core.operation;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One rewrite target the face has fully prepared for the operation: the
 * real disk path (the application entry writes through java.nio.file.Path),
 * the language id its extension resolved to, and the original bytes — read
 * behind the face's own caps, so the operation never touches the file
 * system on the way in (the face/operation boundary, plan 09 adjudication
 * 2). Immutable value; the bytes are shared, never mutated.
 */
public record PreparedTarget(Path path, String languageId, byte[] content) {

    public PreparedTarget {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(languageId, "languageId must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
