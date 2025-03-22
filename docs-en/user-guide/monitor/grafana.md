# Runtime Monitoring

The NOP platform can integrate with the Prometheus and Grafana monitoring platforms. Below is an example of how to set up a simple monitoring system on the Windows platform.

## Common Ports

- `http://localhost:3000` - Grafana
  - Username: nop
  - Password: test
- `http://localhost:3100/ready` - Loki
- `http://localhost:9090/targets` - Prometheus

## Download Monitoring Software

- **Prometheus**: A monitoring platform built into Kubernetes for collecting time-series data.
  - Official download: [https://prometheus.io/download/](https://prometheus.io/download/)
  - Latest version: [https://github.com/prometheus/prometheus/releases/download/v2.44.0/prometheus-2.44.0.windows-amd64.zip](https://github.com/prometheus/prometheus/releases/download/v2.44.0/prometheus-2.44.0.windows-amd64.zip)

- **Windows Exporter**: A monitoring component for Windows systems that exports data to Prometheus.
  - GitHub download: [https://github.com/prometheus-community/windows_exporter/releases](https://github.com/prometheus-community/windows_exporter/releases)
  - Latest version: [https://github.com/prometheus-community/windows_exporter/releases/download/v0.22.0/windows_exporter-0.22.0-amd64.msi](https://github.com/prometheus-community/windows_exporter/releases/download/v0.22.0/windows_exporter-0.22.0-amd64.msi)

- **Grafana**: A visualization and analytics platform.
  - Official download: [https://grafana.com/grafana/download?platform=windows](https://grafana.com/grafana/download?platform=windows)
  - Latest version: [https://dl.grafana.com/oss/release/grafana-10.0.1.windows-amd64.zip](https://dl.grafana.com/oss/release/grafana-10.0.1.windows-amd64.zip)

- **Loki**: A lightweight logging storage and query system, serving as an alternative to Elasticsearch.
  - GitHub download: [https://github.com/grafana/loki/releases](https://github.com/grafana/loki/releases)
  - Latest version: [https://github.com/grafana/loki/releases/download/v2.8.2/loki-windows-amd64.exe.zip](https://github.com/grafana/loki/releases/download/v2.8.2/loki-windows-amd64.exe.zip)

- **Promtail**: A logging collector and forwarder that works alongside Loki, responsible for collecting logs and forwarding them to Loki.
  - GitHub download: [https://github.com/grafana/loki/releases](https://github.com/grafana/loki/releases)
  - Latest version: [https://github.com/grafana/loki/releases/download/v2.8.2/promtail-windows-amd64.exe.zip](https://github.com/grafana/loki/releases/download/v2.8.2/promtail-windows-amd64.exe.zip)

## Installation and Configuration

### Installing Windows Exporter

After installing the MSI file, you can view the exposed metrics at `http://localhost:9182/metrics`.

### Installing Prometheus

Add the following configuration to your `prometheus.yml`:

```yaml
  - job_name: windows_exporter
    scrape_interval: 15s
    static_configs:
      - targets: ["localhost:9182"]
```

Use the following command to start Prometheus:

```bash
prometheus --config.file="prometheus.yml" --web.enable-lifecycle
```

- **web.enable-lifecycle**: Enables hot-reloading. You can reload the configuration by accessing `http://localhost:9090/-/reload`.

- Access all monitoring endpoints at `http://localhost:9090/targets`.

### Installing Loki Server

[API Documentation](https://grafana.com/docs/loki/latest/api/)

- **GET /config**: Retrieves all configuration
- **GET /ready**: Checks the current state

Add the following to your `loki-config.yaml`:

```yaml
  - job_name: windows_exporter
    scrape_interval: 15s
    static_configs:
      - targets: ["localhost:9182"]
```

```markdown
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
    - from: "2023-06-22"
      store: boltdb
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 168h

storage_config:
  boltdb:
    directory: "./data/loki/index"
  filesystem:
    directory: "./data/loki/chunks"

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

```yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: "./data/positions.yaml"

clients:
  - url: "http://localhost:3100/loki/api/v1/push"

scrape_configs:
  - job_name: demo
    static_configs:
      - targets:
          - localhost
    labels:
      job: demologs
      __path__: C:\can\nop\*_entropy\log\*.log
```

```shell
loki-windows-amd64.exe --config.file="loki-config.yaml"
```

> Debug Information: `curl localhost:3100/debug/pprof/profile?seconds=20`

### Grafana Installation

Copy the sample.ini file from the Grafana conf directory to custom.ini and modify the login settings. Default values are admin/admin.

```yaml
[security]
admin_user = nop
# Default admin password, can be changed before first start or in profile settings
admin_password = test
```

Start Grafana Server using the following command:

```shell
grafana-server --config="../conf/custom.ini"
```

Access via `http://localhost:3000` with username "nop" and password "test".

> Note: Initial login will save settings to Grafana/data directory. Modify custom.ini accordingly if needed.
#### In Grafana, Adding Loki Data Source

Before performing visualization management with Grafana, you need to add the Loki data source to Grafana. Follow these steps:

- Open the Grafana UI and log in by clicking the "Configuration" button (located on the left menu).
- Click on "Data Sources" and then select "Add Data Source".
- Choose "Loki" as the data source type.
- Input the Loki API address and related details. In this example, the Loki API address is `http://localhost:3100`.
- Click "Save & Test" to test if the data source is functioning correctly.


#### Creating Grafana Dashboard

To create a Grafana dashboard:

- Open the Grafana UI and click on the "+" button (located on the left menu).
- Select the "Dashboard" option.
- Click on "Add New Panel" to add a new panel.
- Choose the metric you want to display (e.g., select the "Logs" metric to display Loki log data).
- Adjust other settings as needed and save your dashboard.


## Using docker-compose Installation

In addition to manual installation, you can use docker-compose to automatically install Grafana, Loki, and Promtail:

```dockerfile
wget https://raw.githubusercontent.com/grafana/loki/v2.8.0/production/docker-compose.yaml -O docker-compose.yaml
docker-compose -f docker-compose.yaml up
```


## Issue Diagnosis

* **Loki**: When starting, it may prompt an error: `panic: invalid page type: 11:10`
  - **Reason**: The corresponding index table file is already corrupted.
  - **Solution**: Delete the damaged index file.

* **Loki**: Error when creating index client: `file size too small`
  - **Solution**: Delete the Loki persistent directory: `boltdb-shipper-active/index_18xxx`.

* **Promtail**: Context deadline exceeded due to inability to connect to Loki.
  - **Reason**: Promtail cannot establish a connection with Loki.
  - **Solution**: Ensure network connectivity between Promtail and Loki.

