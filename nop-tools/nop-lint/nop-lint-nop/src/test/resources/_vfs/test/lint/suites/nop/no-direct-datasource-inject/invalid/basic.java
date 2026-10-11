package demo;

class Bad {
    @Inject
    DataSource dataSource;

    void run(@Inject DataSource paramSource) {
        dataSource.getConnection();
    }
}
