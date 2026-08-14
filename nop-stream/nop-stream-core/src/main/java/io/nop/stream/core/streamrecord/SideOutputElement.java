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

package io.nop.stream.core.streamrecord;

/**
 * A side-output element carrying a tagged {@link StreamRecord} across task boundaries.
 *
 * <p>HG-01 wire protocol: producers emit {@code collect(OutputTag, record)} on
 * {@code RecordWriterOutput} / {@code BroadcastingRecordWriterOutput} — the tagged record is
 * wrapped into this element and forwarded over the local/remote data plane; consumers route it
 * to the registered side-output consumer by tag id (HG-01, 2026-08-14).
 *
 * <p>Only the tag {@code id} travels with the element; the {@code OutputTag} object itself is
 * task-local state (its {@code TypeInformation} is not serialized). The embedded record keeps
 * its own value/timestamp; {@link #isRecord()} stays {@code false} for this subclass so existing
 * record branches (operator chaining, materialization dual-write gate) are unaffected.
 */
public class SideOutputElement extends StreamElement {

    private final String outputTagId;

    private final StreamRecord<?> record;

    public SideOutputElement(String outputTagId, StreamRecord<?> record) {
        this.outputTagId = outputTagId;
        this.record = record;
    }

    public String getOutputTagId() {
        return outputTagId;
    }

    public StreamRecord<?> getRecord() {
        return record;
    }

    /**
     * Returns a copy of this element with a copied inner record, so broadcasting the same
     * side output to multiple partitions never aliases a shared mutable record instance
     * (HG-01 Phase 1 decision, record copy).
     */
    @SuppressWarnings("unchecked")
    public SideOutputElement copy() {
        Object value = record.getValue();
        StreamRecord<Object> copy;
        if (record.hasTimestamp()) {
            copy = new StreamRecord<>(value, record.getTimestamp());
        } else {
            copy = new StreamRecord<>(value);
        }
        return new SideOutputElement(outputTagId, copy);
    }
}
