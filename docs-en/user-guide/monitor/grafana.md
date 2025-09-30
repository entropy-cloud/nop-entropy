# Runtime Monitoring

The Nop platform can integrate with Prometheus and Grafana monitoring platforms. The following uses Windows as an example to demonstrate how to set up a simple local monitoring system.

## Common ports

* http://localhost:3000  Grafana, username nop, password test
* http://localhost:3100/ready  Loki
* http://localhost:9090/targets  Prometheus

## Download monitoring software

* Prometheus: Kubernetes’ built-in monitoring platform, responsible for collecting monitoring metric data and storing it as time series
  
  - Official download: https://prometheus.io/download/
  - Latest version: https://github.com/prometheus/prometheus/releases/download/v2.44.0/prometheus-2.44.0.windows-amd64.zip

* windows\_exporter: Monitors the Windows operating system and reports data to Prometheus
  
  - GitHub download: https://github.com/prometheus-community/windows_exporter/releases
  - Latest version: https://github.com/prometheus-community/windows_exporter/releases/download/v0.22.0/windows_exporter-0.22.0-amd64.msi

* Grafana: Visualization and analytics platform
  
  - Official download: https://grafana.com/grafana/download?platform=windows
  - Latest version: https://dl.grafana.com/oss/release/grafana-10.0.1.windows-amd64.zip

* Loki: A lightweight log storage and query processing system that can replace the role of Elasticsearch
  
  - GitHub download: https://github.com/grafana/loki/releases
  - Latest version: https://github.com/grafana/loki/releases/download/v2.8.2/loki-windows-amd64.exe.zip

* Promtail: A log collection agent that works with Loki; it collects logs and forwards them to Loki for storage.
  
  - GitHub download:  https://github.com/grafana/loki/releases
  - Latest version: https://github.com/grafana/loki/releases/download/v2.8.2/promtail-windows-amd64.exe.zip

## Install and configure software

### Install windows\_exporter

After installing the MSI directly, you can view the exposed metrics at http://localhost:9182/metrics

### Install Prometheus

Add the following configuration to the prometheus.yml file

```yaml
  - job_name: windows_exporter
    scrape_interval: 15s
    static_configs:
      - targets: [ "localhost:9182" ]
```

Start Prometheus with the following command

```
prometheus --config.file="prometheus.yml" --web.enable-lifecycle
```

* web.enable-lifecycle enables hot reload; you can reload the configuration via http://localhost:9090/-/reload

* Visit http://localhost:9090/targets to see all monitoring targets

### Install the Loki server

[API documentation](https://grafana.com/docs/loki/latest/api/)

* GET /config Retrieve all configuration items
* GET /ready Check current status

Add a loki-config.yaml configuration file

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

Start via shell

```shell
loki-windows-amd64.exe --config.file="loki-config.yaml"
```

* Visit http://localhost:3100/ready; after starting successfully it returns ready

## Install Promtail

[Configuration reference](https://blog.frognew.com/2023/05/loki-04-promtail-intro-and-config-ref.html)

Add a promtail-config.yaml configuration file:

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

Start via command line

```shell
promtail-windows-amd64.exe -config.file="promtail-config.yaml"
```

> Use the following command to obtain debugging diagnostics
> curl localhost:3100/debug/pprof/profile?seconds=20

### Install Grafana

Copy sample.ini in Grafana’s conf directory to custom.ini, then modify the login username and password; the default is admin/admin.

```
[security]
# default admin user, created on startup
admin_user = nop

# default admin password, can be changed before first start of grafana,  or in profile settings
admin_password = test
```

In the grafana/bin directory, start the service with the following command

```shell
grafana-server --config="../conf/custom.ini"
```

* Visit http://localhost:3000 and log in with username nop and password test

> After the first startup, the initialized username and password are saved under grafana/data; modifying admin_user and other settings in the ini file will no longer take effect.

#### Add a Loki data source in Grafana

Before using Grafana for visualization, you need to add the Loki data source to Grafana. Follow these steps:

- Open the Grafana UI, log in, and click the “Configuration” button (located in the left-side menu).
- Under “Data sources,” click “Add data source.”
- Select “Loki” as the data source type.
- Enter the Loki API address and related details. In this case, the Loki API address is http://localhost:3100.
- Click “Save & test” to verify that the data source is working.

#### Create a Grafana Dashboard

- Open the Grafana UI and click the “+” button (in the left-side menu).
- Click the “Dashboard” option.
- Click “Add new panel” to add a new panel.
- Choose the metric you want to display (for example, select the “Logs” metric to display log data from Loki).
- Adjust other settings as needed, and save the Dashboard.

## Install using docker-compose

In addition to manual installation, you can also use docker-compose to automatically install Grafana, Loki, and Promtail

```
wget https://raw.githubusercontent.com/grafana/loki/v2.8.0/production/docker-compose.yaml -O docker-compose.yaml
docker-compose -f docker-compose.yaml up
```

## Troubleshooting

* Loki: On startup shows panic: invalid page type: 11:10
  Cause: The corresponding index table file is corrupted
  Solution: Delete the corresponding index file

* Loki: file size too small\nerror creating index client
  Solution: Delete the boltdb-shipper-active/index_18xxx directory under Loki’s persistent storage directory

* promtail: context deadline exceeded
  Cause: promtail cannot connect to Loki

<!-- SOURCE_MD5:7bde635c68d334fa778a7b2bb533d578-->
