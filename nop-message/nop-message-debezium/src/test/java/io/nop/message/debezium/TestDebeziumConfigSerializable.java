/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class TestDebeziumConfigSerializable {

    @Test
    void testDebeziumConfigSerializable() throws Exception {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("test-cdc");
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");
        config.setDatabasePort(3306);
        config.setDatabaseUser("repl");
        config.setDatabasePassword("secret");
        config.setDatabaseName("inventory");
        config.setDatabaseServerId(12345L);
        config.setServerName("dbserver1");
        config.setTableIncludeList("inventory\\.products");
        config.setOffsetStoragePath("/tmp/offsets.dat");
        config.setOffsetFlushInterval(Duration.ofSeconds(30));
        config.setSchemaHistoryPath("/tmp/schema-history.dat");
        config.setSnapshotMode("schema_only");
        config.setHeartbeatInterval(Duration.ofSeconds(10));
        config.setIncludeSchemaChanges(true);
        config.setIncludeDdl(true);
        config.addExtraProperty("custom.key", "custom.value");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(config);
        oos.close();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        DebeziumConfig restored = (DebeziumConfig) ois.readObject();

        assertNotNull(restored);
        assertEquals("test-cdc", restored.getName());
        assertEquals("mysql", restored.getConnectorType());
        assertEquals("localhost", restored.getDatabaseHost());
        assertEquals(3306, restored.getDatabasePort());
        assertEquals("repl", restored.getDatabaseUser());
        assertEquals("secret", restored.getDatabasePassword());
        assertEquals("inventory", restored.getDatabaseName());
        assertEquals(12345L, restored.getDatabaseServerId());
        assertEquals("dbserver1", restored.getServerName());
        assertEquals("inventory\\.products", restored.getTableIncludeList());
        assertEquals("/tmp/offsets.dat", restored.getOffsetStoragePath());
        assertEquals(Duration.ofSeconds(30), restored.getOffsetFlushInterval());
        assertEquals("/tmp/schema-history.dat", restored.getSchemaHistoryPath());
        assertEquals("schema_only", restored.getSnapshotMode());
        assertEquals(Duration.ofSeconds(10), restored.getHeartbeatInterval());
        assertTrue(restored.isIncludeSchemaChanges());
        assertTrue(restored.isIncludeDdl());
        assertEquals("custom.value", restored.getExtraProperties().get("custom.key"));
    }
}
