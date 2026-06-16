package io.nop.ai.agent.memory;

import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.List;

/**
 * Fail-fast NoOp {@link IStorageAdapter}. Every method throws
 * {@link NopAiAgentException} so a misconfigured adapter-backed store surfaces
 * the missing persistence backend immediately instead of silently pretending
 * writes/reads succeeded (Minimum Rules #24 — no silent no-op).
 *
 * <p>This is the default used when an integrator opts into the adapter-backed
 * store shape ({@code AdapterBackedMemoryStoreProvider}) but only functionalizes
 * one or two of the three adapters — storage not configured is an explicit
 * error condition, never a silent success.
 */
public class NoOpStorageAdapter implements IStorageAdapter {

    public static NoOpStorageAdapter instance() {
        return new NoOpStorageAdapter();
    }

    private static NopAiAgentException notConfigured(String op) {
        return new NopAiAgentException(
                "IStorageAdapter." + op + " is not configured: no persistence backend is wired. "
                        + "Functionalize IStorageAdapter (e.g. InMemoryStorageAdapter for tests, "
                        + "or a DB-backed adapter successor) to enable memory persistence.");
    }

    @Override
    public void save(AiMemoryItem item) {
        throw notConfigured("save");
    }

    @Override
    public List<AiMemoryItem> loadAll(String typeFilter) {
        throw notConfigured("loadAll");
    }

    @Override
    public AiMemoryItem loadByKey(String key) {
        throw notConfigured("loadByKey");
    }

    @Override
    public void update(String key, AiMemoryItem item) {
        throw notConfigured("update");
    }

    @Override
    public void remove(String key) {
        throw notConfigured("remove");
    }

    @Override
    public void batchSave(List<AiMemoryItem> items) {
        throw notConfigured("batchSave");
    }
}
