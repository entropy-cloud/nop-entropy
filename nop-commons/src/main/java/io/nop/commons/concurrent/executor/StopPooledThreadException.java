/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.exceptions.NopSingletonException;

import static io.nop.commons.CommonErrors.ERR_CONCURRENT_STOP_POOLED_THREAD;

/**
 * A custom {@link RuntimeException} thrown by the {@link StandardThreadPoolExecutor} to signal that the thread should
 * be disposed of.
 */
public class StopPooledThreadException extends NopSingletonException {

    private static final long serialVersionUID = 1L;

    public static final StopPooledThreadException INSTANCE = new StopPooledThreadException();

    private StopPooledThreadException() {
        super(ERR_CONCURRENT_STOP_POOLED_THREAD);
    }
}