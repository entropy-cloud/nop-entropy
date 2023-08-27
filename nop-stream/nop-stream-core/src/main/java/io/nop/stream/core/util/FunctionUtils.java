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

package io.nop.stream.core.util;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.configuration.Configuration;
import io.nop.stream.core.common.functions.RichFunction;
import io.nop.stream.core.common.functions.RuntimeContext;
import io.nop.stream.core.common.functions.StreamFunction;

/**
 * Utility class that contains helper methods to work with Flink {@link Function} class.
 */
@Internal
public final class FunctionUtils {

    public static void openFunction(StreamFunction function, Configuration parameters){
        if (function instanceof RichFunction) {
            RichFunction richFunction = (RichFunction) function;
            richFunction.open(parameters);
        }
    }

    public static void closeFunction(StreamFunction function) {
        if (function instanceof RichFunction) {
            RichFunction richFunction = (RichFunction) function;
            richFunction.close();
        }
    }

    public static void setFunctionRuntimeContext(StreamFunction function, RuntimeContext context) {
        if (function instanceof RichFunction) {
            RichFunction richFunction = (RichFunction) function;
            richFunction.setRuntimeContext(context);
        }
    }

    public static RuntimeContext getFunctionRuntimeContext(
            StreamFunction function, RuntimeContext defaultContext) {
        if (function instanceof RichFunction) {
            RichFunction richFunction = (RichFunction) function;
            return richFunction.getRuntimeContext();
        } else {
            return defaultContext;
        }
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private FunctionUtils() {
        throw new RuntimeException();
    }
}
