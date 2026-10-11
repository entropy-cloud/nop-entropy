package io.nop.bytecode.analysis.resources;

/**
 * One resource-leak finding: a tracked resource with residual open obligation on a method-exit
 * path. {@code ref} encodes the acquire point ({@code Type@insnN}) so two leaks on the same
 * return path stay distinct under the CLI dedup key.
 */
public record UnclosedResourceFinding(String className, String methodName, int insnIndex, String ref) {
}
