package demo.orm.test.database.system.concepts.exercises.mapper;

import demo.orm.entity.Course;
import demo.orm.entity.Department;
import demo.orm.entity.Instructor;
import demo.orm.entity.Student;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.orm.SqlLibMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@SqlLibMapper("/database/system/concepts/sql/chapter3.sql-lib.xml")
public interface Chapter3Mapper {

    List<String> exercise_1b(@Name("name") String name);

    BigDecimal exercise_1c();

    List<Instructor> exercise_1d();

    List<Map<String, Object>> exercise_1e();

    Long exercise_1f();

    List<Map<String, Object>> exercise_1g();


    Long exercise_3a(@Name("deptName") String deptName, @Name("percent") double percent);

    Long exercise_3b();

    Long exercise_3c();

    List<Department> exercise_6(@Name("deptName") String deptName);

    List<Course> slide_001();

    List<Course> slide_002();

    List<Course> slide_003();

    BigDecimal slide_005(@Name("deptName") String deptName);

    BigDecimal slide_006(@Name("year") BigDecimal year, @Name("semester") String semester);

    List<Map<String, Object>> slide_007();

    List<Instructor> slide_008();

    List<Instructor> slide_009();

    List<Student> slide_010();

    List<Map<String, Object>> slide_011();

    List<Department> slide_012();

    List<Department> slide_013();

    List<Map<String, Object>> slide_014();

    Long slide_015(@Name("building") String building);

}
