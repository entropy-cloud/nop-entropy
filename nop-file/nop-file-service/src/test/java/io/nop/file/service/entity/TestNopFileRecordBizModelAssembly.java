package io.nop.file.service.entity;

import io.nop.file.biz.INopFileRecordBiz;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WI12 small-module coverage: nop-file-service main code is a thin assembly
 * layer (three marker interfaces plus one CrudBizModel subclass), so the only
 * executable logic is the assembly wiring itself — the entity name pinned by
 * the constructor and the biz object name derived from the @BizModel
 * annotation. Full CRUD behaviour is exercised through the platform container
 * in integration scopes and is deliberately not duplicated here.
 */
public class TestNopFileRecordBizModelAssembly {

    private static final String ENTITY_NAME = "io.nop.file.dao.entity.NopFileRecord";

    @Test
    public void testConstructorPinsEntityName() {
        NopFileRecordBizModel bizModel = new NopFileRecordBizModel();
        assertEquals(ENTITY_NAME, bizModel.getEntityName(),
                "the constructor must pin the ORM entity name");
    }

    @Test
    public void testBizObjNameDerivesFromBizModelAnnotation() {
        NopFileRecordBizModel bizModel = new NopFileRecordBizModel();
        assertEquals("NopFileRecord", bizModel.getBizObjName(),
                "the GraphQL biz object name comes from the @BizModel annotation value");
    }

    @Test
    public void testEntityNameCanBeSetToSameValueButNotChanged() {
        NopFileRecordBizModel bizModel = new NopFileRecordBizModel();

        bizModel.setEntityName(ENTITY_NAME);
        assertEquals(ENTITY_NAME, bizModel.getEntityName(), "re-setting the same name is a no-op");

        assertThrows(IllegalArgumentException.class, () -> bizModel.setEntityName("other.Entity"),
                "changing a pinned entity name is rejected fail-fast");
    }

    @Test
    public void testImplementsFileRecordBizInterface() {
        NopFileRecordBizModel bizModel = new NopFileRecordBizModel();
        assertInstanceOf(INopFileRecordBiz.class, bizModel,
                "the biz model exposes the INopFileRecordBiz service face");
        assertEquals(ENTITY_NAME, io.nop.file.dao.entity.NopFileRecord.class.getName(),
                "the pinned name matches the live entity class");
    }
}
