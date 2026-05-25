/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.core.operators;

import static java.util.Objects.requireNonNull;

import io.nop.stream.core.checkpoint.FunctionInitializationContext;
import io.nop.stream.core.checkpoint.FunctionSnapshotContext;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.ICheckpointedFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.StreamFunction;
import io.nop.stream.core.common.state.CheckpointListener;

import io.nop.stream.core.util.FunctionUtils;

/**
 * This is used as the base class for operators that have a user-defined function. This class
 * handles the opening and closing of the user-defined functions, as part of the operator life
 * cycle.
 *
 * @param <OUT> The output type of the operator
 * @param <F>   The type of the user function
 */
public abstract class AbstractUdfStreamOperator<OUT, F extends StreamFunction>
        extends AbstractStreamOperator<OUT> {

    private static final long serialVersionUID = 1L;

    /**
     * The user function.
     */
    protected final F userFunction;

    public AbstractUdfStreamOperator(F userFunction) {
        this.userFunction = requireNonNull(userFunction);
        // checkUdfCheckpointingPreconditions();
    }

    /**
     * Gets the user function executed in this operator.
     *
     * @return The user function of this operator.
     */
    public F getUserFunction() {
        return userFunction;
    }

    // ------------------------------------------------------------------------
    //  operator life cycle
    // ------------------------------------------------------------------------

    @Override
    public void open() throws Exception {
        super.open();
        FunctionUtils.setFunctionRuntimeContext(userFunction, new StreamingRuntimeContext());
        FunctionUtils.openFunction(userFunction, new io.nop.stream.core.configuration.Configuration() {});
    }

    @Override
    public void finish() throws Exception {
        super.finish();
        if (userFunction instanceof SinkFunction) {
            ((SinkFunction<?>) userFunction).finish();
        }
    }

    @Override
    public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
        OperatorSnapshotResult result = super.snapshotState(context);
        if (userFunction instanceof ICheckpointedFunction) {
            FunctionSnapshotContext fnCtx = new FunctionSnapshotContext() {
                private static final long serialVersionUID = 1L;

                @Override
                public long getCheckpointId() {
                    return context.getCheckpointId();
                }

                @Override
                public long getCheckpointTimestamp() {
                    return context.getTimestamp();
                }
            };
            ((ICheckpointedFunction) userFunction).snapshotState(fnCtx);
        }
        return result;
    }

    @Override
    public void initializeState(TaskStateSnapshot taskStateSnapshot) throws Exception {
        if (userFunction instanceof ICheckpointedFunction) {
            FunctionInitializationContext fnCtx = new FunctionInitializationContext() {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean isRestored() {
                    return taskStateSnapshot != null && !taskStateSnapshot.isEmpty();
                }
            };
            ((ICheckpointedFunction) userFunction).initializeState(fnCtx);
        }
    }

    @Override
    public void close() throws Exception {
        super.close();
        FunctionUtils.closeFunction(userFunction);
    }

    // ------------------------------------------------------------------------
    //  checkpointing and recovery
    // ------------------------------------------------------------------------

    @Override
    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        //super.notifyCheckpointComplete(checkpointId);

        if (userFunction instanceof CheckpointListener) {
            ((CheckpointListener) userFunction).notifyCheckpointComplete(checkpointId);
        }
    }

    @Override
    public void notifyCheckpointAborted(long checkpointId) throws Exception {
        super.notifyCheckpointAborted(checkpointId);

        if (userFunction instanceof CheckpointListener) {
            ((CheckpointListener) userFunction).notifyCheckpointAborted(checkpointId);
        }
    }

    // ------------------------------------------------------------------------
    //  Output type configuration
    // ------------------------------------------------------------------------
}
