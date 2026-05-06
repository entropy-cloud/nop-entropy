package io.nop.code.core.incremental;

class TestInMemoryFingerprintStore extends TestIFingerprintStore {

    @Override
    IFingerprintStore createStore() {
        return new InMemoryFingerprintStore();
    }
}
