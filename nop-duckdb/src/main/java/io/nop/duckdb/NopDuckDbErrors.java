package io.nop.duckdb;

import io.nop.api.core.exceptions.ErrorCode;

/**
 * Error codes for the nop-duckdb analysis execution layer. Descriptions are in English
 * (new module family convention, see docs-for-ai/02-core-guides/error-handling.md).
 */
public interface NopDuckDbErrors {
    String ARG_FILE_PATH = "filePath";
    String ARG_CONFIG_KEY = "configKey";
    String ARG_CONFIG_VALUE = "configValue";
    String ARG_PLATFORM = "platform";
    String ARG_REASON = "reason";
    String ARG_TABLE_NAME = "tableName";

    ErrorCode ERR_DUCKDB_NATIVE_LOAD_FAILED = ErrorCode.define(
            "nop.err.duckdb.native-load-failed",
            "DuckDB native library failed to load on platform {platform}. Bundled natives cover "
                    + "linux_amd64, linux_arm64, osx_universal and windows_amd64 only. Root cause: {reason}",
            ARG_PLATFORM, ARG_REASON);

    ErrorCode ERR_DUCKDB_CONFIG_CONFLICT = ErrorCode.define(
            "nop.err.duckdb.config-conflict",
            "DuckDB file {filePath} is already in use with a different connection configuration. "
                    + "All connections to the same file within one JVM must use an identical configuration. "
                    + "Detail: {reason}",
            ARG_FILE_PATH, ARG_REASON);

    ErrorCode ERR_DUCKDB_CONNECT_FAILED = ErrorCode.define(
            "nop.err.duckdb.connect-failed",
            "Failed to open DuckDB connection for {filePath}. Detail: {reason}",
            ARG_FILE_PATH, ARG_REASON);

    ErrorCode ERR_DUCKDB_INVALID_CONFIG = ErrorCode.define(
            "nop.err.duckdb.invalid-config",
            "Invalid DuckDB engine config {configKey}={configValue}",
            ARG_CONFIG_KEY, ARG_CONFIG_VALUE);

    ErrorCode ERR_DUCKDB_ENGINE_CLOSED = ErrorCode.define(
            "nop.err.duckdb.engine-closed",
            "DuckDB engine is already closed");

    ErrorCode ERR_DUCKDB_FILE_LOCKED = ErrorCode.define(
            "nop.err.duckdb.file-locked",
            "DuckDB file {filePath} is locked by another process (DuckDB allows a single writer "
                    + "per file). Release the lock or use a different file; automatic retry will not "
                    + "succeed while the holder is alive. Detail: {reason}",
            ARG_FILE_PATH, ARG_REASON);

    ErrorCode ERR_DUCKDB_FILE_NOT_FOUND = ErrorCode.define(
            "nop.err.duckdb.file-not-found",
            "Data file does not exist: {filePath}",
            ARG_FILE_PATH);

    ErrorCode ERR_DUCKDB_TABLE_EXISTS = ErrorCode.define(
            "nop.err.duckdb.table-exists",
            "Table {tableName} already exists in the DuckDB database. "
                    + "readCsv/readParquet do not overwrite existing tables; drop it first or use another name.",
            ARG_TABLE_NAME);

    ErrorCode ERR_DUCKDB_INVALID_STEP_INPUT = ErrorCode.define(
            "nop.err.duckdb.invalid-step-input",
            "Invalid DuckDB task step input: {reason}",
            ARG_REASON);

    ErrorCode ERR_DUCKDB_IO_FAILED = ErrorCode.define(
            "nop.err.duckdb.io-failed",
            "DuckDB file IO failed for {filePath}. Detail: {reason}",
            ARG_FILE_PATH, ARG_REASON);
}
