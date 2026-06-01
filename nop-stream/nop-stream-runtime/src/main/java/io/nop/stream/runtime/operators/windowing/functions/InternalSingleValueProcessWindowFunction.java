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

package io.nop.stream.runtime.operators.windowing.functions;

import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.windows.Window;

import java.util.Collections;

public class InternalSingleValueProcessWindowFunction<IN, OUT, KEY, W extends Window>
        implements InternalWindowFunction<IN, OUT, KEY, W> {

    private static final long serialVersionUID = 1L;

    private final ProcessWindowFunction<IN, OUT, KEY, W> wrappedFunction;

    public InternalSingleValueProcessWindowFunction(ProcessWindowFunction<IN, OUT, KEY, W> wrappedFunction) {
        this.wrappedFunction = wrappedFunction;
    }

    @Override
    public void process(KEY key, W window, InternalWindowContext context, IN input, Collector<OUT> out)
            throws Exception {
        ProcessWindowFunction.Context pwfContext =
                new ProcessWindowContextAdapter<>(key, window, context);
        wrappedFunction.process(key, window, Collections.singletonList(input), pwfContext, out);
    }

    @Override
    public void clear(W window, InternalWindowContext context) {
    }

    private static class ProcessWindowContextAdapter<KEY, W extends Window>
            extends ProcessWindowFunction.Context {

        private final KEY key;
        private final W window;
        private final InternalWindowFunction.InternalWindowContext internalContext;

        ProcessWindowContextAdapter(KEY key, W window, InternalWindowFunction.InternalWindowContext internalContext) {
            this.key = key;
            this.window = window;
            this.internalContext = internalContext;
        }

        @Override
        public long currentProcessingTime() {
            return internalContext.currentProcessingTime();
        }

        @Override
        public long currentWatermark() {
            return internalContext.currentWatermark();
        }

        @Override
        public KeyedStateStore windowState() {
            return internalContext.windowState();
        }

        @Override
        public KeyedStateStore globalState() {
            return internalContext.globalState();
        }

        @Override
        public <X> void output(OutputTag<X> outputTag, X value) {
            internalContext.output(outputTag, value);
        }
    }
}
