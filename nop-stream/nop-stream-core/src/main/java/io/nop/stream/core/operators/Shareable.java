/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation declaring that a {@link StreamOperator} is safe to share
 * across parallel subtasks without copying.
 *
 * <p>An operator annotated {@code @Shareable} opts out of the
 * {@link StreamOperator#copyForSubtask()} contract: its
 * {@code copyForSubtask()} default implementation returns {@code this} instead
 * of throwing {@link UnsupportedOperationException}. This is appropriate only
 * for operators that hold <em>no</em> per-subtask mutable state (e.g. stateless
 * pass-through markers used in tests).
 *
 * <p><strong>Warning:</strong> incorrect use of this annotation on operators
 * with per-subtask mutable state will silently corrupt parallel execution by
 * sharing that state across subtasks.
 *
 * <p>See plan {@code 2026-07-26-0804-2-parallel-execution-cep-correctness.md}
 * Phase 1 for the No-Silent-No-Op contract this annotation supports.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Shareable {
}
