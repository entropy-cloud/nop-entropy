package demo;

import java.util.List;

class Bad {
    List<String> load(DemoDao dao) {
        var q = dao.newQuery();
        List<String> rows = dao.findAllByQuery(q);
        return rows;
    }

    List<String> wrongVariable(DemoDao dao) {
        var q = dao.newQuery();
        var other = dao.newQuery();
        other.setLimit(100);
        return dao.selectFieldsByQuery(q);
    }
}
