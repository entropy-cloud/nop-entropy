package demo.orm.test.database.system.concepts.exercises;

import demo.orm.entity.*;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;

import java.math.BigDecimal;

public class UniversityData {

    private IDaoProvider daoProvider;

    private IOrmTemplate ormTemplate;

    public UniversityData(IOrmTemplate ormTemplate, IDaoProvider daoProvider) {
        this.ormTemplate = ormTemplate;
        this.daoProvider = daoProvider;
    }

    private static final String[][] courses = {
            {"BIO-101", "Intro. to Biology", "Biology", "4"},
            {"BIO-301", "Genetics", "Biology", "4"},
            {"BIO-399", "Computational Biology", "Biology", "3"},
            {"CS-101", "Intro. to Computer Science", "Comp. Sci.", "4"},
            {"CS-190", "Game Design", "Comp. Sci.", "4"},
            {"CS-315", "Robotics", "Comp. Sci.", "3"},
            {"CS-319", "Image Processing", "Comp. Sci.", "3"},
            {"CS-347", "Database System Concepts", "Comp. Sci.", "3"},
            {"EE-181", "Intro. to Digital Systems", "Elec. Eng.", "3"},
            {"FIN-201", "Investment Banking", "Finance", "3"},
            {"HIS-351", "World History", "History", "3"},
            {"MU-199", "Music Video Production", "Music", "3"},
            {"PHY-101", "Physical Principles", "Physics", "4"},
    };

    private static final String[][] classrooms = {
            {"Packard", "101", "500"},
            {"Painter", "514", "10"},
            {"Taylor", "3128", "70"},
            {"Watson", "100", "30"},
            {"Watson", "120", "50"},
    };

    private static final String[][] students = {
            {"00128", "Zhang", "Comp. Sci.", "102"},
            {"12345", "Shankar", "Comp. Sci.", "32"},
            {"19991", "Brandt", "History", "80"},
            {"23121", "Chavez", "Finance", "110"},
            {"44553", "Peltier", "Physics", "56"},
            {"45678", "Levy", "Physics", "46"},
            {"54321", "Williams", "Comp. Sci.", "54"},
            {"55739", "Sanchez", "Music", "38"},
            {"70557", "Snow", "Physics", "0"},
            {"76543", "Brown", "Comp. Sci.", "58"},
            {"76653", "Aoi", "Elec. Eng.", "60"},
            {"98765", "Bourikas", "Elec. Eng.", "98"},
            {"98988", "Tanaka", "Biology", "120"},
    };

    private static final String[][] instructors = {
            {"10101", "Srinivasan", "Comp. Sci.", "65000"},
            {"12121", "Wu", "Finance", "90000"},
            {"15151", "Mozart", "Music", "40000"},
            {"22222", "Einstein", "Physics", "95000"},
            {"32343", "El Said", "History", "60000"},
            {"33456", "Gold", "Physics", "87000"},
            {"45565", "Katz", "Comp. Sci.", "75000"},
            {"58583", "Califieri", "History", "62000"},
            {"76543", "Singh", "Finance", "80000"},
            {"76766", "Crick", "Biology", "72000"},
            {"83821", "Brandt", "Comp. Sci.", "92000"},
            {"98345", "Kim", "Elec. Eng.", "80000"},
    };

    private static final String[][] teachings = {
            {"10101", "CS-101", "1", "Fall", "2017"},
            {"10101", "CS-315", "1", "Spring", "2018"},
            {"10101", "CS-347", "1", "Fall", "2017"},
            {"12121", "FIN-201", "1", "Spring", "2018"},
            {"15151", "MU-199", "1", "Spring", "2018"},
            {"22222", "PHY-101", "1", "Fall", "2017"},
            {"32343", "HIS-351", "1", "Spring", "2018"},
            {"45565", "CS-101", "1", "Spring", "2018"},
            {"45565", "CS-319", "1", "Spring", "2018"},
            {"76766", "BIO-101", "1", "Summer", "2017"},
            {"76766", "BIO-301", "1", "Summer", "2018"},
            {"83821", "CS-190", "1", "Spring", "2017"},
            {"83821", "CS-190", "2", "Spring", "2017"},
            {"83821", "CS-319", "2", "Spring", "2018"},
            {"98345", "EE-181", "1", "Spring", "2017"},
    };

    private static final String[][] takings = {
            {"00128", "CS-101", "1", "Fall", "2017", "A"},
            {"00128", "CS-347", "1", "Fall", "2017", "A-"},
            {"12345", "CS-101", "1", "Fall", "2017", "C"},
            {"12345", "CS-190", "2", "Spring", "2017", "A"},
            {"12345", "CS-315", "1", "Spring", "2018", "A"},
            {"12345", "CS-347", "1", "Fall", "2017", "A"},
            {"19991", "HIS-351", "1", "Spring", "2018", "B"},
            {"23121", "FIN-201", "1", "Spring", "2018", "C+"},
            {"44553", "PHY-101", "1", "Fall", "2017", "B-"},
            {"45678", "CS-101", "1", "Fall", "2017", "F"},
            {"45678", "CS-101", "1", "Spring", "2018", "B+"},
            {"45678", "CS-319", "1", "Spring", "2018", "B"},
            {"54321", "CS-101", "1", "Fall", "2017", "A-"},
            {"54321", "CS-190", "2", "Spring", "2017", "B+"},
            {"55739", "MU-199", "1", "Spring", "2018", "A-"},
            {"76543", "CS-101", "1", "Fall", "2017", "A"},
            {"76543", "CS-319", "2", "Spring", "2018", "A"},
            {"76653", "EE-181", "1", "Spring", "2017", "C"},
            {"98765", "CS-101", "1", "Fall", "2017", "C-"},
            {"98765", "CS-315", "1", "Spring", "2018", "B"},
            {"98988", "BIO-101", "1", "Summer", "2017", "A"},
            {"98988", "BIO-301", "1", "Summer", "2018", null},
    };

    private static final String[][] sections = {
            {"BIO-101", "1", "Summer", "2017", "Painter", "514", "B"},
            {"BIO-301", "1", "Summer", "2018", "Painter", "514", "A"},
            {"CS-101", "1", "Fall", "2017", "Packard", "101", "H"},
            {"CS-101", "1", "Spring", "2018", "Packard", "101", "F"},
            {"CS-190", "1", "Spring", "2017", "Taylor", "3128", "E"},
            {"CS-190", "2", "Spring", "2017", "Taylor", "3128", "A"},
            {"CS-315", "1", "Spring", "2018", "Watson", "120", "D"},
            {"CS-319", "1", "Spring", "2018", "Watson", "100", "B"},
            {"CS-319", "2", "Spring", "2018", "Taylor", "3128", "C"},
            {"CS-347", "1", "Fall", "2017", "Taylor", "3128", "A"},
            {"EE-181", "1", "Spring", "2017", "Taylor", "3128", "C"},
            {"FIN-201", "1", "Spring", "2018", "Packard", "101", "B"},
            {"HIS-351", "1", "Spring", "2018", "Painter", "514", "C"},
            {"MU-199", "1", "Spring", "2018", "Packard", "101", "D"},
            {"PHY-101", "1", "Fall", "2017", "Watson", "100", "A"},
    };

    private static final String[][] timeSlots = {
            {"A", "M", "8", "0", "8", "50"},
            {"A", "W", "8", "0", "8", "50"},
            {"A", "F", "8", "0", "8", "50"},
            {"B", "M", "9", "0", "9", "50"},
            {"B", "W", "9", "0", "9", "50"},
            {"B", "F", "9", "0", "9", "50"},
            {"C", "M", "11", "0", "11", "50"},
            {"C", "W", "11", "0", "11", "50"},
            {"C", "F", "11", "0", "11", "50"},
            {"D", "M", "13", "0", "13", "50"},
            {"D", "W", "13", "0", "13", "50"},
            {"D", "F", "13", "0", "13", "50"},
            {"E", "T", "10", "30", "11", "45"},
            {"E", "R", "10", "30", "11", "45"},
            {"F", "T", "14", "30", "15", "45"},
            {"F", "R", "14", "30", "15", "45"},
            {"G", "M", "16", "0", "16", "50"},
            {"G", "W", "16", "0", "16", "50"},
            {"G", "F", "16", "0", "16", "50"},
            {"H", "W", "10", "0", "12", "30"},
    };

    private static final String[][] preReqs = {
            {"BIO-301", "BIO-101"},
            {"BIO-399", "BIO-101"},
            {"CS-190", "CS-101"},
            {"CS-315", "CS-101"},
            {"CS-319", "CS-315"},
            {"CS-347", "CS-319"},
            {"CS-347", "CS-190"},
            {"EE-181", "PHY-101"},
    };

    public void init() {
        initTimeSlots();
        initClassrooms();
        initDepartments();
        initStudents();
        initCourses();
        initSections();
        initInstructors();
        initTeachings();
        initTakings();
        initPreReqs();
    }

    private void initPreReqs() {
        ormTemplate.runInSession(() -> {
            for (var row : preReqs) {
                savePreReq(row[0], row[1]);
            }
        });
    }

    private PreReq savePreReq(String courseId, String preReqId) {
        IEntityDao<PreReq> dao = daoProvider.daoFor(PreReq.class);
        PreReq preReq = dao.newEntity();
        preReq.setCourseId(courseId);
        preReq.setPreReqId(preReqId);
        dao.saveEntity(preReq);
        return preReq;
    }

    private void initTimeSlots() {
        ormTemplate.runInSession(() -> {
            for (var row : timeSlots) {
                saveTimeSlot(row[0], row[1],
                        new BigDecimal(row[2]),
                        new BigDecimal(row[3]),
                        new BigDecimal(row[4]),
                        new BigDecimal(row[5]));
            }
        });
    }

    private TimeSlot saveTimeSlot(String timeSlotId,
                                  String day,
                                  BigDecimal startHr,
                                  BigDecimal startMin,
                                  BigDecimal endHr,
                                  BigDecimal endMin) {
        IEntityDao<TimeSlot> dao = daoProvider.daoFor(TimeSlot.class);
        TimeSlot timeSlot = dao.newEntity();
        timeSlot.setTimeSlotId(timeSlotId);
        timeSlot.setDay(day);
        timeSlot.setStartHr(startHr);
        timeSlot.setStartMin(startMin);
        timeSlot.setEndHr(endHr);
        timeSlot.setEndMin(endMin);
        dao.saveEntity(timeSlot);
        return timeSlot;
    }

    private void initSections() {
        ormTemplate.runInSession(() -> {
            for (var row : sections) {
                saveSection(row[0], row[1], row[2], new BigDecimal(row[3]),
                        row[4], row[5], row[6]);
            }
        });
    }

    private Section saveSection(String courseId,
                                String secId,
                                String semester,
                                BigDecimal year,
                                String building,
                                String roomNumber,
                                String timeSlotId) {
        IEntityDao<Section> dao = daoProvider.daoFor(Section.class);
        Section section = dao.newEntity();
        section.setCourseId(courseId);
        section.setSecId(secId);
        section.setSemester(semester);
        section.setYear(year);
        section.setBuilding(building);
        section.setRoomNumber(roomNumber);
        section.setTimeSlotId(timeSlotId);
        dao.saveEntity(section);
        return section;
    }

    private void initTakings() {
        ormTemplate.runInSession(() -> {
            for (var row : takings) {
                saveTaking(row[0], row[1], row[2], row[3], new BigDecimal(row[4]), row[5]);
            }
        });
    }

    private Taking saveTaking(String studentId,
                              String courseId,
                              String secId,
                              String semester,
                              BigDecimal year,
                              String grade) {
        IEntityDao<Taking> dao = daoProvider.daoFor(Taking.class);
        Taking taking = dao.newEntity();
        taking.setStudentId(studentId);
        taking.setCourseId(courseId);
        taking.setSecId(secId);
        taking.setSemester(semester);
        taking.setYear(year);
        taking.setGrade(grade);
        dao.saveEntity(taking);
        return taking;
    }

    private void initTeachings() {
        ormTemplate.runInSession(() -> {
            for (var row : teachings) {
                saveTeaching(row[0], row[1], row[2], row[3], new BigDecimal(row[4]));
            }
        });
    }

    private Teaching saveTeaching(String instructorId,
                                  String courseId,
                                  String secId,
                                  String semester,
                                  BigDecimal year) {
        IEntityDao<Teaching> dao = daoProvider.daoFor(Teaching.class);
        Teaching teaching = dao.newEntity();
        teaching.setInstructorId(instructorId);
        teaching.setCourseId(courseId);
        teaching.setSecId(secId);
        teaching.setSemester(semester);
        teaching.setYear(year);
        dao.saveEntity(teaching);
        return teaching;
    }

    private void initDepartments() {
        ormTemplate.runInSession(() -> {
            saveDepartment("Biology", "Watson", new BigDecimal("90000"));
            saveDepartment("Comp. Sci.", "Taylor", new BigDecimal("100000"));
            saveDepartment("Elec. Eng.", "Taylor", new BigDecimal("85000"));
            saveDepartment("Finance", "Painter", new BigDecimal("120000"));
            saveDepartment("History", "Painter", new BigDecimal("50000"));
            saveDepartment("Music", "Packard", new BigDecimal("80000"));
            saveDepartment("Physics", "Watson", new BigDecimal("70000"));
        });
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

    private void initClassrooms() {
        ormTemplate.runInSession(() -> {
            for (var row : classrooms) {
                saveClassroom(row[0], row[1], new BigDecimal(row[2]));
            }
        });
    }

    private Classroom saveClassroom(String building,
                                    String roomNumber,
                                    BigDecimal capacity) {
        IEntityDao<Classroom> dao = daoProvider.daoFor(Classroom.class);
        Classroom classroom = dao.newEntity();
        classroom.setBuilding(building);
        classroom.setRoomNumber(roomNumber);
        classroom.setCapacity(capacity);
        dao.saveEntity(classroom);
        return classroom;
    }

    private void initCourses() {
        ormTemplate.runInSession(() -> {
            for (var row : courses) {
                saveCourse(row[0], row[1], row[2], new BigDecimal(row[3]));
            }
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

    private void initStudents() {
        ormTemplate.runInSession(() -> {
            for (var row : students) {
                saveStudent(row[0], row[1], row[2], new BigDecimal(row[3]));
            }
        });
    }

    private Student saveStudent(String id,
                                String name,
                                String deptName,
                                BigDecimal totCred) {
        IEntityDao<Student> dao = daoProvider.daoFor(Student.class);
        Student student = dao.newEntity();
        student.setId(id);
        student.setName(name);
        student.setDeptName(deptName);
        student.setTotCred(totCred);
        dao.saveEntity(student);
        return student;
    }

    private void initInstructors() {
        ormTemplate.runInSession(() -> {
            for (var row : instructors) {
                saveInstructor(row[0], row[1], row[2], new BigDecimal(row[3]));
            }
        });
    }

    private Instructor saveInstructor(String id,
                                      String name,
                                      String deptName,
                                      BigDecimal salary) {
        IEntityDao<Instructor> dao = daoProvider.daoFor(Instructor.class);
        Instructor instructor = dao.newEntity();
        instructor.setId(id);
        instructor.setName(name);
        instructor.setDeptName(deptName);
        instructor.setSalary(salary);
        dao.saveEntity(instructor);
        return instructor;
    }


}
