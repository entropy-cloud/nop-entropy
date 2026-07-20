package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaGlossary;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaSemanticType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSeedGlossaryData extends JunitBaseTestCase {

    public TestSeedGlossaryData() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Test
    @SuppressWarnings("unchecked")
    public void testSeedGlossaryFromSemanticTypes() {
        IEntityDao<NopMetaSemanticType> typeDao = daoProvider.daoFor(NopMetaSemanticType.class);
        Timestamp now = CoreMetrics.currentTimestamp();

        String[][] types = {
                {"PK", "Primary Key", "A unique identifier for a record"},
                {"FK", "Foreign Key", "A field that references a primary key in another table"},
                {"Name", "Name", "A descriptive name or title"},
                {"Title", "Title", "A formal title or designation"},
                {"Date", "Date", "A calendar date value"},
                {"Currency", "Currency", "A monetary amount with currency code"},
                {"Email", "Email", "An electronic mail address"},
                {"Phone", "Phone", "A telephone number"},
                {"URL", "URL", "A Uniform Resource Locator"}
        };

        for (String[] t : types) {
            NopMetaSemanticType st = new NopMetaSemanticType();
            st.setSemanticTypeId(UUID.randomUUID().toString().replace("-", ""));
            st.setTypeName(t[0]);
            st.setDisplayName(t[1]);
            st.setDescription(t[2]);
            st.setVersion(1L);
            st.setCreatedBy("autotest");
            st.setCreateTime(now);
            st.setUpdatedBy("autotest");
            st.setUpdateTime(now);
            typeDao.saveEntity(st);
        }

        GraphQLResponseBean impResp = execute(
                "mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\")" +
                        " { metaModuleId } }");
        assertFalse(impResp.hasError(), "import should not error: " + impResp);
        String metaModuleId = (String) mutationResult(impResp).get("metaModuleId");

        GraphQLResponseBean relResp = execute(
                "mutation { NopMetaModule__releaseModule(metaModuleId: \"" + metaModuleId + "\")" +
                        " { status } }");
        assertFalse(relResp.hasError(), "release should not error: " + relResp);

        IEntityDao<NopMetaGlossary> glossaryDao = daoProvider.daoFor(NopMetaGlossary.class);
        List<NopMetaGlossary> glossaries = glossaryDao.findAll();
        NopMetaGlossary builtIn = null;
        for (NopMetaGlossary g : glossaries) {
            if ("BuiltIn".equals(g.getName())) {
                builtIn = g;
                break;
            }
        }
        assertNotNull(builtIn, "BuiltIn Glossary must exist");
        assertEquals("BuiltIn", builtIn.getName());
        assertEquals("Built-in Glossary", builtIn.getDisplayName());

        IEntityDao<NopMetaGlossaryTerm> termDao = daoProvider.daoFor(NopMetaGlossaryTerm.class);
        List<NopMetaGlossaryTerm> terms = termDao.findAll();
        assertEquals(9, terms.size(), "9 glossary terms must be seeded");

        for (NopMetaGlossaryTerm term : terms) {
            assertEquals(builtIn.getGlossaryId(), term.getGlossaryId(),
                    "all terms must belong to the BuiltIn glossary");
            assertNotNull(term.getFullyQualifiedName());
            assertTrue(term.getFullyQualifiedName().startsWith("BuiltIn."),
                    "fullyQualifiedName must start with 'BuiltIn.': " + term.getFullyQualifiedName());
        }

        long countBefore = termDao.findAll().size();
        new SeedGlossaryData().seedGlossaryTerms(daoProvider);
        long countAfter = termDao.findAll().size();
        assertEquals(countBefore, countAfter, "seed must be idempotent");
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutationResult(GraphQLResponseBean response) {
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return (Map<String, Object>) data.values().iterator().next();
    }
}
