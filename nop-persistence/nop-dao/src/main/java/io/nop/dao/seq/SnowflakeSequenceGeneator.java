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
package io.nop.dao.seq;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static io.nop.dao.DaoErrors.ARG_LAST_TS;
import static io.nop.dao.DaoErrors.ARG_TS;
import static io.nop.dao.DaoErrors.ERR_DAO_INVALID_TIMESTAMP;

// copy from https://github.com/Meituan-Dianping/Leaf/blob/master/leaf-core/src/main/java/com/sankuai/inf/leaf/snowflake/SnowflakeIDGenImpl.java

public class SnowflakeSequenceGeneator implements ISequenceGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowflakeSequenceGeneator.class);

    private final long twepoch;

    private static final long workerIdBits = 10L;

    public static final long MAX_WORKER_ID = ~(-1L << workerIdBits);// 最大能够分配的workerid =1023

    private final long sequenceBits = 12L;

    private final long workerIdShift = sequenceBits;

    private final long timestampLeftShift = sequenceBits + workerIdBits;

    private final long sequenceMask = ~(-1L << sequenceBits);

    private long workerId;

    private long sequence = 0L;

    private long lastTimestamp = -1L;

    /**
     * @param twepoch 起始的时间戳
     */

    public SnowflakeSequenceGeneator(long workerId, long twepoch) {
        this.twepoch = twepoch;
        this.workerId = workerId;

        Guard.checkArgument(timeGen() > twepoch, "Snowflake not support twepoch gt currentTime");
        LOGGER.info("twepoch:{} ,workerId={}", twepoch, workerId);
        Guard.checkArgument(workerId >= 0 && workerId <= MAX_WORKER_ID, "workerID must gte 0 and lte 1023");
    }

    public SnowflakeSequenceGeneator(long workerId) {
        this(workerId, CoreMetrics.currentTimeMillis() - 1);
    }

    @Override
    public synchronized long generateLong(String seqName, boolean useDefault) {
        long timestamp = timeGen();

        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                try {
                    wait(offset << 1);
                    timestamp = timeGen();
                    if (timestamp < lastTimestamp) {
                        throw new NopException(ERR_DAO_INVALID_TIMESTAMP)
                                .param(ARG_TS, timestamp)
                                .param(ARG_LAST_TS, lastTimestamp);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw NopException.adapt(e);
                }

            } else {
                throw new NopException(ERR_DAO_INVALID_TIMESTAMP)
                        .param(ARG_TS, timestamp)
                        .param(ARG_LAST_TS, lastTimestamp);
            }
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;

            if (sequence == 0) {
                // seq 为0的时候表示是下一毫秒时间开始对seq做随机
                sequence = MathHelper.secureRandom().nextInt(100);
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 如果是新的ms开始
            sequence = MathHelper.secureRandom().nextInt(100);
        }

        lastTimestamp = timestamp;
        long id = ((timestamp - twepoch) << timestampLeftShift) | (workerId << workerIdShift) | sequence;

        return id;
    }

    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();

        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }

        return timestamp;
    }

    protected long timeGen() {
        return CoreMetrics.currentTimeMillis();
    }

    public long getWorkerId() {
        return workerId;
    }
}