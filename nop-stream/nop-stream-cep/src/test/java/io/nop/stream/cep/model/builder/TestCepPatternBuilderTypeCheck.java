package io.nop.stream.cep.model.builder;

import io.nop.stream.cep.model.CepPatternGroupModel;
import io.nop.stream.cep.model.CepPatternPartModel;
import io.nop.stream.cep.model.CepPatternSingleModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCepPatternBuilderTypeCheck {

    @Test
    void testSingleModel_instanceofCheck() {
        CepPatternPartModel single = new CepPatternSingleModel();
        assertTrue(single instanceof CepPatternSingleModel,
                "CepPatternSingleModel should be instanceof CepPatternSingleModel");
        assertFalse(single instanceof CepPatternGroupModel,
                "CepPatternSingleModel should not be instanceof CepPatternGroupModel");
    }

    @Test
    void testGroupModel_instanceofCheck() {
        CepPatternPartModel group = new CepPatternGroupModel();
        assertTrue(group instanceof CepPatternGroupModel,
                "CepPatternGroupModel should be instanceof CepPatternGroupModel");
        assertFalse(group instanceof CepPatternSingleModel,
                "CepPatternGroupModel should not be instanceof CepPatternSingleModel");
    }

    @Test
    void testSetType_noOpOnSingle() {
        CepPatternSingleModel single = new CepPatternSingleModel();
        single.setType("group");
        assertEquals("single", single.getType(),
                "setType should be a no-op, getType should still return 'single'");
    }

    @Test
    void testSetType_noOpOnGroup() {
        CepPatternGroupModel group = new CepPatternGroupModel();
        group.setType("single");
        assertEquals("group", group.getType(),
                "setType should be a no-op, getType should still return 'group'");
    }
}
