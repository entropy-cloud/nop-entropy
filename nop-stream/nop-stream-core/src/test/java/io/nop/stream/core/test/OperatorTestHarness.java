/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.test;

import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;

import java.util.ArrayList;
import java.util.List;

/**
 * Test harness for testing stream operators.
 * 
 * <p>Provides utilities for:
 * <ul>
 *   <li>Setting up operator with mock state backend</li>
 *   <li>Processing elements and watermarks</li>
 *   <li>Collecting output for verification</li>
 *   <li>Managing timer service for time-based tests</li>
 * </ul>
 *
 * @param <K> The type of key
 * @param <IN> The type of input elements
 * @param <OUT> The type of output elements
 */
public class OperatorTestHarness<K, IN, OUT> {

    private final OneInputStreamOperator<IN, OUT> operator;
    private final IStateBackend stateBackend;
    private final IKeyedStateBackend<K> keyedStateBackend;
    private final TestOutput<OUT> output;
    private final MockInternalTimerService timerService;
    private final Class<K> keyClass;

    private K currentKey;
    private boolean isOpen = false;

    public OperatorTestHarness(
            OneInputStreamOperator<IN, OUT> operator,
            Class<K> keyClass) throws Exception {
        this(operator, keyClass, new MemoryStateBackend());
    }

    public OperatorTestHarness(
            OneInputStreamOperator<IN, OUT> operator,
            Class<K> keyClass,
            IStateBackend stateBackend) throws Exception {
        this.operator = operator;
        this.keyClass = keyClass;
        this.stateBackend = stateBackend;
        this.keyedStateBackend = stateBackend.createKeyedStateBackend(keyClass);
        this.output = new TestOutput<>();
        this.timerService = new MockInternalTimerService();
    }

    /**
     * Opens the operator, initializing state and timer service.
     */
    public void open() throws Exception {
        if (!isOpen) {
            operator.open();
            isOpen = true;
        }
    }

    /**
     * Closes the operator and releases resources.
     */
    public void close() throws Exception {
        if (isOpen) {
            operator.close();
            isOpen = false;
        }
        keyedStateBackend.close();
    }

    /**
     * Sets the current key for state operations.
     */
    public void setCurrentKey(K key) {
        this.currentKey = key;
        keyedStateBackend.setCurrentKey(key);
    }

    /**
     * Gets the current key.
     */
    public K getCurrentKey() {
        return currentKey;
    }

    /**
     * Processes an element with the given timestamp.
     */
    public void processElement(IN element, long timestamp) throws Exception {
        ensureOpen();
        operator.processElement(new StreamRecord<>(element, timestamp));
    }

    /**
     * Processes an element with no timestamp (processing time).
     */
    public void processElement(IN element) throws Exception {
        ensureOpen();
        operator.processElement(new StreamRecord<>(element));
    }

    /**
     * Processes a watermark.
     */
    public void processWatermark(long timestamp) throws Exception {
        ensureOpen();
        timerService.setCurrentWatermark(timestamp);
        operator.processWatermark(new Watermark(timestamp));
    }

    /**
     * Advances processing time and fires any registered timers.
     */
    public void advanceProcessingTime(long timestamp) throws Exception {
        timerService.advanceProcessingTime(timestamp);
    }

    /**
     * Gets all output elements.
     */
    public List<OUT> getOutput() {
        return output.getElements();
    }

    /**
     * Gets all output records (with timestamps).
     */
    public List<StreamRecord<OUT>> getOutputRecords() {
        return output.getRecords();
    }

    /**
     * Clears all collected output.
     */
    public void clearOutput() {
        output.clear();
    }

    /**
     * Gets the keyed state backend for direct state access.
     */
    public IKeyedStateBackend<K> getKeyedStateBackend() {
        return keyedStateBackend;
    }

    /**
     * Gets the timer service for timer manipulation.
     */
    public MockInternalTimerService getTimerService() {
        return timerService;
    }

    /**
     * Gets the test output for wiring to operator.
     */
    public TestOutput<OUT> getTestOutput() {
        return output;
    }

    private void ensureOpen() {
        if (!isOpen) {
            throw new IllegalStateException("Operator is not open. Call open() first.");
        }
    }
}
