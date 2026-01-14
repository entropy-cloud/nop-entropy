# NopSys 项目示例

## 概述

本文档提供NopSys项目的完整实现示例，展示如何使用Nop Platform构建系统配置管理模块，包括字典管理、菜单管理、参数配置等功能。

## 项目结构

```
nop-sys/
├── nop-sys.orm.xml             # 数据库模型定义（model目录使用模块名）
├── nop-sys.api.xml             # API模型定义
├── src/
│   ├── main/java/io/nop/sys/
│   │   ├── dao/                # DAO接口
│   │   ├── domain/             # 实体类
│   │   ├── service/            # 服务实现
│   │   │   ├── NopSysDictBizModel.java       # 字典业务模型
│   │   │   ├── NopSysDictItemBizModel.java   # 字典项业务模型
│   │   │   ├── NopSysMenuBizModel.java       # 菜单业务模型
│   │   │   └── NopSysConfigBizModel.java      # 配置业务模型
│   │   └── web/                # Web控制器
│   └── main/resources/
│       └── _vfs/               # 虚拟文件系统（_vfs目录下使用app.orm.xml等命名）
│           ├── app/            # 应用配置
│           │   └── app.orm.xml # ORM模型定义
│           ├── biz/            # 业务模型
│           ├── graphql/        # GraphQL定义
│           └── xlib/           # 扩展库
└── pom.xml                      # Maven配置
```

## 数据库模型设计

### 1. 字典表 (nop_sys_dict)

```xml
<entity name="NopSysDict" table="nop_sys_dict">
  <field name="dictId" type="string" primary="true" length="32" />
  <field name="dictCode" type="string" required="true" length="50" unique="true" />
  <field name="dictName" type="string" required="true" length="100" />
  <field name="dictType" type="string" length="20" />
  <field name="description" type="string" length="500" />
  <field name="status" type="int" required="true" defaultValue="1" />
  <field name="sortOrder" type="int" defaultValue="0" />
  <field name="createTime" type="datetime" />
  <field name="updateTime" type="datetime" />

  <relation name="items" type="one-to-many" target="NopSysDictItem" inverse="dict" />
</entity>
```

### 2. 字典项表 (nop_sys_dict_item)

```xml
<entity name="NopSysDictItem" table="nop_sys_dict_item">
  <field name="itemId" type="string" primary="true" length="32" />
  <field name="dictId" type="string" length="32" />
  <field name="itemCode" type="string" required="true" length="50" />
  <field name="itemName" type="string" required="true" length="100" />
  <field name="itemValue" type="string" length="200" />
  <field name="itemLabel" type="string" length="100" />
  <field name="cssClass" type="string" length="50" />
  <field name="status" type="int" required="true" defaultValue="1" />
  <field name="sortOrder" type="int" defaultValue="0" />
  <field name="createTime" type="datetime" />

  <relation name="dict" type="many-to-one" target="NopSysDict" />
</entity>
```

### 3. 菜单表 (nop_sys_menu)

```xml
<entity name="NopSysMenu" table="nop_sys_menu">
  <field name="menuId" type="string" primary="true" length="32" />
  <field name="parentId" type="string" length="32" />
  <field name="menuName" type="string" required="true" length="100" />
  <field name="menuCode" type="string" required="true" length="100" />
  <field name="menuType" type="string" length="20" />
  <field name="icon" type="string" length="50" />
  <field name="path" type="string" length="200" />
  <field name="component" type="string" length="200" />
  <field name="permission" type="string" length="100" />
  <field name="status" type="int" required="true" defaultValue="1" />
  <field name="visible" type="int" defaultValue="1" />
  <field name="sortOrder" type="int" defaultValue="0" />
  <field name="createTime" type="datetime" />
  <field name="updateTime" type="datetime" />

  <relation name="parent" type="many-to-one" target="NopSysMenu" />
  <relation name="children" type="one-to-many" target="NopSysMenu" inverse="parent" />
</entity>
```

### 4. 配置表 (nop_sys_config)

```xml
<entity name="NopSysConfig" table="nop_sys_config">
  <field name="configId" type="string" primary="true" length="32" />
  <field name="configKey" type="string" required="true" length="100" unique="true" />
  <field name="configValue" type="string" length="500" />
  <field name="configType" type="string" length="20" />
  <field name="configGroup" type="string" length="50" />
  <field name="configName" type="string" required="true" length="100" />
  <field name="description" type="string" length="500" />
  <field name="isSystem" type="int" defaultValue="0" />
  <field name="status" type="int" required="true" defaultValue="1" />
  <field name="createTime" type="datetime" />
  <field name="updateTime" type="datetime" />
</entity>
```

## 字典管理实现

### 1. 字典业务模型

```java
package io.nop.sys.service;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.orm.model.OrmEntityModel;
import io.nop.service.crud.CrudBizModel;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;

import static io.nop.sys.SysErrors.*;

@BizModel("NopSysDict")
public class NopSysDictBizModel extends CrudBizModel<NopSysDict> {

    public NopSysDictBizModel() {
        setEntityName(NopSysDict.class.getName());
    }

    @Inject
    private IOrmEntityDao<NopSysDictItem> dictItemDao;

    /**
     * 创建字典
     */
    @BizMutation
    @Transactional
    public NopSysDict createDict(@Name("dict") NopSysDict dict) {
        // 验证字典编码唯一性
        if (isDictCodeExists(dict.getDictCode())) {
            throw new NopException(ERR_SYS_DICT_CODE_EXISTS)
                .param(ARG_DICT_CODE, dict.getDictCode());
        }

        // 设置默认值
        if (dict.getDictId() == null) {
            dict.setDictId(StringHelper.generateUUID());
        }
        if (dict.getStatus() == null) {
            dict.setStatus(1);
        }
        if (dict.getSortOrder() == null) {
            dict.setSortOrder(0);
        }
        dict.setCreateTime(new Date());
        dict.setUpdateTime(new Date());

        return save(dict);
    }

    /**
     * 更新字典
     */
    @BizMutation
    @Transactional
    public NopSysDict updateDict(@Name("dictId") String dictId,
                                   @Name("dict") NopSysDict dict) {
        NopSysDict existing = dao().getEntityById(dictId);
        if (existing == null) {
            throw new NopException(ERR_SYS_DICT_NOT_FOUND)
                .param(ARG_DICT_ID, dictId);
        }

        // 更新字段
        if (dict.getDictCode() != null) {
            existing.setDictCode(dict.getDictCode());
        }
        if (dict.getDictName() != null) {
            existing.setDictName(dict.getDictName());
        }
        if (dict.getDictType() != null) {
            existing.setDictType(dict.getDictType());
        }
        if (dict.getDescription() != null) {
            existing.setDescription(dict.getDescription());
        }
        if (dict.getStatus() != null) {
            existing.setStatus(dict.getStatus());
        }
        if (dict.getSortOrder() != null) {
            existing.setSortOrder(dict.getSortOrder());
        }
        existing.setUpdateTime(new Date());

        return dao().updateEntity(existing);
    }

    /**
     * 删除字典
     */
    @BizMutation
    @Transactional
    public void deleteDict(@Name("dictId") String dictId) {
        NopSysDict dict = dao().getEntityById(dictId);
        if (dict == null) {
            throw new NopException(ERR_SYS_DICT_NOT_FOUND)
                .param(ARG_DICT_ID, dictId);
        }

        // 删除字典项
        dao().deleteByFilter(
            NopSysDictItem.PROP_NAME_dictId,
            FilterBean.eq(dictId)
        );

        // 删除字典
        dao().deleteEntity(dict);
    }

    /**
     * 获取字典项
     */
    @BizQuery
    public List<NopSysDictItem> getDictItems(@Name("dictId") String dictId) {
        NopSysDict dict = dao().getEntityById(dictId);
        if (dict == null) {
            throw new NopException(ERR_SYS_DICT_NOT_FOUND)
                .param(ARG_DICT_ID, dictId);
        }

        return dict.getItems();
    }

    /**
     * 根据编码获取字典
     */
    @BizQuery
    public NopSysDict getDictByCode(@Name("dictCode") String dictCode) {
        return dao().findFirst(
            FilterBean.eq(NopSysDict.PROP_NAME_dictCode, dictCode)
        );
    }

    /**
     * 根据编码获取字典项
     */
    @BizQuery
    public List<NopSysDictItem> getDictItemsByCode(@Name("dictCode") String dictCode) {
        NopSysDict dict = getDictByCode(dictCode);
        if (dict == null) {
            return List.of();
        }

        return dict.getItems();
    }

    /**
     * 检查字典编码是否存在
     */
    private boolean isDictCodeExists(String dictCode) {
        return dao().exists(
            FilterBean.eq(NopSysDict.PROP_NAME_dictCode, dictCode)
        );
    }
}
```

### 2. 字典项业务模型

```java
package io.nop.sys.service;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.service.crud.CrudBizModel;
import jakarta.inject.Inject;
import java.util.Date;

import static io.nop.sys.SysErrors.*;

@BizModel("NopSysDictItem")
public class NopSysDictItemBizModel extends CrudBizModel<NopSysDictItem> {

    public NopSysDictItemBizModel() {
        setEntityName(NopSysDictItem.class.getName());
    }

    @Inject
    private IOrmEntityDao<NopSysDict> dictDao;

    /**
     * 创建字典项
     */
    @BizMutation
    @Transactional
    public NopSysDictItem createDictItem(@Name("item") NopSysDictItem item) {
        // 验证字典存在
        NopSysDict dict = dictDao.getEntityById(item.getDictId());
        if (dict == null) {
            throw new NopException(ERR_SYS_DICT_NOT_FOUND)
                .param(ARG_DICT_ID, item.getDictId());
        }

        // 验证字典项编码唯一性
        if (isItemCodeExists(item.getDictId(), item.getItemCode())) {
            throw new NopException(ERR_SYS_DICT_ITEM_CODE_EXISTS)
                .param(ARG_DICT_ID, item.getDictId())
                .param(ARG_ITEM_CODE, item.getItemCode());
        }

        // 设置默认值
        if (item.getItemId() == null) {
            item.setItemId(StringHelper.generateUUID());
        }
        if (item.getStatus() == null) {
            item.setStatus(1);
        }
        if (item.getSortOrder() == null) {
            item.setSortOrder(0);
        }
        item.setCreateTime(new Date());

        return save(item);
    }

    /**
     * 更新字典项
     */
    @BizMutation
    @Transactional
    public NopSysDictItem updateDictItem(@Name("itemId") String itemId,
                                          @Name("item") NopSysDictItem item) {
        NopSysDictItem existing = dao().getEntityById(itemId);
        if (existing == null) {
            throw new NopException(ERR_SYS_DICT_ITEM_NOT_FOUND)
                .param(ARG_ITEM_ID, itemId);
        }

        // 更新字段
        if (item.getItemCode() != null) {
            existing.setItemCode(item.getItemCode());
        }
        if (item.getItemName() != null) {
            existing.setItemName(item.getItemName());
        }
        if (item.getItemValue() != null) {
            existing.setItemValue(item.getItemValue());
        }
        if (item.getItemLabel() != null) {
            existing.setItemLabel(item.getItemLabel());
        }
        if (item.getCssClass() != null) {
            existing.setCssClass(item.getCssClass());
        }
        if (item.getStatus() != null) {
            existing.setStatus(item.getStatus());
        }
        if (item.getSortOrder() != null) {
            existing.setSortOrder(item.getSortOrder());
        }

        return dao().updateEntity(existing);
    }

    /**
     * 删除字典项
     */
    @BizMutation
    @Transactional
    public void deleteDictItem(@Name("itemId") String itemId) {
        NopSysDictItem item = dao().getEntityById(itemId);
        if (item == null) {
            throw new NopException(ERR_SYS_DICT_ITEM_NOT_FOUND)
                .param(ARG_ITEM_ID, itemId);
        }

        dao().deleteEntity(item);
    }

    /**
     * 检查字典项编码是否存在
     */
    private boolean isItemCodeExists(String dictId, String itemCode) {
        return dao().exists(
            FilterBean.and(
                FilterBean.eq(NopSysDictItem.PROP_NAME_dictId, dictId),
                FilterBean.eq(NopSysDictItem.PROP_NAME_itemCode, itemCode)
            )
        );
    }
}
```

## 菜单管理实现

### 1. 菜单业务模型

```java
package io.nop.sys.service;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.orm.model.OrmEntityModel;
import io.nop.service.crud.CrudBizModel;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;

import static io.nop.sys.SysErrors.*;

@BizModel("NopSysMenu")
public class NopSysMenuBizModel extends CrudBizModel<NopSysMenu> {

    public NopSysMenuBizModel() {
        setEntityName(NopSysMenu.class.getName());
    }

    /**
     * 创建菜单
     */
    @BizMutation
    @Transactional
    public NopSysMenu createMenu(@Name("menu") NopSysMenu menu) {
        // 验证父菜单存在
        if (menu.getParentId() != null) {
            NopSysMenu parent = dao().getEntityById(menu.getParentId());
            if (parent == null) {
                throw new NopException(ERR_SYS_MENU_PARENT_NOT_FOUND)
                    .param(ARG_MENU_ID, menu.getParentId());
            }
        }

        // 验证菜单编码唯一性
        if (isMenuCodeExists(menu.getMenuCode())) {
            throw new NopException(ERR_SYS_MENU_CODE_EXISTS)
                .param(ARG_MENU_CODE, menu.getMenuCode());
        }

        // 设置默认值
        if (menu.getMenuId() == null) {
            menu.setMenuId(StringHelper.generateUUID());
        }
        if (menu.getStatus() == null) {
            menu.setStatus(1);
        }
        if (menu.getVisible() == null) {
            menu.setVisible(1);
        }
        if (menu.getSortOrder() == null) {
            menu.setSortOrder(0);
        }
        menu.setCreateTime(new Date());
        menu.setUpdateTime(new Date());

        return save(menu);
    }

    /**
     * 更新菜单
     */
    @BizMutation
    @Transactional
    public NopSysMenu updateMenu(@Name("menuId") String menuId,
                                   @Name("menu") NopSysMenu menu) {
        NopSysMenu existing = dao().getEntityById(menuId);
        if (existing == null) {
            throw new NopException(ERR_SYS_MENU_NOT_FOUND)
                .param(ARG_MENU_ID, menuId);
        }

        // 验证父菜单存在
        if (menu.getParentId() != null) {
            NopSysMenu parent = dao().getEntityById(menu.getParentId());
            if (parent == null) {
                throw new NopException(ERR_SYS_MENU_PARENT_NOT_FOUND)
                    .param(ARG_MENU_ID, menu.getParentId());
            }
        }

        // 更新字段
        if (menu.getParentId() != null) {
            existing.setParentId(menu.getParentId());
        }
        if (menu.getMenuName() != null) {
            existing.setMenuName(menu.getMenuName());
        }
        if (menu.getMenuCode() != null) {
            existing.setMenuCode(menu.getMenuCode());
        }
        if (menu.getMenuType() != null) {
            existing.setMenuType(menu.getMenuType());
        }
        if (menu.getIcon() != null) {
            existing.setIcon(menu.getIcon());
        }
        if (menu.getPath() != null) {
            existing.setPath(menu.getPath());
        }
        if (menu.getComponent() != null) {
            existing.setComponent(menu.getComponent());
        }
        if (menu.getPermission() != null) {
            existing.setPermission(menu.getPermission());
        }
        if (menu.getStatus() != null) {
            existing.setStatus(menu.getStatus());
        }
        if (menu.getVisible() != null) {
            existing.setVisible(menu.getVisible());
        }
        if (menu.getSortOrder() != null) {
            existing.setSortOrder(menu.getSortOrder());
        }
        existing.setUpdateTime(new Date());

        return dao().updateEntity(existing);
    }

    /**
     * 删除菜单
     */
    @BizMutation
    @Transactional
    public void deleteMenu(@Name("menuId") String menuId) {
        NopSysMenu menu = dao().getEntityById(menuId);
        if (menu == null) {
            throw new NopException(ERR_SYS_MENU_NOT_FOUND)
                .param(ARG_MENU_ID, menuId);
        }

        // 检查是否有子菜单
        if (hasChildren(menuId)) {
            throw new NopException(ERR_SYS_MENU_HAS_CHILDREN)
                .param(ARG_MENU_ID, menuId);
        }

        // 删除菜单
        dao().deleteEntity(menu);
    }

    /**
     * 获取菜单树
     */
    @BizQuery
    public List<NopSysMenu> getMenuTree(@Name("parentId") String parentId) {
        if (parentId == null || parentId.isEmpty()) {
            // 获取顶级菜单
            return dao().findAll(
                FilterBean.and(
                    FilterBean.isNull(NopSysMenu.PROP_NAME_parentId),
                    FilterBean.eq(NopSysMenu.PROP_NAME_status, 1),
                    FilterBean.eq(NopSysMenu.PROP_NAME_visible, 1)
                ),
                OrderBean.asc(NopSysMenu.PROP_NAME_sortOrder)
            );
        } else {
            // 获取子菜单
            return dao().findAll(
                FilterBean.and(
                    FilterBean.eq(NopSysMenu.PROP_NAME_parentId, parentId),
                    FilterBean.eq(NopSysMenu.PROP_NAME_status, 1),
                    FilterBean.eq(NopSysMenu.PROP_NAME_visible, 1)
                ),
                OrderBean.asc(NopSysMenu.PROP_NAME_sortOrder)
            );
        }
    }

    /**
     * 检查菜单编码是否存在
     */
    private boolean isMenuCodeExists(String menuCode) {
        return dao().exists(
            FilterBean.eq(NopSysMenu.PROP_NAME_menuCode, menuCode)
        );
    }

    /**
     * 检查是否有子菜单
     */
    private boolean hasChildren(String menuId) {
        return dao().exists(
            FilterBean.eq(NopSysMenu.PROP_NAME_parentId, menuId)
        );
    }
}
```

## 配置管理实现

### 1. 配置业务模型

```java
package io.nop.sys.service;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.service.crud.CrudBizModel;
import jakarta.inject.Inject;
import java.util.Date;
import java.util.List;

import static io.nop.sys.SysErrors.*;

@BizModel("NopSysConfig")
public class NopSysConfigBizModel extends CrudBizModel<NopSysConfig> {

    public NopSysConfigBizModel() {
        setEntityName(NopSysConfig.class.getName());
    }

    /**
     * 创建配置
     */
    @BizMutation
    @Transactional
    public NopSysConfig createConfig(@Name("config") NopSysConfig config) {
        // 验证配置键唯一性
        if (isConfigKeyExists(config.getConfigKey())) {
            throw new NopException(ERR_SYS_CONFIG_KEY_EXISTS)
                .param(ARG_CONFIG_KEY, config.getConfigKey());
        }

        // 设置默认值
        if (config.getConfigId() == null) {
            config.setConfigId(StringHelper.generateUUID());
        }
        if (config.getStatus() == null) {
            config.setStatus(1);
        }
        if (config.getIsSystem() == null) {
            config.setIsSystem(0);
        }
        config.setCreateTime(new Date());
        config.setUpdateTime(new Date());

        return save(config);
    }

    /**
     * 更新配置
     */
    @BizMutation
    @Transactional
    public NopSysConfig updateConfig(@Name("configId") String configId,
                                      @Name("config") NopSysConfig config) {
        NopSysConfig existing = dao().getEntityById(configId);
        if (existing == null) {
            throw new NopException(ERR_SYS_CONFIG_NOT_FOUND)
                .param(ARG_CONFIG_ID, configId);
        }

        // 检查是否为系统配置
        if (existing.getIsSystem() == 1) {
            throw new NopException(ERR_SYS_CONFIG_IS_SYSTEM)
                .param(ARG_CONFIG_ID, configId);
        }

        // 更新字段
        if (config.getConfigValue() != null) {
            existing.setConfigValue(config.getConfigValue());
        }
        if (config.getConfigType() != null) {
            existing.setConfigType(config.getConfigType());
        }
        if (config.getConfigGroup() != null) {
            existing.setConfigGroup(config.getConfigGroup());
        }
        if (config.getConfigName() != null) {
            existing.setConfigName(config.getConfigName());
        }
        if (config.getDescription() != null) {
            existing.setDescription(config.getDescription());
        }
        if (config.getStatus() != null) {
            existing.setStatus(config.getStatus());
        }
        existing.setUpdateTime(new Date());

        return dao().updateEntity(existing);
    }

    /**
     * 删除配置
     */
    @BizMutation
    @Transactional
    public void deleteConfig(@Name("configId") String configId) {
        NopSysConfig config = dao().getEntityById(configId);
        if (config == null) {
            throw new NopException(ERR_SYS_CONFIG_NOT_FOUND)
                .param(ARG_CONFIG_ID, configId);
        }

        // 检查是否为系统配置
        if (config.getIsSystem() == 1) {
            throw new NopException(ERR_SYS_CONFIG_IS_SYSTEM)
                .param(ARG_CONFIG_ID, configId);
        }

        // 删除配置
        dao().deleteEntity(config);
    }

    /**
     * 根据键获取配置
     */
    @BizQuery
    public NopSysConfig getConfigByKey(@Name("configKey") String configKey) {
        return dao().findFirst(
            FilterBean.eq(NopSysConfig.PROP_NAME_configKey, configKey)
        );
    }

    /**
     * 根据键获取配置值
     */
    @BizQuery
    public String getConfigValue(@Name("configKey") String configKey) {
        NopSysConfig config = getConfigByKey(configKey);
        return config != null ? config.getConfigValue() : null;
    }

    /**
     * 根据分组获取配置
     */
    @BizQuery
    public List<NopSysConfig> getConfigByGroup(@Name("configGroup") String configGroup) {
        return dao().findAll(
            FilterBean.eq(NopSysConfig.PROP_NAME_configGroup, configGroup)
        );
    }

    /**
     * 检查配置键是否存在
     */
    private boolean isConfigKeyExists(String configKey) {
        return dao().exists(
            FilterBean.eq(NopSysConfig.PROP_NAME_configKey, configKey)
        );
    }
}
```

## 最佳实践

1. **字典管理**: 使用字典统一管理枚举值和状态值
2. **菜单树**: 使用递归查询构建菜单树结构
3. **配置缓存**: 使用缓存提高配置读取性能
4. **系统配置**: 标记系统配置，防止误删
5. **配置分组**: 使用配置分组进行分类管理
6. **权限控制**: 对敏感配置进行权限控制
7. **审计日志**: 记录配置变更的审计日志

## 总结

NopSys项目展示了如何使用Nop Platform构建系统配置管理模块：

1. **字典管理**: 系统字典和字典项的管理
2. **菜单管理**: 树形结构的菜单管理
3. **配置管理**: 系统参数配置的管理
4. **业务模型**: 使用BizModel封装业务逻辑
5. **GraphQL API**: 自动生成GraphQL查询和变更

遵循这些模式，可以快速构建灵活、可维护的系统配置管理模块。

## 相关文档

- [服务层开发指南](../getting-started/service/service-layer-development.md)
- [IEntityDao使用指南](../getting-started/dao/entitydao-usage.md)
- [GraphQL服务开发指南](../getting-started/api/graphql-guide.md)
- [事务管理指南](../getting-started/core/transaction-guide.md)
- [异常处理指南](../getting-started/core/exception-guide.md)

---

**文档版本**: 1.0
**最后更新**: 2025-01-09
**作者**: AI Assistant (Sisyphus)
