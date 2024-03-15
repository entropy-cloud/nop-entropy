package demo.orm.test.database.system.concepts.exercises;

import demo.orm.entity.*;
import demo.orm.test.database.system.concepts.exercises.mapper.Chapter3Mapper;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.api.core.beans.FilterBeans.eq;

public class TestChapter3 extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    Chapter3Mapper mapper;

    @BeforeEach
    public void initData() {
        new UniversityData(ormTemplate, daoProvider).init();
    }

    /**
     * 找出在 "Fall 2017" 或者 "Spring 2018" 开有 section 的课程。
     * 使用 EQL 的集合操作。
     */
    @Test
    public void test_slide_001() {
        List<Course> courses = mapper.slide_001();
        courses.sort((c1, c2) -> c1.getCourseId().compareTo(c2.getCourseId()));
        Assertions.assertEquals(8, courses.size());
        Assertions.assertEquals("PHY-101", courses.get(7).getCourseId());
    }

    /**
     * 找出同时在 "Fall 2017" 和 "Spring 2018" 开有 section 的课程。
     */
    @Test
    public void test_slide_002() {
        List<Course> courses = mapper.slide_002();
        Assertions.assertEquals(1, courses.size());
        Assertions.assertEquals("CS-101", courses.get(0).getCourseId());
    }

    /**
     * 找出 "Fall 2017" 开有 section，但是 "Spring 2018" 没有 section 的课程。
     */
    @Test
    public void test_slide_003() {
        List<Course> courses = mapper.slide_003();
        courses.sort((c1, c2) -> c1.getCourseId().compareTo(c2.getCourseId()));
        Assertions.assertEquals(2, courses.size());
        Assertions.assertEquals("CS-347", courses.get(0).getCourseId());
        Assertions.assertEquals("PHY-101", courses.get(1).getCourseId());
    }

    /**
     * 找出 salary 为 null 的教师。
     * 演示代码中直接执行 EQL。
     */
    @Test
    public void test_slide_004() {
        SQL eql = SQL.begin().sql("select o from Instructor o where o.salary is null").end();
        List<Instructor> list = ormTemplate.findAll(eql);
        Assertions.assertEquals(0, list.size());
    }

    /**
     * 获取计算机系教师的平均薪资。
     */
    @Test
    public void test_slide_005() {
        BigDecimal avg = mapper.slide_005("Comp. Sci.");
        Assertions.assertEquals(
                new BigDecimal("77333.33").setScale(2, RoundingMode.HALF_UP),
                avg.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 找出有在 Spring 2018 的课程 section 授课的教师数量。
     * 注意不要重复计算。
     */
    @Test
    public void test_slide_006() {
        BigDecimal count = mapper.slide_006(BigDecimal.valueOf(2018), "Spring");
        Assertions.assertEquals(6, count.intValue());
    }

    /**
     * 找出教师平均薪资大于 42000 的系名和平均薪资。
     */
    @Test
    public void test_slide_007() {
        List<Map<String, Object>> list = mapper.slide_007();
        Assertions.assertEquals(6, list.size());

        BigDecimal avgSalary = (BigDecimal) list.stream().filter(m -> {
            Department d = (Department) m.get("department");
            return d.getDeptName().equals("Comp. Sci.");
        }).findFirst().get().get("avgSalary");
        Assertions.assertEquals(
                new BigDecimal("77333.33").setScale(2, RoundingMode.HALF_UP),
                avgSalary.setScale(2, RoundingMode.HALF_UP)
        );
    }

    /**
     * 找出符合条件的教师，只要这些教师的薪资大于 Biology 系的任意一个教师的薪资，则满足条件。
     */
    @Test
    public void test_slide_008() {
        IEntityDao<Instructor> dao = daoProvider.daoFor(Instructor.class);
        ormTemplate.runInSession(() -> {
            List<Instructor> instructors = mapper.slide_008();
            // 测试批量加载
            // 因为已经在 sql-lib.xml 中写了等价的 batchLoadSelection
            // Java 中不需要再写
//            dao.batchLoadProps(instructors, List.of("teachings.section", "department"));
            Assertions.assertEquals(7, instructors.size());
            BigDecimal min = BigDecimal.valueOf(72000);
            instructors.forEach(instructor -> {
                Assertions.assertTrue(instructor.getSalary().compareTo(min) > 0);
                // 因为前面已经批量加载，下面访问关联实体应该不会发出 SQL 查询
                // 可以查看日志确认
                instructor.getRelatedSectionList().forEach(
                        section -> Assertions.assertFalse(section.getBuilding().isBlank()));
                Assertions.assertFalse(instructor.getDepartment().getBuilding().isBlank());
            });
        });
    }

    /**
     * 找出符合条件的教师，这些教师的薪资要大于 Biology 系的全部教师的薪资。
     */
    @Test
    public void test_slide_009() {
        List<Instructor> instructors = mapper.slide_009();
        Assertions.assertEquals(7, instructors.size());
        BigDecimal max = BigDecimal.valueOf(72000);
        instructors.forEach(instructor -> {
            Assertions.assertTrue(instructor.getSalary().compareTo(max) > 0);
        });
    }

    /**
     * 找出参加了 Biology 系所有课程的学生。
     * 问题略为复杂，可以画出韦恩图，比较容易理解。
     */
    @Test
    public void test_slide_010() {
        List<Student> students = mapper.slide_010();
        Assertions.assertEquals(0, students.size());
    }

    /**
     * 找出平均教师薪资高于 42000 的部门及对应的平均薪资。
     */
    @Test
    public void test_slide_011() {
        List<Map<String, Object>> list = mapper.slide_011();
        Assertions.assertEquals(6, list.size());
    }

    /**
     * 找出具有最大 budget 的系。
     * 请使用 with 子句，使得查询语句更易读。
     */
    @Test
    public void test_slide_012() {
        List<Department> list = mapper.slide_012();
        Assertions.assertEquals(1, list.size());
        Assertions.assertEquals("Finance", list.get(0).getDeptName());
    }

    /**
     * 定义院系总薪资为某个院系的所有教师的薪资之和。
     * 找出总薪资 > 算术平均值(院系总薪资) 的所有院系。
     * 请使用 with 子句，使得查询语句更易读。
     */
    @Test
    public void test_slide_013() {
        List<Department> list = mapper.slide_013();
        Assertions.assertEquals(3, list.size());
    }

    /**
     * 列出所有院系，以及院系中的教师数量。
     */
    @Test
    public void test_slide_014() {
        List<Map<String, Object>> list = mapper.slide_014();
        Assertions.assertEquals(7, list.size());
        Department d1 = (Department) list.get(1).get("department");
        Long c1 = (Long) list.get(1).get("instructorCount");
        Department d2 = (Department) list.get(6).get("department");
        Long c2 = (Long) list.get(6).get("instructorCount");
        Assertions.assertEquals("Comp. Sci.", d1.getDeptName());
        Assertions.assertEquals("Taylor", d1.getBuilding());
        Assertions.assertEquals(3, c1);
        Assertions.assertEquals("Physics", d2.getDeptName());
        Assertions.assertEquals("Watson", d2.getBuilding());
        Assertions.assertEquals(2, c2);
    }

    /**
     * 删除符合条件的教师。这些教师所在的系位于 Watson 建筑楼。
     */
    @Test
    public void test_slide_015() {
        Long deleted = mapper.slide_015("Watson");
        Assertions.assertEquals(3, deleted);
    }

    /**
     * 找出所属系为 "Comp. Sci." 并且学分等于 3 的课程。
     */
    @Test
    public void test_exercise_1a() {
        IEntityDao<Course> dao = daoProvider.daoFor(Course.class);
        QueryBean query = new QueryBean();
        // 多次 addFilter 以 AND 方式组合起来
        query.addFilter(eq(Course.PROP_NAME_deptName, "Comp. Sci."))
                .addFilter(eq(Course.PROP_NAME_credits, BigDecimal.valueOf(3)));
        List<Course> list = dao.findAllByQuery(query);
        Assertions.assertEquals(3, list.size());
        for (var c : list) {
            Assertions.assertEquals("Comp. Sci.", c.getDeptName());
            Assertions.assertEquals(BigDecimal.valueOf(3), c.getCredits());
        }
    }

    /**
     * 找出 "Einstein" 教师的所有学生。
     */
    @Test
    public void test_exercise_1b() {
        List<String> ids = mapper.exercise_1b("Einstein");
        Assertions.assertEquals(1, ids.size());
        Assertions.assertEquals("44553", ids.get(0));
    }

    /**
     * 找出教师的最大薪资。
     */
    @Test
    public void test_exercise_1c() {
        BigDecimal maxSalary = mapper.exercise_1c();
        BigDecimal expected = new BigDecimal("95000").setScale(2);
        Assertions.assertEquals(expected, maxSalary);
    }

    /**
     * 找出领取最大薪资的所有教师。
     */
    @Test
    public void test_exercise_1d() {
        List<Instructor> instructors = mapper.exercise_1d();
        Assertions.assertEquals(1, instructors.size());
        Assertions.assertEquals("Einstein", instructors.get(0).getName());
    }

    /**
     * 找出 2017 年秋季的每一个课程 section 的参加学生人数。
     * 需要同时获取课程 section 的信息。
     */
    @Test
    public void test_exercise_1e() {
        List<Map<String, Object>> list = mapper.exercise_1e();
        Assertions.assertEquals(3, list.size());
        // 按课程名称排序
        list.sort((row1, row2) -> {
            Section s1 = (Section) row1.get("section");
            Section s2 = (Section) row2.get("section");
            return s1.getCourseId().compareTo(s2.getCourseId());
        });

        Long c1 = (Long) list.get(0).get("studentCount");
        Section s1 = (Section) list.get(0).get("section");
        Assertions.assertEquals("CS-101", s1.getCourseId());
        Assertions.assertEquals(6, c1);

        Long c2 = (Long) list.get(1).get("studentCount");
        Section s2 = (Section) list.get(1).get("section");
        Assertions.assertEquals("CS-347", s2.getCourseId());
        Assertions.assertEquals(2, c2);

        Long c3 = (Long) list.get(2).get("studentCount");
        Section s3 = (Section) list.get(2).get("section");
        Assertions.assertEquals("PHY-101", s3.getCourseId());
        Assertions.assertEquals(1, c3);
    }

    /**
     * 找出 2017 年秋季的所有课程 section 的最大参加人数。
     */
    @Test
    public void test_exercise_1f() {
        Long maxCount = mapper.exercise_1f();
        Assertions.assertEquals(6, maxCount);
    }

    /**
     * 找出 2017 年秋季，参加学生人数最多的课程 section，以及参加人数。
     * 可能存在多个课程 section。
     */
    @Test
    public void test_exercise_1g() {
        List<Map<String, Object>> list = mapper.exercise_1g();
        Assertions.assertEquals(1, list.size());
        Section section = (Section) list.get(0).get("section");
        Long count = (Long) list.get(0).get("studentCount");
        Assertions.assertEquals("CS-101", section.getCourseId());
        Assertions.assertEquals(6, count);
    }

    /**
     * "Comp. Sci." 系的每一位教师薪资增加 10%。
     */
    @Test
    public void test_exercise_3a() {
        IEntityDao<Instructor> dao = daoProvider.daoFor(Instructor.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(Instructor.PROP_NAME_deptName, "Comp. Sci."));

        // 更新前教师列表，按 ID 排序
        List<Instructor> oldList = dao.findAllByQuery(query);
        oldList.sort((i1, i2) -> i1.getId().compareTo(i2.getId()));

        // 更新数据库
        Long updatedNumber = mapper.exercise_3a("Comp. Sci.", 0.1);
        Assertions.assertEquals(3, updatedNumber);

        // 更新后教师列表，按 ID 排序
        List<Instructor> newList = dao.findAllByQuery(query);
        newList.sort((i1, i2) -> i1.getId().compareTo(i2.getId()));

        Assertions.assertEquals(3, oldList.size());
        Assertions.assertEquals(oldList.size(), newList.size());
        for (var i = 0; i < 3; i++) {
            Instructor insOld = oldList.get(i);
            Instructor insNew = newList.get(i);
            Assertions.assertEquals(insOld.getId(), insNew.getId());
            Assertions.assertEquals(
                    insOld.getSalary().multiply(BigDecimal.valueOf(1.1)).setScale(2),
                    insNew.getSalary());
        }
    }

    /**
     * 删除没有提供 section 的课程，也就是没有在 section 表中出现的课程。
     */
    @Test
    public void test_exercise_3b() {
        Long deletedNumber = mapper.exercise_3b();
        Assertions.assertEquals(1, deletedNumber);
        IEntityDao<Course> dao = daoProvider.daoFor(Course.class);
        List<Course> list = dao.findAll();
        Assertions.assertEquals(12, list.size());
        long filter = list.stream().filter(course -> course.getCourseId().equals("BIO-399")).count();
        Assertions.assertEquals(0, filter);
    }

    /**
     * 对于每一个 tot_cred > 100 的学生，新增同名教师。
     * 新增教师所属系与原学生相同，薪资是 10000。
     */
    @Test
    public void test_exercise_3c() {
        Long insertedNumber = mapper.exercise_3c();
        Assertions.assertEquals(3, insertedNumber);

        IEntityDao<Instructor> dao = daoProvider.daoFor(Instructor.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(Instructor.PROP_NAME_salary, BigDecimal.valueOf(10000)));
        List<Instructor> list = dao.findAllByQuery(query);
        Assertions.assertEquals(3, list.size());

        Set<String> set = new HashSet<>() {{
            add("Zhang");
            add("Chavez");
            add("Tanaka");
        }};
        Set<String> nameSet = list.stream().map(Instructor::getName)
                .collect(Collectors.toSet());
        Assertions.assertEquals(set, nameSet);
    }

    /**
     * 忽略大小写，查找名为 "Comp. Sci." 的系。
     * 测试 lower 函数的使用。
     */
    @Test
    public void test_exercise_6() {
        List<Department> departments = mapper.exercise_6("%sci%");
        Assertions.assertEquals(1, departments.size());
        Assertions.assertEquals("Comp. Sci.", departments.get(0).getDeptName());
    }
}
