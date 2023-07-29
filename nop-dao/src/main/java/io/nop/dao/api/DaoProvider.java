package io.nop.dao.api;

public class DaoProvider {
    private static IDaoProvider _instance;

    public static IDaoProvider instance() {
        return _instance;
    }

    public static void registerInstance(IDaoProvider daoProvider) {
        _instance = daoProvider;
    }
}
