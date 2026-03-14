/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.state;

import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.fraud.model.TransactionEvent;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility class for managing user transaction statistics using MemoryStateBackend.
 * Tracks transaction count and total amount per user for fraud detection.
 */
public class UserTransactionHistory {
    
    private static final ValueStateDescriptor<Long> COUNT_STATE_DESC = 
            new ValueStateDescriptor<>("transactionCount", Long.class);
    
    private static final ValueStateDescriptor<BigDecimal> TOTAL_STATE_DESC = 
            new ValueStateDescriptor<>("totalAmount", BigDecimal.class);
    
    private final ValueState<Long> countState;
    private final ValueState<BigDecimal> totalState;
    
    /**
     * Initialize UserTransactionHistory with state from the provided KeyedStateStore.
     * 
     * @param stateStore The keyed state store to retrieve ValueState instances
     */
    public UserTransactionHistory(KeyedStateStore stateStore) {
        this.countState = stateStore.getState(COUNT_STATE_DESC);
        this.totalState = stateStore.getState(TOTAL_STATE_DESC);
    }
    
    /**
     * Update transaction statistics with a new transaction event.
     * Increments count and adds amount to total.
     * 
     * @param event The transaction event to process
     * @throws IOException If state access fails
     */
    public void update(TransactionEvent event) throws IOException {
        // Get current count (default to 0 for first-time users)
        Long currentCount = countState.value();
        if (currentCount == null) {
            currentCount = 0L;
        }
        
        // Get current total (default to 0 for first-time users)
        BigDecimal currentTotal = totalState.value();
        if (currentTotal == null) {
            currentTotal = BigDecimal.ZERO;
        }
        
        // Update count and total
        countState.update(currentCount + 1);
        totalState.update(currentTotal.add(event.getAmount()));
    }
    
    /**
     * Calculate the average transaction amount for the current user.
     * 
     * @return The average amount, or BigDecimal.ZERO if no transactions exist
     * @throws IOException If state access fails
     */
    public BigDecimal getAverage() throws IOException {
        Long count = countState.value();
        BigDecimal total = totalState.value();
        
        // Handle first-time user (no transactions yet)
        if (count == null || count == 0L || total == null) {
            return BigDecimal.ZERO;
        }
        
        // Calculate average with 2 decimal places
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * Get the transaction count for the current user.
     * 
     * @return The count of transactions, or 0 if no transactions exist
     * @throws IOException If state access fails
     */
    public long getCount() throws IOException {
        Long count = countState.value();
        return count != null ? count : 0L;
    }
    
    /**
     * Get the total amount for the current user.
     * 
     * @return The total amount, or BigDecimal.ZERO if no transactions exist
     * @throws IOException If state access fails
     */
    public BigDecimal getTotal() throws IOException {
        BigDecimal total = totalState.value();
        return total != null ? total : BigDecimal.ZERO;
    }
}
