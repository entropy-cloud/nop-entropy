/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

/**
 * AR-1 (P0): mapping from a user-facing job name to the storage-level jobId.
 *
 * <p>{@code LocalFileCheckpointStorage.validateId} enforces {@code [a-zA-Z0-9_-]+}, while
 * {@code env.execute()} defaults to {@code "Streaming Job"} (contains a space) and DSL/Java
 * job names may contain CJK characters, dots or other unsafe characters. This utility maps
 * any job name to a storage-safe id:
 *
 * <ul>
 *   <li>every character outside {@code [a-zA-Z0-9_-]} is replaced with {@code '_'}</li>
 *   <li>when any replacement happened, a 6-hex stable hash of the ORIGINAL name is
 *       appended ({@code {sanitized}-{hash6}}) so the mapping is injective — two distinct
 *       CJK names that sanitize to the same underscore pattern cannot collide into one
 *       storage namespace (which would re-create the AR-1 cross-job pollution)</li>
 *   <li>the mapping is deterministic (pure function of the name), so the same job name
 *       maps to the same storage namespace across runs — the legal "same jobId, same
 *       topology, cross-run auto-recovery" semantics is preserved</li>
 * </ul>
 */
public final class StorageJobIds {

    private StorageJobIds() {
    }

    /**
     * Maps a job name to a storage-safe jobId. Returns {@code null} only for
     * {@code null}/blank input (caller decides the fallback).
     */
    public static String sanitizeJobId(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(jobName.length() + 8);
        boolean replaced = false;
        for (int i = 0; i < jobName.length(); i++) {
            char c = jobName.charAt(i);
            if (isSafe(c)) {
                sb.append(c);
            } else {
                sb.append('_');
                replaced = true;
            }
        }
        if (sb.isEmpty()) {
            return null;
        }
        if (replaced) {
            sb.append('-').append(String.format("%06x", stableHash(jobName) & 0xFFFFFFL));
        }
        return sb.toString();
    }

    private static boolean isSafe(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '_' || c == '-';
    }

    private static int stableHash(String s) {
        int h = 1128115;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return h;
    }
}
