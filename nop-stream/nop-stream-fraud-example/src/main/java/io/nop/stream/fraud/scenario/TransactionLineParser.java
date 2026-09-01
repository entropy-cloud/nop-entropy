/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.math.BigDecimal;

import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.fraud.model.TransactionEvent;

/**
 * S2 line parser: parses a {@code userId,amount,eventTime} transaction-log line into a
 * {@link TransactionEvent}. Registered as the {@code bean} of the parse {@code <map>}
 * transform in {@code fraud-s2-file.stream.xml} (bean form instead of inline xpl
 * because the typed-model construction and validation logic exceeds a pure expression).
 *
 * <p>Malformed lines fail fast (no silent skip): a bounded file source that silently
 * drops bad lines would under-report aggregates.
 */
public class TransactionLineParser implements MapFunction<String, TransactionEvent> {

    private static final long serialVersionUID = 1L;

    @Override
    public TransactionEvent map(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalStateException("Blank transaction log line");
        }
        String[] parts = line.split(",");
        if (parts.length < 3) {
            throw new IllegalStateException("Malformed transaction log line (expected "
                    + "userId,amount,eventTime): '" + line + "'");
        }
        String userId = parts[0].trim();
        BigDecimal amount = new BigDecimal(parts[1].trim());
        long eventTime = Long.parseLong(parts[2].trim());
        if (userId.isEmpty()) {
            throw new IllegalStateException("Malformed transaction log line (empty userId): '" + line + "'");
        }
        String transactionId = "tx-" + userId + "-" + eventTime;
        return new TransactionEvent(transactionId, userId, amount, "n/a", eventTime, "PURCHASE");
    }
}
