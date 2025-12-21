package io.nop.dbtoo.exp;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.IResource;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.batch.exp.ImportDbTool;
import io.nop.batch.exp.config.ImportDbConfig;
import io.nop.xlang.xdsl.DslModelHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

@NopTestConfig(localDb = true)
public class TestImportDbTool extends JunitBaseTestCase {
    @Inject
    DataSource dataSource;

    @Inject
    IJdbcTemplate jdbcTemplate;

    @Test
    public void testImport() {
        initData();

        ImportDbTool tool = new ImportDbTool();
        tool.setDataSource(dataSource);
        IResource resource = classpathResource("config/test.import-db.xml");
        ImportDbConfig config = (ImportDbConfig) DslModelHelper.loadDslModel(resource);
        config.setInputDir(getTestResourceFile("data").getAbsolutePath());
        tool.setConfig(config);
        tool.execute();
    }

    void initData() {
        String sql = "create table sys_menu (\n" +
                "  menu_id           bigint      not null   comment '菜单ID',\n" +
                "  menu_name         varchar(50)     not null                   comment '菜单名称',\n" +
                "  parent_id         bigint      default 0                  comment '父菜单ID',\n" +
                "  order_num         int          default 0                  comment '显示顺序',\n" +
                "  path              varchar(200)    default ''                 comment '路由地址',\n" +
                "  component         varchar(255)    default null               comment '组件路径',\n" +
                "  query             varchar(255)    default null               comment '路由参数',\n" +
                "  is_frame          int          default 1                  comment '是否为外链（0是 1否）',\n" +
                "  is_cache          int          default 0                  comment '是否缓存（0缓存 1不缓存）',\n" +
                "  menu_type         char(1)         default ''                 comment '菜单类型（M目录 C菜单 F按钮）',\n" +
                "  visible           char(1)         default 0                  comment '菜单状态（0显示 1隐藏）',\n" +
                "  status            char(1)         default 0                  comment '菜单状态（0正常 1停用）',\n" +
                "  perms             varchar(100)    default null               comment '权限标识',\n" +
                "  icon              varchar(100)    default '#'                comment '菜单图标',\n" +
                "  create_by         varchar(64)     default ''                 comment '创建者',\n" +
                "  create_time       timestamp                                   comment '创建时间',\n" +
                "  update_by         varchar(64)     default ''                 comment '更新者',\n" +
                "  update_time       timestamp                                   comment '更新时间',\n" +
                "  remark            varchar(500)    default ''                 comment '备注',\n" +
                "  constraint PK_SYS_MENU primary key (menu_id)\n" +
                ")";

        jdbcTemplate.executeUpdate(new SQL(sql));
    }
}