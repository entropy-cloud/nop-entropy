# 1. 注册Windows服务

将jar包重命名为nop-application.jar，放置到本目录下

执行 NopApplication.bat install注册服务

执行 NopApplication.bat uninstall 删除服务

注册服务后在Windows的服务列表中可以看到NopApplication服务。可以通过修改lib目录下的xml文件来修改服务名和jar包名称

# 2. 启动和停止

NopApplication.bat start 

NopApplication.bat stop

# 3. 测试
执行 StartNopApplication.bat用于在命令行窗口中测试服务启动