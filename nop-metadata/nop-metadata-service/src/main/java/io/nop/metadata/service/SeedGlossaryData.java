package io.nop.metadata.service;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaGlossary;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaSemanticType;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class SeedGlossaryData {

    public void seedGlossaryTerms(IDaoProvider daoProvider) {
        IEntityDao<NopMetaGlossary> glossaryDao = daoProvider.daoFor(NopMetaGlossary.class);

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopMetaGlossary.PROP_NAME_name, "BuiltIn"));
        NopMetaGlossary existing = glossaryDao.findFirstByQuery(query);
        if (existing != null)
            return;

        Timestamp now = CoreMetrics.currentTimestamp();

        NopMetaGlossary glossary = new NopMetaGlossary();
        glossary.setGlossaryId(UUID.randomUUID().toString().replace("-", ""));
        glossary.setName("BuiltIn");
        glossary.setDisplayName("Built-in Glossary");
        glossary.setVersion(1L);
        glossary.setCreatedBy("system");
        glossary.setCreateTime(now);
        glossary.setUpdatedBy("system");
        glossary.setUpdateTime(now);
        glossaryDao.saveEntity(glossary);

        String glossaryId = glossary.getGlossaryId();

        IEntityDao<NopMetaSemanticType> typeDao = daoProvider.daoFor(NopMetaSemanticType.class);
        List<NopMetaSemanticType> types = typeDao.findAll();

        IEntityDao<NopMetaGlossaryTerm> termDao = daoProvider.daoFor(NopMetaGlossaryTerm.class);

        for (NopMetaSemanticType type : types) {
            if (type.getTypeName() == null)
                continue;

            NopMetaGlossaryTerm term = new NopMetaGlossaryTerm();
            term.setGlossaryTermId(UUID.randomUUID().toString().replace("-", ""));
            term.setGlossaryId(glossaryId);
            term.setName(type.getTypeName());
            term.setFullyQualifiedName("BuiltIn." + type.getTypeName());
            term.setDisplayName(type.getDisplayName());
            term.setDescription(type.getDescription());
            term.setVersion(1L);
            term.setCreatedBy("system");
            term.setCreateTime(now);
            term.setUpdatedBy("system");
            term.setUpdateTime(now);
            termDao.saveEntity(term);
        }
    }
}
