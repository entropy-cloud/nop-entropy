/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.io.IOException;
import java.math.BigDecimal;

import io.nop.stream.core.common.functions.KeyedProcessFunction;
import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.util.Collector;
import io.nop.stream.fraud.model.TransactionEvent;
import io.nop.stream.fraud.state.UserTransactionHistory;

/**
 * S1 keyed-state enrichment function: maintains per-user transaction history in keyed
 * {@code ValueState} and emits {@link EnrichedTransaction} records carrying the
 * user's PRIOR average amount and previous city.
 *
 * <p>This is the production-form revival of the previously unused
 * {@link UserTransactionHistory} sketch (fraud-example Gap B) and the de-stubbing of
 * the unusual-amount rule (the fixed $100 average is replaced by the real per-user
 * keyed-state average; CEP conditions in the XDSL consume the enriched fields).
 *
 * <p>State layout (all keyed by userId, restored via the keyed state backend on
 * checkpoint recovery):
 * <ul>
 *   <li>{@code transactionCount} / {@code totalAmount} — owned by
 *       {@link UserTransactionHistory}</li>
 *   <li>{@code lastCity} — the user's most recent transaction city</li>
 * </ul>
 */
public class UserHistoryEnricher extends KeyedProcessFunction<String, TransactionEvent, EnrichedTransaction> {

    private static final long serialVersionUID = 1L;

    private static final ValueStateDescriptor<String> LAST_CITY_STATE_DESC =
            new ValueStateDescriptor<>("lastCity", String.class);

    private transient ValueState<String> lastCityState;

    private ValueState<String> lastCityState() {
        if (lastCityState == null) {
            KeyedStateStore store = getRuntimeContext().getKeyedStateStore();
            lastCityState = store.getState(LAST_CITY_STATE_DESC);
        }
        return lastCityState;
    }

    @Override
    public void processElement(TransactionEvent event,
                               ProcessFunction<TransactionEvent, EnrichedTransaction>.Context ctx,
                               Collector<EnrichedTransaction> out) throws Exception {
        UserTransactionHistory history =
                new UserTransactionHistory(getRuntimeContext().getKeyedStateStore());

        // Prior-history snapshot BEFORE ingesting the current event: the unusual-amount
        // rule compares the current amount against the user's PRIOR average.
        BigDecimal priorAvg;
        long priorCount;
        String prevCity;
        try {
            priorAvg = history.getAverage();
            priorCount = history.getCount();
            prevCity = lastCityState().value();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read keyed transaction history", e);
        }

        try {
            history.update(event);
            lastCityState().update(event.getCity());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update keyed transaction history", e);
        }

        out.collect(new EnrichedTransaction(event, priorAvg, priorCount, prevCity));
    }
}
