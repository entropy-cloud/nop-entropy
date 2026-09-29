/*
 * nop-bytecode: bytecode analysis substrate library.
 * Parallel to the source-only nop-lint engine; runs on compiled artifacts.
 */

/**
 * Module root package. Layer layout (substrate ADR, Wave 0):
 *
 * <ul>
 *   <li>{@code io.nop.bytecode.collect} - collection layer: enumerate class artifacts from
 *       Maven reactor output directories and jars, with manifest-based incremental collection.</li>
 * </ul>
 *
 * <p>Adjudicated constraints for this module: zero {@code nop-*} runtime dependencies
 * (plain JDK + ASM only), loud failure on missing or unparseable inputs, no codegen pipeline.
 */
package io.nop.bytecode;
