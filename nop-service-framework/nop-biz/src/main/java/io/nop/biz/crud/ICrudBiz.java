/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.std.StdTreeEntity;
import io.nop.core.context.IServiceContext;
import io.nop.orm.IOrmEntity;
import io.nop.xlang.xmeta.IObjMeta;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRUD业务接口，提供标准的增删改查功能
 * <p>
 * 该接口用于AI代码生成，定义了实体对象的基本操作，包括查询、新增、修改、删除等功能
 * <p>
 * <b>核心概念说明：</b>
 * <ul>
 * <li><b>T</b>: 实体类型，必须实现IOrmEntity接口，对应数据库中的一条记录</li>
 * <li><b>IServiceContext</b>: 服务上下文，包含用户会话、数据权限校验器等信息，通常由框架自动传入</li>
 * <li><b>QueryBean</b>: 查询条件对象，用于构建分页、过滤、排序等查询条件</li>
 * <li><b>FieldSelectionBean</b>: 字段选择对象，用于指定需要返回哪些字段，GraphQL中使用</li>
 * <li><b>Map&lt;String, Object&gt;</b>: 数据映射，key为字段名，value为字段值，用于增删改操作</li>
 * </ul>
 * <p>
 * 标记了@BizQuery和@BizMutation注解的方法是通过GraphQL/REST接口可以调用的服务方法，而标记了@BizAction的方法是内部使用的方法
 *
 * @param <T> 实体类型，必须实现IOrmEntity接口
 */
public interface ICrudBiz<T extends IOrmEntity> {

    // ==================== 查询操作 ====================

    /**
     * 获取符合条件的记录总数
     *
     * @param query   查询条件，包含过滤条件等。如果为null，则查询所有记录
     * @param context 服务上下文，包含用户信息、数据权限校验器等
     * @return 记录总数
     */
    @BizQuery
    long findCount(@Optional @Name("query") QueryBean query, IServiceContext context);

    /**
     * 分页查询记录
     * <p>
     * 示例代码：
     * <pre>
     * QueryBean query = new QueryBean();
     * query.setOffset(0);  // 起始位置
     * query.setLimit(20);  // 每页条数
     * query.addFilter(FilterBeans.eq("status", 1));  // 添加过滤条件
     * query.addOrderBy(OrderFieldBean.asc("createTime"));  // 添加排序
     * PageBean&lt;User&gt; page = userBiz.findPage(query, null, context);
     * List&lt;User&gt; users = page.getItems();
     * long total = page.getTotal();
     * </pre>
     *
     * @param query     查询条件，包含分页参数(offset, limit)、过滤条件、排序条件等。如果为null则返回第一页（limit=20）
     * @param selection 字段选择对象，用于GraphQL场景指定返回哪些字段，普通查询传null即可
     * @param context   服务上下文，包含用户信息、数据权限校验器等
     * @return 分页结果对象，包含items(数据列表)、total(总记录数)等字段
     */
    @BizQuery
    PageBean<T> findPage(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    /**
     * 返回符合条件的第一条数据
     *
     * @param query     查询条件，包含过滤条件、排序条件等。如果为null则返回第一条记录
     * @param selection 字段选择对象，用于GraphQL场景指定返回哪些字段，普通查询传null即可
     * @param context   服务上下文，包含用户信息、数据权限校验器等
     * @return 第一条符合条件的记录，如果没有则返回null
     */
    @BizQuery
    T findFirst(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    /**
     * 根据查询条件返回列表数据
     * <p>
     * 与findPage的不同在于:
     * <ul>
     * <li>findPage返回PageBean类型，支持分页，可以获取总数</li>
     * <li>findList返回List类型，适合数据量不大的场景，默认最多返回配置的最大条数</li>
     * </ul>
     *
     * @param query     查询条件，包含过滤条件、排序条件等。如果不指定limit，则使用系统默认的最大值
     * @param selection 字段选择对象，用于GraphQL场景指定返回哪些字段，普通查询传null即可
     * @param context   服务上下文，包含用户信息、数据权限校验器等
     * @return 符合条件的记录列表
     */
    @BizQuery
    List<T> findList(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    /**
     * 根据主键ID获取单条记录
     *
     * @param id            主键ID，字符串形式。如果是复合主键，会自动解析转换为OrmCompositePk
     * @param ignoreUnknown 如果为true，找不到记录时返回null；如果为false，找不到记录时抛出异常
     * @param context       服务上下文，包含用户信息、数据权限校验器等
     * @return 找到的记录，如果ignoreUnknown=true且找不到则返回null
     * @throws io.nop.dao.exceptions.UnknownEntityException 当找不到记录且ignoreUnknown=false时抛出
     */
    @BizQuery
    T get(@Name("id") String id, @Optional @Name("ignoreUnknown") boolean ignoreUnknown, IServiceContext context);

    /**
     * 根据主键ID批量获取记录
     *
     * @param ids           主键ID集合
     * @param ignoreUnknown 如果为true，找不到的ID会被忽略；如果为false，任何一个ID找不到都抛出异常
     * @param context       服务上下文，包含用户信息、数据权限校验器等
     * @return 找到的记录列表，顺序可能与ids不一致
     */
    @BizQuery
    List<T> batchGet(@Name("ids") Collection<String> ids, @Optional @Name("ignoreUnknown") boolean ignoreUnknown, IServiceContext context);

    /**
     * 将实体记录作为字典项返回
     * <p>
     * 通常用于下拉选择框等场景，返回id和displayName的对应关系
     *
     * @param context 服务上下文
     * @return 字典Bean，包含id-value的映射关系
     */
    @BizQuery
    DictBean asDict(IServiceContext context);

    // ==================== 新增操作 ====================

    /**
     * 保存（新增）一条记录
     * <p>
     * 根据传入的data创建新记录并保存到数据库。如果data中包含id字段，且该id的记录已存在且未逻辑删除，则会抛出异常。
     * <p>
     * 示例代码：
     * <pre>
     * Map&lt;String, Object&gt; data = new HashMap&lt;&gt;();
     * data.put("name", "张三");
     * data.put("age", 25);
     * data.put("status", 1);
     * User user = userBiz.save(data, context);
     * </pre>
     *
     * @param data    数据映射，key为字段名，value为字段值。如果字段不存在会抛出验证异常
     * @param context 服务上下文
     * @return 保存后的实体对象，包含数据库生成的id、创建时间等字段
     */
    @BizMutation
    T save(@Name("data") Map<String, Object> data, IServiceContext context);

    /**
     * 根据是否有id来决定是新增还是更新
     * <p>
     * <ul>
     * <li>如果data中没有id字段，或者id为空，则执行新增操作</li>
     * <li>如果data中有id字段且不为空，则执行更新操作</li>
     * </ul>
     *
     * @param data    数据映射，通常从表单提交
     * @param context 服务上下文
     * @return 保存或更新后的实体对象
     */
    @BizMutation
    T saveOrUpdate(@Name("data") Map<String, Object> data, IServiceContext context);

    /**
     * 复制新建
     * <p>
     * 根据现有记录创建一条新记录。data中必须包含源记录的id，会复制该记录的所有可复制字段到新记录。
     * 主键字段会被重置，序列号字段会被清空，由数据库自动生成。
     *
     * @param data    数据映射，必须包含源记录的id字段。可以包含需要覆盖的字段
     * @param context 服务上下文
     * @return 新创建的记录
     */
    @BizMutation
    T copyForNew(@Name("data") Map<String, Object> data, IServiceContext context);

    // ==================== 修改操作 ====================

    /**
     * 更新一条记录
     * <p>
     * 根据data中的id找到记录，然后用data中的值更新该记录的字段。
     * <p>
     * 示例代码：
     * <pre>
     * Map&lt;String, Object&gt; data = new HashMap&lt;&gt;();
     * data.put("id", "123");
     * data.put("name", "李四");
     * data.put("age", 26);
     * User user = userBiz.update(data, context);
     * </pre>
     *
     * @param data    数据映射，必须包含id字段，指定要更新的记录。其他字段为要更新的值
     * @param context 服务上下文
     * @return 更新后的实体对象
     */
    @BizMutation
    T update(@Name("data") Map<String, Object> data, IServiceContext context);

    /**
     * 批量修改多条记录
     * <p>
     * 对指定的多条记录，统一更新相同的字段值。
     *
     * @param ids           主键ID集合，指定要更新哪些记录
     * @param data          要更新的字段映射，key为字段名，value为新的值。这些值会应用到所有ids对应的记录上
     * @param ignoreUnknown 如果为true，找不到的ID会被忽略；如果为false，任何一个ID找不到都抛出异常
     * @param context       服务上下文
     */
    @BizMutation
    void batchUpdate(@Name("ids") Set<String> ids, @Name("data") Map<String, Object> data, @Optional @Name("ignoreUnknown") boolean ignoreUnknown, IServiceContext context);

    /**
     * 根据查询条件批量更新符合条件的记录
     * <p>
     * 先根据query查询出符合条件的记录，然后用data中的值更新这些记录。
     *
     * @param query   查询条件，找出要更新的记录
     * @param data    要更新的字段映射
     * @param context 服务上下文
     * @return 实际更新的记录数
     */
    @BizMutation
    int updateByQuery(@Name("query") QueryBean query, @Name("data") Map<String, Object> data, IServiceContext context);

    // ==================== 删除操作 ====================

    /**
     * 根据主键删除一条记录
     * <p>
     * 如果配置了逻辑删除，则执行逻辑删除（设置删除标记）。
     * 如果配置了级联删除，则自动删除关联的子记录。
     *
     * @param id      主键ID
     * @param context 服务上下文
     * @return true表示删除成功，false表示记录不存在
     */
    @BizMutation
    boolean delete(@Name("id") String id, IServiceContext context);

    /**
     * 批量删除多条记录
     * <p>
     * 根据主键ID集合批量删除记录。
     *
     * @param ids     主键ID集合
     * @param context 服务上下文
     * @return 未成功删除的ID集合（如记录不存在或被其他记录引用）
     */
    @BizMutation
    Set<String> batchDelete(@Name("ids") Set<String> ids, IServiceContext context);

    /**
     * 批量增删改操作
     * <p>
     * 支持在一个操作中同时进行新增、修改、删除，常用于表格编辑场景。
     * <ul>
     * <li>data中的记录：根据是否有id决定是新增还是更新</li>
     * <li>common：公共字段，会合并到data的每条记录中</li>
     * <li>delIds：要删除的记录ID集合</li>
     * </ul>
     * <p>
     * 示例场景：用户在前端编辑表格，新增了几行，修改了几行，删除了几行，一次提交。
     *
     * @param data    记录列表，每条记录是一个Map。如果Map中有id字段则更新，否则新增
     * @param common  公共字段，会应用到data中的每条记录上。可以为null
     * @param delIds  要删除的记录ID集合。可以为null或空集合
     * @param context 服务上下文
     */
    @BizMutation
    void batchModify(@Name("data") List<Map<String, Object>> data, @Optional @Name("common") Map<String, Object> common,
                     @Optional @Name("delIds") Set<String> delIds, IServiceContext context);

    /**
     * 根据查询条件批量删除符合条件的记录
     * <p>
     * 先根据query查询出符合条件的记录，然后删除这些记录。
     *
     * @param query   查询条件，找出要删除的记录
     * @param context 服务上下文
     * @return 实际删除的记录数
     */
    @BizMutation
    int deleteByQuery(@Name("query") QueryBean query, IServiceContext context);

    // ==================== 多对多关联操作 ====================

    /**
     * 新增多对多关联关系
     * <p>
     * 例如：用户和角色的多对多关系，给用户添加角色。
     *
     * @param id        主实体的ID，例如用户ID
     * @param propName  多对多属性名，在xmeta中定义的关联属性名
     * @param relValues 关联实体的ID集合，例如角色ID列表
     * @param filter    过滤条件，可以限制添加哪些关联。可以为null
     * @param context   服务上下文
     */
    @BizMutation
    void addManyToManyRelations(@Name("id") String id, @Name("propName") String propName, @Name("relValues") Collection<String> relValues,
                                 @Optional @Name("filter") TreeBean filter, IServiceContext context);

    /**
     * 删除多对多关联关系
     * <p>
     * 从主实体的多对多关联中移除指定的关联记录。
     *
     * @param id        主实体的ID
     * @param propName  多对多属性名
     * @param relValues 要删除的关联实体ID集合
     * @param filter    过滤条件，可以限制删除哪些关联。可以为null
     * @param context   服务上下文
     */
    @BizMutation
    void removeManyToManyRelations(@Name("id") String id, @Name("propName") String propName, @Name("relValues") Collection<String> relValues,
                                    @Optional @Name("filter") TreeBean filter, IServiceContext context);

    /**
     * 更新多对多关联关系
     * <p>
     * 将主实体的指定多对多关联更新为relValues中的值，即替换掉原有的关联关系。
     *
     * @param id        主实体的ID
     * @param propName  多对多属性名
     * @param relValues 新的关联实体ID集合
     * @param filter    过滤条件，可以限制更新哪些关联。可以为null
     * @param context   服务上下文
     */
    @BizMutation
    void updateManyToManyRelations(@Name("id") String id, @Name("propName") String propName, @Name("relValues") Collection<String> relValues,
                                    @Optional @Name("filter") TreeBean filter, IServiceContext context);

    // ==================== 树形结构操作 ====================

    /**
     * 查询树形结构的根节点列表
     * <p>
     * 适用于具有树形结构的实体（如组织机构、菜单、分类等）。返回所有根节点（没有父节点的节点）。
     *
     * @param query     查询条件，可以过滤根节点
     * @param selection 字段选择对象
     * @param context   服务上下文
     * @return 根节点列表
     */
    @BizQuery
    List<T> findRoots(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    /**
     * 分页查询树形结构
     * <p>
     * 返回树形结构的基本信息（id、parentId、name等），用于前端构建树。
     *
     * @param query     查询条件
     * @param selection 字段选择对象
     * @param context   服务上下文
     * @return 树形结构的分页结果，items为StdTreeEntity列表
     */
    @BizQuery
    PageBean<StdTreeEntity> findTreeEntityPage(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    /**
     * 查询树形结构
     *
     * @param query     查询条件
     * @param selection 字段选择对象
     * @param context   服务上下文
     * @return 树形结构列表，items为StdTreeEntity列表
     */
    @BizQuery
    List<StdTreeEntity> findTreeEntityList(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    /**
     * 查询树形结构并返回完整实体
     * <p>
     * 与findTreeEntityList的区别在于，此方法返回完整的实体对象。
     *
     * @param query     查询条件
     * @param selection 字段选择对象
     * @param context   服务上下文
     * @return 树形结构对应的实体列表
     */
    @BizQuery
    List<T> findListForTree(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    /**
     * 分页查询树形结构并返回完整实体
     *
     * @param query     查询条件
     * @param selection 字段选择对象
     * @param context   服务上下文
     * @return 树形结构实体的分页结果
     */
    @BizQuery
    PageBean<T> findPageForTree(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    // ==================== 逻辑删除操作 ====================

    /**
     * 分页查询已删除（逻辑删除）的记录
     * <p>
     * 只有配置了逻辑删除的实体才能使用此方法。查询已设置删除标记的记录。
     *
     * @param query     查询条件
     * @param selection 字段选择对象
     * @param context   服务上下文
     * @return 已删除记录的分页结果
     */
    @BizQuery
    PageBean<T> deleted_findPage(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

    /**
     * 获取已被逻辑删除的单条记录
     *
     * @param id            主键ID
     * @param ignoreUnknown 如果为true，找不到返回null；如果为false，找不到抛出异常
     * @param context       服务上下文
     * @return 已删除的记录
     */
    @BizQuery
    T deleted_get(@Name("id") String id, @Optional @Name("ignoreUnknown") boolean ignoreUnknown, IServiceContext context);

    /**
     * 恢复已删除（逻辑删除）的记录
     * <p>
     * 清除删除标记，使记录恢复正常状态。
     *
     * @param id      主键ID
     * @param context 服务上下文
     * @return 恢复后的记录
     */
    @BizQuery
    T recoverDeleted(@Name("id") String id, IServiceContext context);

    // ==================== 辅助方法 ====================

    /**
     * 新建一个实体对象
     * <p>
     * 创建一个空的实体对象，可以手动设置属性后调用saveEntity保存。
     *
     * @return 新建的实体对象，所有字段为默认值
     */
    T newEntity();

    /**
     * 获取对象的元数据
     * <p>
     * 返回对象的元数据信息，包括字段定义、验证规则、显示名称等。
     *
     * @return 对象元数据
     */
    IObjMeta getObjMeta();

    /**
     * 获取业务对象名称
     * <p>
     * 返回当前业务对象的唯一标识名称，用于权限控制、日志记录等场景。
     *
     * @return 业务对象名称
     */
    String getBizObjName();

    // ==================== 直接针对实体对象的操作方法 ====================

    /**
     * 删除实体对象
     * <p>
     * 与delete(String id)的区别在于，此方法直接传入实体对象，而不是ID。
     * 包含数据权限检查、关联引用检查、级联删除等逻辑。
     *
     * @param entity  要删除的实体对象
     * @param context 服务上下文
     */
    @BizAction
    void deleteEntity(@Name("entity") T entity, IServiceContext context);

    /**
     * 保存实体对象
     * <p>
     * 与save(Map data)的区别在于，此方法直接传入实体对象。
     * 包含数据权限检查、唯一性检查、状态机初始化等逻辑。
     *
     * @param entity  要保存的实体对象
     * @param context 服务上下文
     */
    @BizAction
    void saveEntity(@Name("entity") T entity, IServiceContext context);

    /**
     * 更新实体对象
     * <p>
     * 与update(Map data)的区别在于，此方法直接传入实体对象。
     * 包含数据权限检查、唯一性检查等逻辑。
     *
     * @param entity  要更新的实体对象
     * @param context 服务上下文
     */
    @BizAction
    void updateEntity(@Name("entity") T entity, IServiceContext context);

    /**
     * 给实体对象赋值
     * <p>
     * 将data中的属性值复制到entity对象上，支持复杂主子表数据的赋值。
     *
     * @param entity  目标实体对象
     * @param data    数据映射，key为字段名，value为字段值
     * @param context 服务上下文
     */
    @BizAction
    void assignToEntity(@Name("entity") T entity, @Name("data") Map<String, Object> data, IServiceContext context);

    /**
     * 根据传入的数据创建实体对象
     * <p>
     * 支持复杂主子表数据的构建，包含数据验证、逻辑删除恢复等逻辑。
     * 构建的实体对象可以被修改后再保存。
     *
     * @param data    数据映射
     * @param action  操作类型："save"表示新增，"update"表示修改
     * @param context 服务上下文
     * @return 构建好的实体对象
     */
    @BizAction
    T buildEntityForSave(@Name("data") Map<String, Object> data, @Name("action") String action, IServiceContext context);

    /**
     * 检查是否允许访问指定实体
     * <p>
     * 执行数据权限检查，如果当前用户没有权限访问该实体，会抛出异常。
     *
     * @param entity  要检查的实体对象
     * @param action  操作类型，如"get"、"update"、"delete"等
     * @param context 服务上下文
     * @throws io.nop.api.core.exceptions.NopException 如果没有权限访问该实体
     */
    @BizAction
    void checkAllowAccess(@Name("entity") T entity, @Name("action") String action, IServiceContext context);
}
