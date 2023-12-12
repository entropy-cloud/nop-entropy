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

package io.nop.stream.cep.nfa.aftermatch;

import java.util.Optional;

/**
 * Discards every partial match that started before the first event of emitted match mapped to
 * *PatternName*.
 */
public final class SkipToFirstStrategy extends SkipToElementStrategy {
    private static final long serialVersionUID = 7127107527654629026L;

    SkipToFirstStrategy(String patternName, boolean shouldThrowException) {
        super(patternName, shouldThrowException);
    }

    @Override
    public SkipToElementStrategy throwExceptionOnMiss() {
        Optional<String> name = getPatternName();
        if(name.isEmpty())
            throw new IllegalArgumentException("null name");
        return new SkipToFirstStrategy(name.get(), true);
    }

    @Override
    int getIndex(int size) {
        return 0;
    }

    @Override
    public String toString() {
        String name = getPatternName().orElse("");
        return "SkipToFirstStrategy{" + "patternName='" + name + '\'' + '}';
    }
}
