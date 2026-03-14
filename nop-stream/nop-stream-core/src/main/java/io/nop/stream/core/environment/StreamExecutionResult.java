/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.environment;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * The result of a stream execution. Contains information about the executed job.
 * <p>
 * This is a simplified version based on Apache Flink's JobExecutionResult.
 */
public class StreamExecutionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String jobName;
    private final long executionTime;
    private final Map<String, Object> accumulators;

    /**
     * Creates a new StreamExecutionResult.
     *
     * @param jobName       The name of the job
     * @param executionTime The execution time in milliseconds
     * @param accumulators   The accumulator results
     */
    public StreamExecutionResult(String jobName, long executionTime, Map<String, Object> accumulators) {
        this.jobName = jobName;
        this.executionTime = executionTime;
        this.accumulators = accumulators != null ? accumulators : Collections.emptyMap();
    }

    /**
     * Creates a new StreamExecutionResult with empty accumulators.
     *
     * @param jobName       The name of the job
     * @param executionTime The execution time in milliseconds
     */
    public StreamExecutionResult(String jobName, long executionTime) {
        this(jobName, executionTime, null);
    }

    /**
     * Gets the name of the executed job.
     *
     * @return The job name
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * Gets the execution time of the job in milliseconds.
     *
     * @return The execution time
     */
    public long getExecutionTime() {
        return executionTime;
    }

    /**
     * Gets the accumulator results.
     *
     * @return An unmodifiable map of accumulator results
     */
    public Map<String, Object> getAccumulatorResults() {
        return Collections.unmodifiableMap(accumulators);
    }

    /**
     * Gets the result of an accumulator with the given name.
     *
     * @param accumulatorName The name of the accumulator
     * @return The accumulator result, or null if not found
     */
    public Object getAccumulatorResult(String accumulatorName) {
        return accumulators.get(accumulatorName);
    }

    /**
     * Gets the result of an accumulator with the given name and type.
     *
     * @param accumulatorName The name of the accumulator
     * @param type            The expected type of the accumulator result
     * @param <T>             The type of the accumulator result
     * @return The accumulator result, or null if not found or not of the expected type
     */
    @SuppressWarnings("unchecked")
    public <T> T getAccumulatorResult(String accumulatorName, Class<T> type) {
        Object result = accumulators.get(accumulatorName);
        if (result != null && type.isAssignableFrom(result.getClass())) {
            return (T) result;
        }
        return null;
    }

    @Override
    public String toString() {
        return "StreamExecutionResult{" +
                "jobName='" + jobName + '\'' +
                ", executionTime=" + executionTime + " ms" +
                ", accumulators=" + accumulators.size() + " results" +
                '}';
    }
}
