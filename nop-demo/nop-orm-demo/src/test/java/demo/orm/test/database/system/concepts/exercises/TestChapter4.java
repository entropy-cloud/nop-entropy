package demo.orm.test.database.system.concepts.exercises;

import demo.orm.entity.Department;
import demo.orm.entity.Instructor;
import demo.orm.entity.Section;
import demo.orm.test.database.system.concepts.exercises.mapper.Chapter4Mapper;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class TestChapter4 extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    Chapter4Mapper mapper;

    @BeforeEach
    public void initData() {
        new UniversityData(ormTemplate, daoProvider).init();
    }

    /**
     * 获取每一位教师的信息，以及所授课程 section 的数量。
     * 确保即使没有任何授课的教师信息也能获取到。
     * 以外连接的方式编写 EQL。
     */
    @Test
    public void test_exercise_2a() {
        IEntityDao<Instructor> dao = daoProvider.daoFor(Instructor.class);
        List<Instructor> all = dao.findAll();
        List<Map<String, Object>> list = mapper.exercise_2a();
        Assertions.assertEquals(all.size(), list.size());
        list.forEach(System.out::println);
        list.subList(9, 12).forEach(e -> {
            Long c = (Long) e.get("teachingCount");
            Assertions.assertEquals(0, c);
        });
        Assertions.assertEquals(3, (Long) list.get(0).get("teachingCount"));
        Assertions.assertEquals(3, (Long) list.get(1).get("teachingCount"));
    }

    /**
     * 同 test_exercise_1a。
     * 获取每一位教师的信息，以及所授课程 section 的数量。
     * 确保即使没有任何授课的教师信息也能获取到。
     * 以子查询的方式编写 EQL。
     */
    @Test
    public void test_exercise_2b() {
        IEntityDao<Instructor> dao = daoProvider.daoFor(Instructor.class);
        List<Instructor> all = dao.findAll();
        List<Map<String, Object>> list = mapper.exercise_2b();
        Assertions.assertEquals(all.size(), list.size());
        list.subList(9, 12).forEach(e -> {
            Long c = (Long) e.get("teachingCount");
            Assertions.assertEquals(0, c);
        });
        Assertions.assertEquals(3, (Long) list.get(0).get("teachingCount"));
        Assertions.assertEquals(3, (Long) list.get(1).get("teachingCount"));
    }

    /**
     * 获取 2028 年春季的所有课程 section，以及相关的授课教师。
     */
    @Test
    public void test_exercise_2c() {
        // 需要在同一个 session 中做相关查询
        ormTemplate.runInSession(() -> {
            IEntityDao<Section> dao = daoProvider.daoFor(Section.class);
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq(Section.PROP_NAME_year, BigDecimal.valueOf(2018)))
                    .addFilter(FilterBeans.eq(Section.PROP_NAME_semester, "Spring"));
            List<Section> sections = dao.findAllByQuery(query);
            Assertions.assertEquals(7, sections.size());

            // 批量加载，避免 N + 1 问题
            dao.batchLoadProps(sections, List.of("teachings.instructor"));

            Section section = sections.stream()
                    .filter(s -> s.getCourseId().equals("CS-319"))
                    .findFirst()
                    .get();
            List<Instructor> instructors = section.getRelatedInstructorList();
            Assertions.assertEquals(1, instructors.size());
            Assertions.assertEquals("Katz", instructors.get(0).getName());
        });
    }

    /**
     * 获取所有的系，以及对应的教师数量。
     */
    @Test
    public void test_exercise_2d() {
        List<Map<String, Object>> list = mapper.exercise_2d();

        Map<String, Object> m1 = list.stream()
                .filter(m -> ((Department) m.get("department")).getDeptName().equals("Comp. Sci."))
                .findFirst()
                .get();
        Assertions.assertEquals(3, (Long) m1.get("instructorCount"));

        Map<String, Object> m2 = list.stream()
                .filter(m -> ((Department) m.get("department")).getDeptName().equals("Biology"))
                .findFirst()
                .get();
        Assertions.assertEquals(1, (Long) m2.get("instructorCount"));
    }

}
