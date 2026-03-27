/**
 * Copyright (c) 2017-2024 nop Commerce (c) 2025
 */
package io.nop.db.migration;

/**
 * Foreign key action enum
 */
public enum ForeignKeyAction {
    CASCADE,
    SET_NULL,
    NO_ACTION,
    RESTRICT
}
