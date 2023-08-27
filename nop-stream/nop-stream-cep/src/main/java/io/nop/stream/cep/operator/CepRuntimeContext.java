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

package io.nop.stream.cep.operator;


import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.common.functions.RuntimeContext;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A wrapper class for the {@link RuntimeContext}.
 *
 * <p>This context only exposes the functionality needed by the pattern process function and
 * iterative condition function. Consequently, state access, accumulators, broadcast variables and
 * the distributed cache are disabled.
 */
@Internal
class CepRuntimeContext implements RuntimeContext {

    private final RuntimeContext runtimeContext;

    CepRuntimeContext(final RuntimeContext runtimeContext) {
        this.runtimeContext = checkNotNull(runtimeContext);
    }

//    @Override
//    public JobID getJobId() {
//        return runtimeContext.getJobId();
//    }
//
//    @Override
//    public String getTaskName() {
//        return runtimeContext.getTaskName();
//    }
//
//    @Override
//    public OperatorMetricGroup getMetricGroup() {
//        return runtimeContext.getMetricGroup();
//    }
//
//    @Override
//    public int getNumberOfParallelSubtasks() {
//        return runtimeContext.getNumberOfParallelSubtasks();
//    }
//
//    @Override
//    public int getMaxNumberOfParallelSubtasks() {
//        return runtimeContext.getMaxNumberOfParallelSubtasks();
//    }
//
//    @Override
//    public int getIndexOfThisSubtask() {
//        return runtimeContext.getIndexOfThisSubtask();
//    }
//
//    @Override
//    public int getAttemptNumber() {
//        return runtimeContext.getAttemptNumber();
//    }
//
//    @Override
//    public String getTaskNameWithSubtasks() {
//        return runtimeContext.getTaskNameWithSubtasks();
//    }
//
//    @Override
//    public ExecutionConfig getExecutionConfig() {
//        return runtimeContext.getExecutionConfig();
//    }
//
//    @Override
//    public ClassLoader getUserCodeClassLoader() {
//        return runtimeContext.getUserCodeClassLoader();
//    }
//
//    @Override
//    public void registerUserCodeClassLoaderReleaseHookIfAbsent(
//            String releaseHookName, Runnable releaseHook) {
//        runtimeContext.registerUserCodeClassLoaderReleaseHookIfAbsent(releaseHookName, releaseHook);
//    }
//
//    @Override
//    public DistributedCache getDistributedCache() {
//        return runtimeContext.getDistributedCache();
//    }
//
//    @Override
//    public Set<ExternalResourceInfo> getExternalResourceInfos(String resourceName) {
//        return runtimeContext.getExternalResourceInfos(resourceName);
//    }
//
//    // -----------------------------------------------------------------------------------
//    // Unsupported operations
//    // -----------------------------------------------------------------------------------
//
//    @Override
//    public <V, A extends Serializable> void addAccumulator(
//            final String name, final Accumulator<V, A> accumulator) {
//        throw new UnsupportedOperationException("Accumulators are not supported.");
//    }
//
//    @Override
//    public <V, A extends Serializable> Accumulator<V, A> getAccumulator(final String name) {
//        throw new UnsupportedOperationException("Accumulators are not supported.");
//    }
//
//    @Override
//    public IntCounter getIntCounter(final String name) {
//        throw new UnsupportedOperationException("Int counters are not supported.");
//    }
//
//    @Override
//    public LongCounter getLongCounter(final String name) {
//        throw new UnsupportedOperationException("Long counters are not supported.");
//    }
//
//    @Override
//    public DoubleCounter getDoubleCounter(final String name) {
//        throw new UnsupportedOperationException("Double counters are not supported.");
//    }
//
//    @Override
//    public Histogram getHistogram(final String name) {
//        throw new UnsupportedOperationException("Histograms are not supported.");
//    }
//
//    @Override
//    public boolean hasBroadcastVariable(final String name) {
//        throw new UnsupportedOperationException("Broadcast variables are not supported.");
//    }
//
//    @Override
//    public <RT> List<RT> getBroadcastVariable(final String name) {
//        throw new UnsupportedOperationException("Broadcast variables are not supported.");
//    }
//
//    @Override
//    public <T, C> C getBroadcastVariableWithInitializer(
//            final String name, final BroadcastVariableInitializer<T, C> initializer) {
//        throw new UnsupportedOperationException("Broadcast variables are not supported.");
//    }
//
//    @Override
//    public <T> ValueState<T> getState(final ValueStateDescriptor<T> stateProperties) {
//        throw new UnsupportedOperationException("State is not supported.");
//    }
//
//    @Override
//    public <T> ListState<T> getListState(final ListStateDescriptor<T> stateProperties) {
//        throw new UnsupportedOperationException("State is not supported.");
//    }
//
//    @Override
//    public <T> ReducingState<T> getReducingState(final ReducingStateDescriptor<T> stateProperties) {
//        throw new UnsupportedOperationException("State is not supported.");
//    }
//
//    @Override
//    public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
//            final AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
//        throw new UnsupportedOperationException("State is not supported.");
//    }
//
//    @Override
//    public <UK, UV> MapState<UK, UV> getMapState(final MapStateDescriptor<UK, UV> stateProperties) {
//        throw new UnsupportedOperationException("State is not supported.");
//    }
}
