/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.math.BigDecimal;
import java.util.Map;

import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConstants;
import io.nop.stream.core.common.functions.FlatMapFunction;
import io.nop.stream.core.util.Collector;
import io.nop.stream.fraud.model.TransactionEvent;

/**
 * S1 decode function: converts a CDC {@link ChangeEvent} (the record shape produced by
 * {@code DebeziumCdcSourceFunction}) into a {@link TransactionEvent}. Only insert and
 * update semantics are forwarded (delete/read events carry no "after" payload for the
 * pipeline); malformed insert/update payloads fail fast instead of being silently
 * dropped.
 *
 * <p>Registered as the {@code bean} of the {@code <flatMap>} decode transform in
 * {@code fraud-s1-cdc.stream.xml}.
 */
public class CdcChangeDecoder implements FlatMapFunction<ChangeEvent, TransactionEvent> {

    private static final long serialVersionUID = 1L;

    @Override
    public void flatMap(ChangeEvent event, Collector<TransactionEvent> out) {
        String op = event.getOperation();
        if (DebeziumConstants.OP_DELETE.equals(op) || DebeziumConstants.OP_READ.equals(op)) {
            // delete/read rows have no after-image to feed the fraud pipeline
            return;
        }

        Map<String, Object> after = event.getAfter();
        if (after == null) {
            throw new IllegalStateException("CDC event with op=" + op + " has no after payload: " + event);
        }

        Object transactionId = after.get("transactionId");
        Object userId = after.get("userId");
        Object amount = after.get("amount");
        Object city = after.get("city");
        Object eventType = after.get("eventType");

        if (transactionId == null || userId == null || amount == null) {
            throw new IllegalStateException("CDC after payload is missing required fields "
                    + "(transactionId/userId/amount): " + after);
        }

        BigDecimal decimalAmount = new BigDecimal(String.valueOf(amount));
        TransactionEvent tx = new TransactionEvent(
                String.valueOf(transactionId),
                String.valueOf(userId),
                decimalAmount,
                city != null ? String.valueOf(city) : "unknown",
                event.getTimestamp(),
                eventType != null ? String.valueOf(eventType) : "PURCHASE");
        out.collect(tx);
    }
}
