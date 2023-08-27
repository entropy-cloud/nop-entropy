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

package io.nop.stream.cep.functions.adaptors;

import io.nop.stream.cep.PatternFlatSelectFunction;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.core.configuration.Configuration;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.FunctionUtils;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Adapter that expresses {@link PatternFlatSelectFunction} with {@link PatternProcessFunction}.
 */

public class PatternFlatSelectAdapter<IN, OUT> extends PatternProcessFunction<IN, OUT> {

    private final PatternFlatSelectFunction<IN, OUT> flatSelectFunction;

    public PatternFlatSelectAdapter(final PatternFlatSelectFunction<IN, OUT> flatSelectFunction) {
        this.flatSelectFunction = checkNotNull(flatSelectFunction);
    }

    @Override
    public void open(final Configuration parameters) {
        FunctionUtils.setFunctionRuntimeContext(flatSelectFunction, getRuntimeContext());
        FunctionUtils.openFunction(flatSelectFunction, parameters);
    }

    @Override
    public void close() {
        FunctionUtils.closeFunction(flatSelectFunction);
    }

    @Override
    public void processMatch(
            final Map<String, List<IN>> match, final Context ctx, final Collector<OUT> out)
            throws Exception {
        flatSelectFunction.flatSelect(match, out);
    }
}
