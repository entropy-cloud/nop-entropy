# 运行时监控

Nop平台可以接入Prometheus和Grafana监控平台。下面以Windows平台为例，介绍如何在本机搭建一个简单的监控系统。

## 下载监控软件

* prometheus: kubernetes内置的监控平台，负责采集监控指标数据按时间序列存储
  - 官网下载: https://prometheus.io/download/
  - 最新版本: https://github.com/prometheus/prometheus/releases/download/v2.44.0/prometheus-2.44.0.windows-amd64.zip

* windows_exporter: 监控windows操作系统，向prometheus报送数据的监控进程
  - github下载： https://github.com/prometheus-community/windows_exporter/releases
  - 最新版本: https://github.com/prometheus-community/windows_exporter/releases/download/v0.22.0/windows_exporter-0.22.0-amd64.msi

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

### 安装windows_exporter
  直接安装msi文件之后，可以通过http://localhost:9182/metrics查看对外暴露的指标

### 安装prometheus
  在prometheus.yml配置文件中增加如下配置

````yaml
  - job_name: windows_exporter
    scrape_interval: 15s
    static_configs:
      - targets: [ "localhost:9182" ]
````

使用如下命令启动prometheus

````
prometheus --config.file="prometheus.yml" --web.enable-lifecycle
````

* web.enable-lifecycle表示启用热加载机制，可以通过 http://localhost:9090/-/reload来重新加载配置文件

* 访问 http://localhost:9090/targets 可以查看所有监控端点

### 安装grafana


### 安装loki服务器
