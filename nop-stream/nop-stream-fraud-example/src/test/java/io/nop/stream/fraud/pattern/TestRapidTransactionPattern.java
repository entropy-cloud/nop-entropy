/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.pattern;

import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.fraud.model.FraudAlert;
import io.nop.stream.fraud.model.TransactionEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for RapidTransactionPattern.
 */
public class TestRapidTransactionPattern {

    @Test
    public void testPatternCreation() {
        Pattern<TransactionEvent, ?> pattern = RapidTransactionPattern.createPattern();
        
        assertNotNull(pattern, "Pattern should not be null");
        assertEquals("second", pattern.getName(), "Pattern should end with 'second' event");
        assertNotNull(pattern.getPrevious(), "Pattern should have a previous node");
        assertEquals("first", pattern.getPrevious().getName(), "Previous pattern should be 'first'");
    }

    @Test
    public void testGenerateAlert() {
        // Create test events
        TransactionEvent firstEvent = new TransactionEvent(
            "txn-001",
            "user-123",
            new BigDecimal("1500"),
            "New York",
            System.currentTimeMillis() - 10000,
            "PURCHASE"
        );

        TransactionEvent secondEvent = new TransactionEvent(
            "txn-002",
            "user-123",
            new BigDecimal("2000"),
            "New York",
            System.currentTimeMillis(),
            "PURCHASE"
        );

        // Create match map
        Map<String, List<TransactionEvent>> match = new HashMap<>();
        match.put("first", List.of(firstEvent));
        match.put("second", List.of(secondEvent));

        // Generate alert
        FraudAlert alert = RapidTransactionPattern.generateAlert(match);

        // Verify alert properties
        assertNotNull(alert, "Alert should not be null");
        assertNotNull(alert.getAlertId(), "Alert ID should not be null");
        assertEquals("RAPID_TRANSACTION", alert.getFraudType(), "Fraud type should be RAPID_TRANSACTION");
        assertEquals("user-123", alert.getUserId(), "User ID should match");
        assertNotNull(alert.getDescription(), "Description should not be null");
        assertEquals(secondEvent.getTimestamp(), alert.getTimestamp(), "Timestamp should match second event");
        
        List<TransactionEvent> triggeringEvents = alert.getTriggeringEvents();
        assertNotNull(triggeringEvents, "Triggering events should not be null");
        assertEquals(2, triggeringEvents.size(), "Should have 2 triggering events");
        assertTrue(triggeringEvents.contains(firstEvent), "Should contain first event");
        assertTrue(triggeringEvents.contains(secondEvent), "Should contain second event");
    }

    @Test
    public void testGenerateAlertWithInvalidMatch() {
        Map<String, List<TransactionEvent>> match = new HashMap<>();
        
        // Test with missing 'first' event
        assertThrows(IllegalArgumentException.class, () -> {
            RapidTransactionPattern.generateAlert(match);
        }, "Should throw exception when 'first' event is missing");

        // Test with missing 'second' event
        match.put("first", List.of());
        assertThrows(IllegalArgumentException.class, () -> {
            RapidTransactionPattern.generateAlert(match);
        }, "Should throw exception when 'first' event list is empty");
    }

    @Test
    public void testConstants() {
        assertEquals(new BigDecimal("1000"), RapidTransactionPattern.getAmountThreshold(),
            "Amount threshold should be 1000");
        assertEquals(30, RapidTransactionPattern.getTimeWindowSeconds(),
            "Time window should be 30 seconds");
        assertEquals("RAPID_TRANSACTION", RapidTransactionPattern.getFraudType(),
            "Fraud type should be RAPID_TRANSACTION");
    }
}
