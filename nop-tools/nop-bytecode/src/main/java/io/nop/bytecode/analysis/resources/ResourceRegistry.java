package io.nop.bytecode.analysis.resources;

import java.util.Set;

/**
 * Registry of tracked resource types (plan 06 v1: two delivered families — Lock deferred to
 * follow-up since it has no close() and needs a separate lock/unlock pairing spec).
 *
 * <p>v1 is name-list based (conservative): acquire sites are {@code NEW} instructions whose
 * exact owner is in {@link #NEW_RESOURCE_TYPES}, plus a small factory-method list
 * ({@link #FACTORY_RETURNS}) keyed by {@code owner.name desc} (JDBC shapes — SpotBugs policy
 * database style). Types outside the list are not tracked (declared FN face; full hierarchy
 * derivation is a follow-up).
 */
public final class ResourceRegistry {

    /** Types acquired via NEW whose exact owner is tracked. */
    private static final Set<String> NEW_RESOURCE_TYPES = Set.of(
            "java/io/BufferedReader", "java/io/BufferedWriter", "java/io/FileReader",
            "java/io/FileWriter", "java/io/FileInputStream", "java/io/FileOutputStream",
            "java/io/InputStreamReader", "java/io/OutputStreamWriter", "java/io/PrintWriter",
            "java/io/PrintStream", "java/io/ObjectInputStream", "java/io/ObjectOutputStream",
            "java/util/Scanner", "java/io/ByteArrayInputStream", "java/io/ByteArrayOutputStream");

    /** Factory methods (owner.name + return type) whose return value is a tracked resource. */
    private static final Set<String> FACTORY_METHODS = Set.of(
            "java/sql/DriverManager.getConnection",
            "java/sql/Connection.createStatement",
            "java/sql/Connection.prepareStatement",
            "java/sql/Statement.executeQuery",
            "java/sql/Statement.executeUpdate",
            "java/sql/Statement.getResultSet");

    /** Whitelist: wrapper constructors taking ownership of the wrapped resource. */
    private static final Set<String> OWNERSHIP_WRAPPERS = Set.of(
            "java/io/BufferedReader", "java/io/BufferedWriter", "java/io/FilterInputStream",
            "java/io/FilterOutputStream", "java/io/FilterReader", "java/io/FilterWriter",
            "java/io/ObjectInputStream", "java/io/ObjectOutputStream", "java/io/PrintWriter",
            "java/io/PrintStream", "java/util/Scanner", "java/io/DataInputStream",
            "java/io/DataOutputStream", "java/io/InputStreamReader", "java/io/OutputStreamWriter");

    public boolean isNewResourceType(String internalOwner) {
        return NEW_RESOURCE_TYPES.contains(internalOwner);
    }

    public boolean isFactoryAcquire(String owner, String name) {
        return FACTORY_METHODS.contains(owner + "." + name);
    }

    public boolean isOwnershipWrapper(String internalOwner) {
        return OWNERSHIP_WRAPPERS.contains(internalOwner);
    }
}
