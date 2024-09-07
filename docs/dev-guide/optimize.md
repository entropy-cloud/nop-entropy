# Nop平台的优化配置


## 配置变量
开发模式下会执行很多动态检查，启动的时候也会自动校验所有模型。关闭开关后可以加快启动速度，并减少运行时获取缓存时的速度

* nop.core.component.resource-cache.check-changed: 配置为false，禁用动态修改检查。
  模型一旦加载到缓存中就不会自动失效，除非被主动删除。
* nop.web.validate-page-model: 配置为false，则启动的时候就不会检查所有page.yaml文件都会加载
