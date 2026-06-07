package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.semantic.SemanticRelationType;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.orm.model.IOrmModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestSemanticRelationTypeDict extends JunitAutoTestCase {

    @Test
    void testSemanticRelationTypeDictExistsInOrmModel() {
        IOrmModel ormModel = (IOrmModel) ResourceComponentManager.instance()
                .loadComponentModel("/nop/code/orm/_app.orm.xml");

        DictBean dict = ormModel.getDict("code/semantic_relation_type");
        assertNotNull(dict, "Dictionary code/semantic_relation_type should exist in ORM model");
    }

    @Test
    void testSemanticRelationTypeDictContainsAllEnumValues() {
        IOrmModel ormModel = (IOrmModel) ResourceComponentManager.instance()
                .loadComponentModel("/nop/code/orm/_app.orm.xml");

        DictBean dict = ormModel.getDict("code/semantic_relation_type");
        assertNotNull(dict, "Dictionary code/semantic_relation_type should exist in ORM model");

        List<DictOptionBean> options = dict.getOptions();
        Set<String> optionCodes = options.stream()
                .map(DictOptionBean::getCode)
                .collect(Collectors.toSet());

        for (SemanticRelationType type : SemanticRelationType.values()) {
            assertTrue(optionCodes.contains(type.name()),
                    "Dict should contain option for " + type.name() + ", but found: " + optionCodes);
        }

        assertEquals(SemanticRelationType.values().length, options.size(),
                "Dict should have exactly " + SemanticRelationType.values().length + " options");
    }

    @Test
    void testSemanticEdgeEntityHasRelationTypeColumn() {
        IOrmModel ormModel = (IOrmModel) ResourceComponentManager.instance()
                .loadComponentModel("/nop/code/orm/_app.orm.xml");

        var entity = ormModel.requireEntityModel("io.nop.code.dao.entity.NopCodeSemanticEdge");
        assertNotNull(entity.getColumn("relationType", false),
                "NopCodeSemanticEdge should have relationType column");
    }
}
