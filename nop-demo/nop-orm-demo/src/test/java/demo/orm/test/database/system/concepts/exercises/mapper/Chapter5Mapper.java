package demo.orm.test.database.system.concepts.exercises.mapper;

import demo.orm.entity.Course;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.orm.SqlLibMapper;

import java.util.List;

@SqlLibMapper("/database/system/concepts/sql/chapter5.sql-lib.xml")
public interface Chapter5Mapper {

    List<Course> slide_01(@Name("title") String title);

}
