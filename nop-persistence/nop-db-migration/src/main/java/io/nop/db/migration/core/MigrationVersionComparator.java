/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.commons.util.StringHelper;
import io.nop.db.migration.model.DbMigrationModel;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Comparator for sorting migration versions.
 * 
 * Version formats:
 * - Versioned: V{major}.{minor}.{patch}__{description} (e.g., V1.0.0__create_table)
 * - Repeatable: R__{description} (e.g., R__create_view)
 * 
 * Sorting order:
 * 1. Versioned migrations first, sorted by semantic version ascending
 * 2. Repeatable migrations last, sorted by description
 */
public class MigrationVersionComparator implements Comparator<DbMigrationModel>, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    public static final MigrationVersionComparator INSTANCE = new MigrationVersionComparator();
    
    @Override
    public int compare(DbMigrationModel m1, DbMigrationModel m2) {
        if (m1 == m2) return 0;
        if (m1 == null) return -1;
        if (m2 == null) return 1;
        
        String v1 = m1.getVersion();
        String v2 = m2.getVersion();
        
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;
        
        boolean r1 = isRepeatable(v1);
        boolean r2 = isRepeatable(v2);
        
        if (!r1 && r2) return -1;
        if (r1 && !r2) return 1;
        
        if (r1 && r2) {
            String d1 = extractDescription(v1);
            String d2 = extractDescription(v2);
            if (d1 == null && d2 == null) return 0;
            if (d1 == null) return -1;
            if (d2 == null) return 1;
            return d1.compareTo(d2);
        }
        
        return StringHelper.compareVersions(extractSemanticVersion(v1), extractSemanticVersion(v2));
    }
    
    /**
     * Check if this is a repeatable migration (R__ prefix)
     */
    public static boolean isRepeatable(String version) {
        return version != null && version.startsWith("R__");
    }
    
    /**
     * Extract semantic version from versioned migration name
     * e.g., "V1.0.0__create_table" -> "1.0.0"
     */
    public static String extractSemanticVersion(String version) {
        if (version == null || isRepeatable(version)) {
            return "0";
        }
        
        String v = version;
        if (v.startsWith("V") || v.startsWith("v")) {
            v = v.substring(1);
        }
        
        int sepIndex = v.indexOf("__");
        if (sepIndex > 0) {
            return v.substring(0, sepIndex);
        }
        
        return v;
    }
    
    /**
     * Extract description from migration name
     * e.g., "V1.0.0__create_table" -> "create_table"
     * e.g., "R__create_view" -> "create_view"
     */
    public static String extractDescription(String version) {
        if (version == null) {
            return "";
        }
        
        int sepIndex = version.indexOf("__");
        if (sepIndex > 0) {
            return version.substring(sepIndex + 2);
        }
        
        return "";
    }
    
    /**
     * Parse version string into parts for comparison
     */
    public static int[] parseVersionParts(String version) {
        String semVer = extractSemanticVersion(version);
        String[] parts = semVer.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            if (i < parts.length) {
                try {
                    result[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) {
                    result[i] = 0;
                }
            }
        }
        return result;
    }
}
