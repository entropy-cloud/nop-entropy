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
 * Test cases for GeographicAnomalyPattern.
 */
public class TestGeographicAnomalyPattern {

    @Test
    public void testPatternCreation() {
        Pattern<TransactionEvent, ?> pattern = GeographicAnomalyPattern.createPattern();
        
        assertNotNull(pattern, "Pattern should not be null");
        assertEquals("city2", pattern.getName(), "Pattern should end with 'city2' event");
        assertNotNull(pattern.getPrevious(), "Pattern should have a previous node");
        assertEquals("city1", pattern.getPrevious().getName(), "Previous pattern should be 'city1'");
    }

    @Test
    public void testGenerateAlertWithDifferentCities() {
        // Create test events from different cities
        TransactionEvent firstEvent = new TransactionEvent(
            "txn-001",
            "user-123",
            new BigDecimal("100"),
            "New York",
            System.currentTimeMillis() - 1800000, // 30 minutes ago
            "PURCHASE"
        );

        TransactionEvent secondEvent = new TransactionEvent(
            "txn-002",
            "user-123",
            new BigDecimal("150"),
            "Los Angeles",
            System.currentTimeMillis(),
            "PURCHASE"
        );

        // Create match map
        Map<String, List<TransactionEvent>> match = new HashMap<>();
        match.put("city1", List.of(firstEvent));
        match.put("city2", List.of(secondEvent));

        // Generate alert
        FraudAlert alert = GeographicAnomalyPattern.generateAlert(match);

        // Verify alert properties
        assertNotNull(alert, "Alert should not be null");
        assertNotNull(alert.getAlertId(), "Alert ID should not be null");
        assertEquals("GEOGRAPHIC_ANOMALY", alert.getFraudType(), "Fraud type should be GEOGRAPHIC_ANOMALY");
        assertEquals("user-123", alert.getUserId(), "User ID should match");
        assertNotNull(alert.getDescription(), "Description should not be null");
        assertTrue(alert.getDescription().contains("New York"), "Description should contain first city");
        assertTrue(alert.getDescription().contains("Los Angeles"), "Description should contain second city");
        assertEquals(secondEvent.getTimestamp(), alert.getTimestamp(), "Timestamp should match second event");
        
        List<TransactionEvent> triggeringEvents = alert.getTriggeringEvents();
        assertNotNull(triggeringEvents, "Triggering events should not be null");
        assertEquals(2, triggeringEvents.size(), "Should have 2 triggering events");
        assertTrue(triggeringEvents.contains(firstEvent), "Should contain first event");
        assertTrue(triggeringEvents.contains(secondEvent), "Should contain second event");
    }

    @Test
    public void testGenerateAlertRejectsSameCity() {
        // Create test events from the SAME city
        TransactionEvent firstEvent = new TransactionEvent(
            "txn-001",
            "user-123",
            new BigDecimal("100"),
            "New York",
            System.currentTimeMillis() - 1800000,
            "PURCHASE"
        );

        TransactionEvent secondEvent = new TransactionEvent(
            "txn-002",
            "user-123",
            new BigDecimal("150"),
            "New York", // Same city as first event
            System.currentTimeMillis(),
            "PURCHASE"
        );

        // Create match map
        Map<String, List<TransactionEvent>> match = new HashMap<>();
        match.put("city1", List.of(firstEvent));
        match.put("city2", List.of(secondEvent));

        // Should throw exception because cities are the same
        assertThrows(IllegalArgumentException.class, () -> {
            GeographicAnomalyPattern.generateAlert(match);
        }, "Should throw exception when cities are the same");
    }

    @Test
    public void testGenerateAlertRejectsDifferentUsers() {
        // Create test events from different users
        TransactionEvent firstEvent = new TransactionEvent(
            "txn-001",
            "user-123",
            new BigDecimal("100"),
            "New York",
            System.currentTimeMillis() - 1800000,
            "PURCHASE"
        );

        TransactionEvent secondEvent = new TransactionEvent(
            "txn-002",
            "user-456", // Different user
            new BigDecimal("150"),
            "Los Angeles",
            System.currentTimeMillis(),
            "PURCHASE"
        );

        // Create match map
        Map<String, List<TransactionEvent>> match = new HashMap<>();
        match.put("city1", List.of(firstEvent));
        match.put("city2", List.of(secondEvent));

        // Should throw exception because users are different
        assertThrows(IllegalArgumentException.class, () -> {
            GeographicAnomalyPattern.generateAlert(match);
        }, "Should throw exception when users are different");
    }

    @Test
    public void testGenerateAlertWithInvalidMatch() {
        Map<String, List<TransactionEvent>> match = new HashMap<>();
        
        // Test with missing 'city1' event
        assertThrows(IllegalArgumentException.class, () -> {
            GeographicAnomalyPattern.generateAlert(match);
        }, "Should throw exception when 'city1' event is missing");

        // Test with missing 'city2' event
        match.put("city1", List.of());
        assertThrows(IllegalArgumentException.class, () -> {
            GeographicAnomalyPattern.generateAlert(match);
        }, "Should throw exception when 'city1' event list is empty");
    }

    @Test
    public void testConstants() {
        assertEquals(1, GeographicAnomalyPattern.getTimeWindowHours(),
            "Time window should be 1 hour");
        assertEquals("GEOGRAPHIC_ANOMALY", GeographicAnomalyPattern.getFraudType(),
            "Fraud type should be GEOGRAPHIC_ANOMALY");
    }

    @Test
    public void testGenerateAlertDescription() {
        // Create test events
        TransactionEvent firstEvent = new TransactionEvent(
            "txn-001",
            "user-789",
            new BigDecimal("200"),
            "Chicago",
            1700000000000L,
            "PURCHASE"
        );

        TransactionEvent secondEvent = new TransactionEvent(
            "txn-002",
            "user-789",
            new BigDecimal("300"),
            "Miami",
            1700000300000L,
            "PURCHASE"
        );

        // Create match map
        Map<String, List<TransactionEvent>> match = new HashMap<>();
        match.put("city1", List.of(firstEvent));
        match.put("city2", List.of(secondEvent));

        // Generate alert
        FraudAlert alert = GeographicAnomalyPattern.generateAlert(match);

        // Verify description contains key information
        String description = alert.getDescription();
        assertTrue(description.contains("user-789"), "Description should contain user ID");
        assertTrue(description.contains("Chicago"), "Description should contain first city");
        assertTrue(description.contains("Miami"), "Description should contain second city");
        assertTrue(description.contains("txn-001"), "Description should contain first transaction ID");
        assertTrue(description.contains("txn-002"), "Description should contain second transaction ID");
        assertTrue(description.contains("200"), "Description should contain first amount");
        assertTrue(description.contains("300"), "Description should contain second amount");
    }
}
