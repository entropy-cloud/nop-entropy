package io.nop.sys.dao.seq;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IDaoProvider;
import io.nop.sys.dao.entity.NopSysSequence;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSysSequenceGenerator extends JunitBaseTestCase {
    static final Logger LOG = LoggerFactory.getLogger(TestSysSequenceGenerator.class);

    @Inject
    IDaoProvider daoProvider;

    @Inject
    SysSequenceGenerator generator;

    @Test
    public void testSequence() {
        NopSysSequence seq = new NopSysSequence();
        seq.setNextValue(100L);
        seq.setSeqName("test");
        seq.setCacheSize(200);
        seq.setSeqType("default");
        seq.setIsUuid(DaoConstants.NO_VALUE);
        seq.setStepSize(1);

        daoProvider.daoFor(NopSysSequence.class).saveEntity(seq);

        String value = generator.generateString("test", true);
        assertEquals("100", value);

        LOG.info("before-generate");
        value = generator.generateString("test", true);
        assertEquals("101", value);

        value = generator.generateString("test", true);
        assertEquals("102", value);
        LOG.info("after-generate");

        NopSysSequence entity = daoProvider.daoFor(NopSysSequence.class).getEntityById(seq.orm_id());
        assertEquals(300, entity.getNextValue());
    }
}
