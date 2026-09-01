/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.core.util.Collector;
import io.nop.stream.fraud.model.FraudAlert;
import io.nop.stream.fraud.model.TransactionEvent;

/**
 * S1 CEP select function: converts a pattern match over {@link EnrichedTransaction}
 * into a {@link FraudAlert}. One instance per fraud pattern (the fraud type is a
 * constructor constant), registered as the {@code bean} of a {@code <cep>} transform
 * in {@code fraud-s1-cdc.stream.xml}.
 *
 * <p>All derived fields are computed deterministically from the match (max event
 * timestamp, sum of amounts) so the scenario's expected outputs do not depend on
 * match-internal iteration order.
 */
public class FraudAlertPatternFunction extends PatternProcessFunction<EnrichedTransaction, FraudAlert> {

    private static final long serialVersionUID = 1L;

    private final String fraudType;

    public FraudAlertPatternFunction(String fraudType) {
        this.fraudType = fraudType;
    }

    @Override
    public void processMatch(Map<String, List<EnrichedTransaction>> match, Context ctx,
                             Collector<FraudAlert> out) {
        List<TransactionEvent> triggering = new ArrayList<>();
        String userId = null;
        long maxTimestamp = Long.MIN_VALUE;
        java.math.BigDecimal totalAmount = java.math.BigDecimal.ZERO;

        for (List<EnrichedTransaction> events : match.values()) {
            for (EnrichedTransaction enriched : events) {
                TransactionEvent event = enriched.getEvent();
                triggering.add(event);
                if (userId == null) {
                    userId = event.getUserId();
                }
                maxTimestamp = Math.max(maxTimestamp, event.getTimestamp());
                if (event.getAmount() != null) {
                    totalAmount = totalAmount.add(event.getAmount());
                }
            }
        }

        if (userId == null) {
            // A CEP match always carries at least one event; an empty match is a
            // contract violation — fail fast instead of emitting a hollow alert.
            throw new IllegalStateException("CEP match contains no events for pattern " + fraudType);
        }

        String alertId = "alert-" + fraudType + "-" + userId + "-" + maxTimestamp;
        String description = fraudType + " pattern matched with " + triggering.size()
                + " triggering events, total amount " + totalAmount.toPlainString();

        out.collect(new FraudAlert(alertId, fraudType, userId, description, maxTimestamp, triggering));
    }
}
