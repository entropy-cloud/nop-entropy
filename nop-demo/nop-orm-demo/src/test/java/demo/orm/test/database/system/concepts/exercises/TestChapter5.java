package demo.orm.test.database.system.concepts.exercises;

import demo.orm.entity.Course;
import demo.orm.test.database.system.concepts.exercises.mapper.Chapter5Mapper;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TestChapter5 extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    Chapter5Mapper mapper;

    @BeforeEach
    public void initData() {
        new UniversityData(ormTemplate, daoProvider).init();
    }

    /**
     * 找出标题为 "Database System Concepts" 这门课程的所有先修课程，包括直接的和间接的。
     */
    @Test
    public void test_slide_01() {
        List<Course> courses = mapper.slide_01("Database System Concepts");
        Set<String> titles = Set.of(
                "Image Processing",
                "Game Design",
                "Intro. to Computer Science",
                "Robotics");
        Assertions.assertEquals(titles,
                courses.stream().map(Course::getTitle).collect(Collectors.toSet()));
    }

}
