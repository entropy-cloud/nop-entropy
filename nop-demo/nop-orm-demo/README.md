# 说明
本目录的内容来自网友xyplayman的[nop-orm-demo项目](https://gitee.com/xyplayman/nop-orm-demo)，感谢xyplayman的贡献。

# 概述

nop-orm-demo 项目用于演示 [NOP](https://gitee.com/canonical-entropy/nop-entropy) 平台中 nop-orm 模块的使用。

示例主要取材于 《Database System Concepts》一书的配套网站，www.db-book.com，包括 `Slides` 和 `Practice Exercises`，并做了一定的调整。

# 使用方法

用 IntelliJ IDEA 打开项目，直接运行其中的测试用例即可。

如果需要修改数据库模型，首先在 app.orm.xml 中调整模型，然后运行 OrmCodeGen 重新生成实体代码。