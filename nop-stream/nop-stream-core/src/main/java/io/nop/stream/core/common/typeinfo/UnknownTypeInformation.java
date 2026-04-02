/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.typeinfo;

/**
 * A placeholder {@link TypeInformation} used when the output type cannot be statically inferred
 * (e.g. for {@code map()} and {@code flatMap()} without explicit type hints).
 *
 * <p>Using this class instead of {@code null} prevents NullPointerExceptions when type
 * information is propagated through the transformation DAG, and provides a clear extension
 * point for future type-inference support.
 *
 * @param <T> the (unknown) element type
 */
@SuppressWarnings("unchecked")
public class UnknownTypeInformation<T> implements TypeInformation<T> {

    /** Singleton instance – safe to share across all unknown-type transformations. */
    public static final UnknownTypeInformation<?> INSTANCE = new UnknownTypeInformation<>();

    private UnknownTypeInformation() {
    }

    /**
     * Returns {@code Object.class} as the best approximation for an unknown type.
     */
    @Override
    public Class<T> getTypeClass() {
        return (Class<T>) Object.class;
    }

    @Override
    public String toString() {
        return "UnknownType";
    }
}
