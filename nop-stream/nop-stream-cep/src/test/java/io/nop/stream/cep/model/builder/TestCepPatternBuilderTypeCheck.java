package io.nop.stream.cep.model.builder;

import io.nop.stream.cep.model.CepPatternGroupModel;
import io.nop.stream.cep.model.CepPatternSingleModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCepPatternBuilderTypeCheck {

    @Test
    void testSetType_noOpOnSingle() {
        CepPatternSingleModel single = new CepPatternSingleModel();
        single.setType("group");
        assertEquals("single", single.getType(),
                "setType should be a no-op on single model");
    }

    @Test
    void testSetType_noOpOnGroup() {
        CepPatternGroupModel group = new CepPatternGroupModel();
        group.setType("single");
        assertEquals("group", group.getType(),
                "setType should be a no-op on group model");
    }
}
