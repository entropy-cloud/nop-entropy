package demo;

interface IRepoBiz<T> extends ICrudBiz<T> {

    T require(String id, IServiceContext ctx);

    @BizQuery
    java.util.List<T> list(IServiceContext ctx);

    void deleteAll(IServiceContext ctx);

}
