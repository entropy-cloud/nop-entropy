/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph;

import java.io.Serializable;

/**
 * Represents an executable task in the streaming job.
 *
 * <p>An Invokable is the actual executable unit that performs the work
 * for a JobVertex in the execution graph. It encapsulates the operator
 * logic that will be executed in parallel across multiple task instances.
 *
 * <p>This is a minimal placeholder interface. The full implementation will
 * include methods for:
 * <ul>
 *   <li>Invoking the operator logic</li>
 *   <li>Managing lifecycle (open, close)</li>
 *   <li>Handling checkpoints and recovery</li>
 *   <li>Processing input streams and producing outputs</li>
 * </ul>
 *
 * @param <T> The type of data processed by this invokable
 * @see JobVertex
 * @see OperatorChain
 */
public interface Invokable<T> extends Serializable {

    /**
     * Placeholder method for invokable execution.
     * Full implementation will invoke the operator logic.
     */
    void invoke() throws Exception;
}
