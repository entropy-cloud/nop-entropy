package io.nop.orm.model.init;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.graph.DefaultDirectedGraph;
import io.nop.core.model.graph.DefaultEdge;
import io.nop.core.model.graph.IDirectedGraph;
import io.nop.core.model.graph.TopoEntry;
import io.nop.core.model.graph.TopologicalOrderIterator;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmModelConfigs;
import io.nop.orm.model.OrmToOneReferenceModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.orm.model.OrmModelErrors.ARG_LOOP_ENTITY_NAMES;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_REF_DEPENDS_CONTAINS_LOOP;

public class OrmModelTopEntryBuilder {
    static final Logger LOG = LoggerFactory.getLogger(OrmModelTopEntryBuilder.class);

    public void build(Collection<? extends IEntityModel> entityModels,
                      Map<String, TopoEntry<IEntityModel>> entryMap) {
        IDirectedGraph<IEntityModel, DefaultEdge<IEntityModel>> graph = buildDependsMap(entityModels);
        for (IEntityModel entityModel : graph.vertexSet()) {
            if (graph.getOutwardDegree(entityModel) != 0) {
                entityModel.setDependByOtherEntity(true);
            }
        }

        TopologicalOrderIterator<IEntityModel> it = graph.topologicalOrderIterator(false);
        int topoOrder = 0;
        while (it.hasNext()) {
            TopoEntry<IEntityModel> entry = new TopoEntry<>(topoOrder++, it.next());
            IEntityModel entityModel = entry.getValue();
            entryMap.put(entityModel.getName(), entry);
        }

        List<String> tableNames = entryMap.values().stream().map(
                entry -> entry.getValue().getTableName()).collect(Collectors.toList());
        LOG.debug("nop.orm.entity-topology-order:tables={}", tableNames);

        if (!it.getRemaining().isEmpty()) {
            Set<String> names = it.getRemaining().stream().map(IEntityModel::getName).collect(Collectors.toSet());
            if (OrmModelConfigs.CFG_ORM_CHECK_ENTITY_LOOP_DEPENDENCY.get()) {
                throw new NopException(ERR_ORM_MODEL_REF_DEPENDS_CONTAINS_LOOP).param(ARG_LOOP_ENTITY_NAMES, names);
            } else {
                LOG.warn("nop.orm.entity-dependency-contains-loop:loopEntityNames={}", names);
            }
        }
    }

    private IDirectedGraph<IEntityModel, DefaultEdge<IEntityModel>> buildDependsMap(Collection<? extends IEntityModel> entityModels) {
        Map<String, IEntityModel> entityMap = CollectionHelper.newHashMap(entityModels.size());
        for (IEntityModel entityModel : entityModels) {
            entityMap.put(entityModel.getName(), entityModel);
        }

        DefaultDirectedGraph<IEntityModel, DefaultEdge<IEntityModel>> graph = DefaultDirectedGraph.create();
        for (IEntityModel entityModel : entityModels) {
            graph.addVertex(entityModel);
            for (IEntityRelationModel rel : entityModel.getRelations()) {
                if (rel.isToOneRelation()) {
                    // 忽略自关联
                    if (entityModel.getName().equals(rel.getRefEntityName()))
                        continue;

                    if (rel.getRefEntityName().indexOf('.') < 0) {
                        if (StringHelper.simpleClassName(entityModel.getName()).equals(rel.getRefEntityName()))
                            continue;
                    }

                    // 忽略对于视图的依赖
                    if (rel.getRefEntityModel().isTableView())
                        continue;

                    // 如果指定了忽略关联依赖
                    OrmToOneReferenceModel toOne = (OrmToOneReferenceModel) rel;
                    if (toOne.isIgnoreDepends())
                        continue;

                    if (!rel.isReverseDepends()) {
                        IEntityModel refEntityModel = entityMap.get(rel.getRefEntityName());
                        // 子表依赖主表
                        graph.addEdge(refEntityModel, entityModel);
                    }
                }
            }
        }
        return graph;
    }
}
