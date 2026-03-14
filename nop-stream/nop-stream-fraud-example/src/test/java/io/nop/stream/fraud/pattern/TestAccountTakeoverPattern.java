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
 * Test cases for AccountTakeoverPattern.
 */
public class TestAccountTakeoverPattern {

    @Test
    public void testPatternCreation() {
        Pattern<TransactionEvent, ?> pattern = AccountTakeoverPattern.createPattern();
        
        assertNotNull(pattern, "Pattern should not be null");
        assertEquals("withdraw", pattern.getName(), "Pattern should end with 'withdraw' event");
        assertNotNull(pattern.getPrevious(), "Pattern should have a previous node");
        assertEquals("change", pattern.getPrevious().getName(), "Previous pattern should be 'change'");
        assertNotNull(pattern.getPrevious().getPrevious(), "Pattern should have a grandparent node");
        assertEquals("login", pattern.getPrevious().getPrevious().getName(), "Grandparent pattern should be 'login'");
    }

    @Test
    public void testGenerateAlertWithValidSequence() {
        // Create test events for valid account takeover pattern
        TransactionEvent loginEvent = new TransactionEvent(
            "txn-001",
            "user-123",
            BigDecimal.ZERO,
            "New York",
            System.currentTimeMillis() - 900000, // 15 minutes ago
            "LOGIN"
        );

        TransactionEvent changeEvent = new TransactionEvent(
            "txn-002",
            "user-123",
            BigDecimal.ZERO,
            "New York",
            System.currentTimeMillis() - 600000, // 10 minutes ago
            "CHANGE_PASSWORD"
        );

        TransactionEvent withdrawEvent = new TransactionEvent(
            "txn-003",
            "user-123",
            new BigDecimal("5000"),
            "New York",
            System.currentTimeMillis(),
            "WITHDRAWAL"
        );

        // Create match map
        Map<String, List<TransactionEvent>> match = new HashMap<>();
        match.put("login", List.of(loginEvent));
        match.put("change", List.of(changeEvent));
        match.put("withdraw", List.of(withdrawEvent));

        // Generate alert
        FraudAlert alert = AccountTakeoverPattern.generateAlert(match);

        // Verify alert properties
        assertNotNull(alert, "Alert should not be null");
        assertNotNull(alert.getAlertId(), "Alert ID should not be null");
        assertEquals("ACCOUNT_TAKEOVER", alert.getFraudType(), "Fraud type should be ACCOUNT_TAKEOVER");
        assertEquals("user-123", alert.getUserId(), "User ID should match");
        assertNotNull(alert.getDescription(), "Description should not be null");
        assertEquals(withdrawEvent.getTimestamp(), alert.getTimestamp(), "Timestamp should match withdraw event");
        
        List<TransactionEvent> triggeringEvents = alert.getTriggeringEvents();
        assertNotNull(triggeringEvents, "Triggering events should not be null");
        assertEquals(3, triggeringEvents.size(), "Should have 3 triggering events");
        assertTrue(triggeringEvents.contains(loginEvent), "Should contain login event");
        assertTrue(triggeringEvents.contains(changeEvent), "Should contain change event");
        assertTrue(triggeringEvents.contains(withdrawEvent), "Should contain withdraw event");
    }

    @Test
    public void testGenerateAlertRejectsDifferentUsers() {
        // Create test events from different users
        TransactionEvent loginEvent = new TransactionEvent(
            "txn-001",
            "user-123",
            BigDecimal.ZERO,
            "New York",
            System.currentTimeMillis() - 900000,
            "LOGIN"
        );

        TransactionEvent changeEvent = new TransactionEvent(
            "txn-002",
            "user-456", // Different user
            BigDecimal.ZERO,
            "New York",
            System.currentTimeMillis() - 600000,
            "CHANGE_PASSWORD"
        );

        TransactionEvent withdrawEvent = new TransactionEvent(
            "txn-003",
            "user-789", // Different user
            new BigDecimal("5000"),
            "New York",
            System.currentTimeMillis(),
            "WITHDRAWAL"
        );

        // Create match map
        Map<String, List<TransactionEvent>> match = new HashMap<>();
        match.put("login", List.of(loginEvent));
        match.put("change", List.of(changeEvent));
        match.put("withdraw", List.of(withdrawEvent));

        // Should throw exception because users are different
        assertThrows(IllegalArgumentException.class, () -> {
            AccountTakeoverPattern.generateAlert(match);
        }, "Should throw exception when users are different");
    }

    @Test
    public void testGenerateAlertRejectsWrongEventTypes() {
        // Create test events with wrong event types
        TransactionEvent loginEvent = new TransactionEvent(
            "txn-001",
            "user-123",
            BigDecimal.ZERO,
            "New York",
            System.currentTimeMillis() - 900000,
            "PURCHASE" // Wrong type, should be LOGIN
        );

        TransactionEvent changeEvent = new TransactionEvent(
            "txn-002",
            "user-123",
            BigDecimal.ZERO,
            "New York",
            System.currentTimeMillis() - 600000,
            "CHANGE_PASSWORD"
        );

        TransactionEvent withdrawEvent = new TransactionEvent(
            "txn-003",
            "user-123",
            new BigDecimal("5000"),
            "New York",
            System.currentTimeMillis(),
            "WITHDRAWAL"
        );

        // Create match map
        Map<String, List<TransactionEvent>> match = new HashMap<>();
        match.put("login", List.of(loginEvent));
        match.put("change", List.of(changeEvent));
        match.put("withdraw", List.of(withdrawEvent));

        // Should throw exception because event type is wrong
        assertThrows(IllegalArgumentException.class, () -> {
            AccountTakeoverPattern.generateAlert(match);
        }, "Should throw exception when event type is wrong");
    }

    @Test
    public void testGenerateAlertWithMissingEvents() {
        Map<String, List<TransactionEvent>> match = new HashMap<>();
        
        // Test with missing all events
        assertThrows(IllegalArgumentException.class, () -> {
            AccountTakeoverPattern.generateAlert(match);
        }, "Should throw exception when all events are missing");

        // Test with missing 'change' and 'withdraw' events
        match.put("login", List.of(new TransactionEvent(
            "txn-001",
            "user-123",
            BigDecimal.ZERO,
            "New York",
            System.currentTimeMillis(),
            "LOGIN"
        )));
        assertThrows(IllegalArgumentException.class, () -> {
            AccountTakeoverPattern.generateAlert(match);
        }, "Should throw exception when 'change' and 'withdraw' events are missing");

        // Test with empty event lists
        match.put("login", List.of());
        match.put("change", List.of());
        match.put("withdraw", List.of());
        assertThrows(IllegalArgumentException.class, () -> {
            AccountTakeoverPattern.generateAlert(match);
        }, "Should throw exception when event lists are empty");
    }

    @Test
    public void testConstants() {
        assertEquals(15, AccountTakeoverPattern.getTimeWindowMinutes(),
            "Time window should be 15 minutes");
        assertEquals("ACCOUNT_TAKEOVER", AccountTakeoverPattern.getFraudType(),
            "Fraud type should be ACCOUNT_TAKEOVER");
    }

    @Test
    public void testGenerateAlertDescription() {
        // Create test events
        TransactionEvent loginEvent = new TransactionEvent(
            "txn-login-001",
            "user-999",
            BigDecimal.ZERO,
            "Boston",
            1700000000000L,
            "LOGIN"
        );

        TransactionEvent changeEvent = new TransactionEvent(
            "txn-change-002",
            "user-999",
            BigDecimal.ZERO,
            "Boston",
            1700000300000L,
            "CHANGE_PASSWORD"
        );

        TransactionEvent withdrawEvent = new TransactionEvent(
            "txn-withdraw-003",
            "user-999",
            new BigDecimal("7500"),
            "Boston",
            1700000600000L,
            "WITHDRAWAL"
        );

        // Create match map
        Map<String, List<TransactionEvent>> match = new HashMap<>();
        match.put("login", List.of(loginEvent));
        match.put("change", List.of(changeEvent));
        match.put("withdraw", List.of(withdrawEvent));

        // Generate alert
        FraudAlert alert = AccountTakeoverPattern.generateAlert(match);

        // Verify description contains key information
        String description = alert.getDescription();
        assertTrue(description.contains("user-999"), "Description should contain user ID");
        assertTrue(description.contains("Boston"), "Description should contain city");
        assertTrue(description.contains("txn-login-001"), "Description should contain login transaction ID");
        assertTrue(description.contains("txn-change-002"), "Description should contain change transaction ID");
        assertTrue(description.contains("txn-withdraw-003"), "Description should contain withdraw transaction ID");
        assertTrue(description.contains("7500"), "Description should contain withdrawal amount");
        assertTrue(description.contains("15"), "Description should mention 15 minutes time window");
    }

    @Test
    public void testPatternSequenceOrder() {
        // Verify that the pattern enforces strict sequence order
        Pattern<TransactionEvent, ?> pattern = AccountTakeoverPattern.createPattern();
        
        // Navigate through the pattern chain
        Pattern<?, ?> withdraw = pattern;
        assertEquals("withdraw", withdraw.getName());
        
        Pattern<?, ?> change = withdraw.getPrevious();
        assertNotNull(change, "Should have 'change' pattern");
        assertEquals("change", change.getName());
        
        Pattern<?, ?> login = change.getPrevious();
        assertNotNull(login, "Should have 'login' pattern");
        assertEquals("login", login.getName());
        
        // Verify this is the beginning of the pattern
        assertNull(login.getPrevious(), "'login' should be the first pattern in the sequence");
    }
}
