package demo.orm.test.database.system.concepts.exercises.mapper;

import io.nop.api.core.annotations.orm.SqlLibMapper;

import java.util.List;
import java.util.Map;

@SqlLibMapper("/database/system/concepts/sql/chapter4.sql-lib.xml")
public interface Chapter4Mapper {

    List<Map<String, Object>> exercise_2a();

    List<Map<String, Object>> exercise_2b();

    List<Map<String, Object>> exercise_2d();

}
