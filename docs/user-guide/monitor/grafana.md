# 运行时监控

Nop平台可以接入Prometheus和Grafana监控平台。下面以Windows平台为例，介绍如何在本机搭建一个简单的监控系统。

## 常用端口

* http://localhost:3000  grafana，用户名nop,密码test
* http://localhost:3100/ready  loki
* http://localhost:9090/targets  prometheus

## 下载监控软件

* prometheus: kubernetes内置的监控平台，负责采集监控指标数据按时间序列存储
  
  - 官网下载: https://prometheus.io/download/
  - 最新版本: https://github.com/prometheus/prometheus/releases/download/v2.44.0/prometheus-2.44.0.windows-amd64.zip

* windows\_exporter: 监控windows操作系统，向prometheus报送数据的监控进程
  
  - github下载： https://github.com/prometheus-community/windows\_exporter/releases
  - 最新版本: https://github.com/prometheus-community/windows\_exporter/releases/download/v0.22.0/windows\_exporter-0.22.0-amd64.msi

* grafana: 可视化展示和分析平台
  
  - 官网下载: https://grafana.com/grafana/download?platform=windows
  - 最新版本：https://dl.grafana.com/oss/release/grafana-10.0.1.windows-amd64.zip

* loki: 轻量级的日志存储和查询处理系统，可以替代ElasticSearch的作用
  
  - github下载: https://github.com/grafana/loki/releases
  - 最新版本： https://github.com/grafana/loki/releases/download/v2.8.2/loki-windows-amd64.exe.zip

* promtail: 与loki协同工作的日志采集代理，它负责收集日志并转发到loki进行存储。
  
  - github下载：  https://github.com/grafana/loki/releases
  - 最新版本：https://github.com/grafana/loki/releases/download/v2.8.2/promtail-windows-amd64.exe.zip

## 安装和配置软件

### 安装windows\_exporter

直接安装msi文件之后，可以通过http://localhost:9182/metrics查看对外暴露的指标

### 安装prometheus

在prometheus.yml配置文件中增加如下配置

```yaml
  - job_name: windows_exporter
    scrape_interval: 15s
    static_configs:
      - targets: [ "localhost:9182" ]
```

使用如下命令启动prometheus

```
prometheus --config.file="prometheus.yml" --web.enable-lifecycle
```

* web.enable-lifecycle表示启用热加载机制，可以通过 http://localhost:9090/-/reload来重新加载配置文件

* 访问 http://localhost:9090/targets 可以查看所有监控端点

### 安装loki服务器

[API文档](https://grafana.com/docs/loki/latest/api/)

* GET /config 读取所有配置项
* GET /ready 检查当前状态

增加loki-config.yaml配置文件

```yaml
auth_enabled: false

server:
  http_listen_port: 3100

ingester:
  lifecycler:
    address: 127.0.0.1
    ring:
      kvstore:
        store: inmemory
      replication_factor: 1
    final_sleep: 0s
  chunk_idle_period: 5m
  chunk_retain_period: 30s
  max_transfer_retries: 0

schema_config:
  configs:
    - from: 2023-06-22
      store: boltdb
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 168h

storage_config:
  boltdb:
    directory: ./data/loki/index

  filesystem:
    directory: ./data/loki/chunks

limits_config:
  enforce_metric_name: false
  reject_old_samples: true
  reject_old_samples_max_age: 168h

chunk_store_config:
  max_look_back_period: 0s

table_manager:
  retention_deletes_enabled: false
  retention_period: 0s
```

通过shell启动

```shell
loki-windows-amd64.exe --config.file="loki-config.yaml"
```

* 访问 http://localhost:3100/ready ，成功启动后返回ready

## 安装promtail

[配置参考](https://blog.frognew.com/2023/05/loki-04-promtail-intro-and-config-ref.html)

增加promtail-config.yaml配置文件：

```yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: ./data/positions.yaml

clients:
  - url: http://localhost:3100/loki/api/v1/push

scrape_configs:
- job_name: demo
  static_configs:
  - targets:
      - localhost
    labels:
      job: demologs
      __path__: C:\can\nop\nop-entropy\log\*.log

```

通过命令行启动

```shell
promtail-windows-amd64.exe -config.file="promtail-config.yaml"
```

> 通过如下指令获取调试诊断信息
> curl localhost:3100/debug/pprof/profile?seconds=20

### 安装grafana

将grafana的conf目录下的sample.ini文件复制为custom.ini，然后修改其中的登录用户名和密码，缺省为admin/admin。

```
[security]
# default admin user, created on startup
admin_user = nop

# default admin password, can be changed before first start of grafana,  or in profile settings
admin_password = test
```

在grafana/bin目录下通过如下指令启动服务

```shell
grafana-server --config="../conf/custom.ini"
```

* 访问http://localhost:3000，通过用户名 nop，密码test登录

> 首次启动后初始化用户名和密码就保存到了grafana/data目录下，修改ini文件中的admin\_user等配置不再起作用。

#### 在 Grafana 中添加 Loki 数据源

在使用 Grafana 进行可视化管理之前，您需要将 Loki 数据源添加到 Grafana。请按照以下步骤操作：

- 打开 Grafana UI，登录并单击“配置”按钮（位于左侧菜单中）。
- 在“数据源”下单击“添加数据源”。
- 选择“Loki”作为数据源类型。
- 输入 Loki API 地址及其相关详细信息。在本例中，Loki API 的地址为 http://localhost:3100。
- 单击“保存并测试”以测试数据源是否正常工作。

#### 创建 Grafana Dashboard

- 打开 Grafana UI 并单击“+”按钮（左侧菜单中）。
- 单击“Dashboard”选项。
- 单击“Add new panel”以添加一个新的 Panel。
- 选择您要显示的指标（例如，请选择“Logs”指标以显示来自 Loki 的日志数据）。
- 根据需要调整其他设置，并保存 Dashboard。

## 使用docker-compose安装

除了手动安装之外，也可以通过docker-compose自动安装grafana, loki和promtail

```
wget https://raw.githubusercontent.com/grafana/loki/v2.8.0/production/docker-compose.yaml -O docker-compose.yaml
docker-compose -f docker-compose.yaml up
```

## 问题诊断

* Loki: 启动时提示 panic: invalid page type: 11:10
  原因: 对应的 index table 文件已经损坏
  解决: 删除相应的 index 文件即可解决

* Loki: file size too small\\nerror creating index client
  解决: 删除 loki 的持久化目录下的 boltdb-shipper-active/index\_18xxx 目录

* promtail: context deadline exceeded
  原因: promtail 无法连接 loki 所致
