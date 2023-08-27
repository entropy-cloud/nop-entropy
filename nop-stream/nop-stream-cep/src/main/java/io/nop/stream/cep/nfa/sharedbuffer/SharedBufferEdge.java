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

package io.nop.stream.cep.nfa.sharedbuffer;

import io.nop.stream.cep.nfa.DeweyNumber;

import java.util.Objects;

/**
 * Versioned edge in {@link SharedBuffer} that allows retrieving predecessors.
 */
public class SharedBufferEdge {

    private final NodeId target;
    private final DeweyNumber deweyNumber;

    /**
     * Creates versioned (with {@link DeweyNumber}) edge that points to the target entry.
     *
     * @param target      id of target entry
     * @param deweyNumber version for this edge
     */
    public SharedBufferEdge(NodeId target, DeweyNumber deweyNumber) {
        this.target = target;
        this.deweyNumber = deweyNumber;
    }

    NodeId getTarget() {
        return target;
    }

    DeweyNumber getDeweyNumber() {
        return deweyNumber;
    }

    @Override
    public String toString() {
        return "SharedBufferEdge{" + "target=" + target + ", deweyNumber=" + deweyNumber + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SharedBufferEdge that = (SharedBufferEdge) o;
        return Objects.equals(target, that.target) && Objects.equals(deweyNumber, that.deweyNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, deweyNumber);
    }

}
