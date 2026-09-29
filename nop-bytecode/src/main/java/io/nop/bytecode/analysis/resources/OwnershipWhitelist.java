package io.nop.bytecode.analysis.resources;

import java.util.Set;

/**
 * Ownership-transfer whitelist (plan 06): wrapper constructors that take ownership of the
 * resource passed as argument — a tracked reference fed to one of these constructors is
 * discharged everywhere (the wrapper closes it).
 *
 * <p>Name-list based (conservative); extend via this set as wrappers are encountered.
 */
public final class OwnershipWhitelist {

    private static final Set<String> WRAPPER_CONSTRUCTORS = Set.of(
            "java/io/BufferedReader", "java/io/BufferedWriter", "java/io/FilterInputStream",
            "java/io/FilterOutputStream", "java/io/FilterReader", "java/io/FilterWriter",
            "java/io/ObjectInputStream", "java/io/ObjectOutputStream", "java/io/PrintWriter",
            "java/io/PrintStream", "java/util/Scanner", "java/io/DataInputStream",
            "java/io/DataOutputStream", "java/io/InputStreamReader", "java/io/OutputStreamWriter");

    /** Whether the constructor owner takes ownership of its tracked resource argument. */
    public boolean isOwnershipWrapper(String internalOwner) {
        return WRAPPER_CONSTRUCTORS.contains(internalOwner);
    }
}
