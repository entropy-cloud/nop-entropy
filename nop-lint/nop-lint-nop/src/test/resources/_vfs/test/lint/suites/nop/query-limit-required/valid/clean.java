package demo;

import java.util.List;

class Clean {
    List<String> load(DemoDao dao) {
        var q = dao.newQuery();
        q.setLimit(100);
        List<String> rows = dao.findAllByQuery(q);
        return rows;
    }

    List<String> cappedByMaxResults(DemoDao dao) {
        var q2 = dao.newQuery();
        q2.setMaxResults(50);
        return dao.selectFieldsByQuery(q2);
    }

    void bareStatement(DemoDao dao) {
        var q3 = dao.newQuery();
        q3.setLimit(10);
        dao.findAllByQuery(q3);
    }
}
