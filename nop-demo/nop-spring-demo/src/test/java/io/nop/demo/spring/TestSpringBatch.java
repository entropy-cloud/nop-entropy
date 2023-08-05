package io.nop.demo.spring;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;

@SpringBootTest
@ComponentScan("io.nop.demo.spring")
@AutoConfigureTestDatabase
@ImportResource("classpath:spring-batch-job.beans.xml")
public class TestSpringBatch {
    @Autowired
    JobLauncher jobLauncher;

    @Autowired
    @Qualifier("testMyJob")
    Job job;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }


    @Test
    public void testJob() throws Exception {
        JobParameters params = new JobParameters();
        JobExecution execution = jobLauncher.run(job, params);
        System.out.println(execution.getExitStatus());
    }
}
