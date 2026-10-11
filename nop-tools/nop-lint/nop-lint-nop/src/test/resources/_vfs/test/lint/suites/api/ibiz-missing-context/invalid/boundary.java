package demo;

interface IRepoBiz<T> extends ICrudBiz<T> {

    void loadAll(String pattern, String... parts);

    T get(String id, IServiceContext ctx, String extra);

}
