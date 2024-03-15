package demo.orm.test;

import demo.orm.entity.Course;
import demo.orm.entity.Department;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

/**
 * 由于基类 JunitBaseTestCase 的 init 方法标注了 @BeforeEach，
 * 因此在运行任何一个 Test 之前，会运行 JunitBaseTestCase::init。
 * init 方法中调用 IBeanContainer::restart() 重启 IOC 容器，从而重新创建数据库。
 * 也就是说，每个 Test 都会使用全新的数据库。
 */
public class TestEntityDao extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private void initDepartments() {
        IEntityDao<Department> dao = daoProvider.daoFor(Department.class);
        saveDepartment("Biology", "Watson", new BigDecimal("90000"));
        saveDepartment("Comp. Sci.", "Taylor", new BigDecimal("100000"));
        saveDepartment("Elec. Eng.", "Taylor", new BigDecimal("85000"));
        saveDepartment("Finance", "Painter", new BigDecimal("120000"));
        saveDepartment("History", "Painter", new BigDecimal("50000"));
        saveDepartment("Music", "Packard", new BigDecimal("80000"));
        saveDepartment("Physics", "Watson", new BigDecimal("70000"));
    }

    private Department saveDepartment(String deptName,
                                      String building,
                                      BigDecimal budget) {
        IEntityDao<Department> dao = daoProvider.daoFor(Department.class);
        Department department = dao.newEntity();
        department.setDeptName(deptName);
        department.setBuilding(building);
        department.setBudget(budget);
        dao.saveEntity(department);
        return department;
    }

    @Test
    public void testSaveEntity() {
        // 每一个 Department 都开启新的 ORM session
        // 因此 Department 数据会即时写入数据库
        initDepartments();
        IEntityDao<Department> dao = daoProvider.daoFor(Department.class);
        Assertions.assertEquals(7, dao.findAll().size());
    }

    @Test
    public void testRunInSession() {
        ormTemplate.runInSession(session -> {
            initDepartments();
            IEntityDao<Department> dao = daoProvider.daoFor(Department.class);
            // 此时 session 未刷新入库，因此查询结果为空
            Assertions.assertEquals(0, dao.findAll().size());
            // 必须要提前手动刷新 session，将 session 缓存写入数据库
            // 后续的查询才能获取新增的数据
            session.flush();
            Assertions.assertEquals(7, dao.findAll().size());
            return null;
        });
    }

    private Course saveCourse(String courseId,
                              String title,
                              String deptName,
                              BigDecimal credits) {
        IEntityDao<Course> dao = daoProvider.daoFor(Course.class);
        Course course = dao.newEntity();
        course.setCourseId(courseId);
        course.setTitle(title);
        course.setDeptName(deptName);
        course.setCredits(credits);
        dao.saveEntity(course);
        return course;
    }

    @Test
    public void testSaveRelation() {
        ormTemplate.runInSession(session -> {
            Department department = saveDepartment(
                    "dept-001", "Watson", new BigDecimal("90000"));
            Course course = saveCourse(
                    "c-001", "课程001", "dept-001", new BigDecimal(1));
            // 调用 course.setDeptName() 之后
            // nop-orm 自动增加 Department 关联
            Assertions.assertEquals("Watson", course.getDepartment().getBuilding());
            session.flush();
            IEntityDao<Course> dao = daoProvider.daoFor(Course.class);
            Course example = new Course();
            example.setTitle("课程001");
            Course courseFromDB = dao.findFirstByExample(example);
            // 从数据库加载实体之后，如果 session 已经存在相同 id 的实体，
            // 那么实际上得到的是 session 中的实体
            Assertions.assertSame(course, courseFromDB);
            return null;
        });
    }

}
