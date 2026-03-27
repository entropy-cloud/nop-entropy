/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.db.migration.model.DbMigrationModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestMigrationVersionComparator {
    
    @Test
    public void testVersionedMigrationSorting() {
        List<DbMigrationModel> migrations = new ArrayList<>();
        
        migrations.add(createMigration("V1.1.0__add_column"));
        migrations.add(createMigration("V1.0.0__create_table"));
        migrations.add(createMigration("V2.0.0__major_change"));
        migrations.add(createMigration("V1.0.1__fix_index"));
        
        Collections.sort(migrations, MigrationVersionComparator.INSTANCE);
        
        assertEquals("V1.0.0__create_table", migrations.get(0).getVersion());
        assertEquals("V1.0.1__fix_index", migrations.get(1).getVersion());
        assertEquals("V1.1.0__add_column", migrations.get(2).getVersion());
        assertEquals("V2.0.0__major_change", migrations.get(3).getVersion());
    }
    
    @Test
    public void testRepeatableMigrationsSortAfterVersioned() {
        List<DbMigrationModel> migrations = new ArrayList<>();
        
        migrations.add(createMigration("R__create_view"));
        migrations.add(createMigration("V1.0.0__create_table"));
        migrations.add(createMigration("R__update_stats"));
        
        Collections.sort(migrations, MigrationVersionComparator.INSTANCE);
        
        assertEquals("V1.0.0__create_table", migrations.get(0).getVersion());
        assertTrue(MigrationVersionComparator.isRepeatable(migrations.get(1).getVersion()));
        assertTrue(MigrationVersionComparator.isRepeatable(migrations.get(2).getVersion()));
    }
    
    @Test
    public void testRepeatableMigrationsSortByDescription() {
        List<DbMigrationModel> migrations = new ArrayList<>();
        
        migrations.add(createMigration("R__update_stats"));
        migrations.add(createMigration("R__create_view"));
        migrations.add(createMigration("R__alter_index"));
        
        Collections.sort(migrations, MigrationVersionComparator.INSTANCE);
        
        assertEquals("R__alter_index", migrations.get(0).getVersion());
        assertEquals("R__create_view", migrations.get(1).getVersion());
        assertEquals("R__update_stats", migrations.get(2).getVersion());
    }
    
    @Test
    public void testIsRepeatable() {
        assertTrue(MigrationVersionComparator.isRepeatable("R__create_view"));
        assertFalse(MigrationVersionComparator.isRepeatable("V1.0.0__create_table"));
        assertFalse(MigrationVersionComparator.isRepeatable(null));
    }
    
    @Test
    public void testExtractSemanticVersion() {
        assertEquals("1.0.0", MigrationVersionComparator.extractSemanticVersion("V1.0.0__create_table"));
        assertEquals("2.1.3", MigrationVersionComparator.extractSemanticVersion("V2.1.3__add_index"));
        assertEquals("0", MigrationVersionComparator.extractSemanticVersion("R__create_view"));
        assertEquals("1.0", MigrationVersionComparator.extractSemanticVersion("V1.0__simple"));
    }
    
    @Test
    public void testExtractDescription() {
        assertEquals("create_table", MigrationVersionComparator.extractDescription("V1.0.0__create_table"));
        assertEquals("create_view", MigrationVersionComparator.extractDescription("R__create_view"));
        assertEquals("", MigrationVersionComparator.extractDescription("V1.0.0"));
    }
    
    @Test
    public void testParseVersionParts() {
        int[] parts = MigrationVersionComparator.parseVersionParts("V1.2.3__test");
        assertArrayEquals(new int[]{1, 2, 3}, parts);
        
        parts = MigrationVersionComparator.parseVersionParts("V2.0__test");
        assertArrayEquals(new int[]{2, 0, 0}, parts);
        
        parts = MigrationVersionComparator.parseVersionParts("V1__test");
        assertArrayEquals(new int[]{1, 0, 0}, parts);
    }
    
    private DbMigrationModel createMigration(String version) {
        DbMigrationModel model = new DbMigrationModel();
        model.setVersion(version);
        return model;
    }
}
