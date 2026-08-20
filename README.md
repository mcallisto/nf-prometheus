# nf-prometheus

Prometheus metrics for [Nextflow](https://nextflow.io) pipelines, designed for
**on-premise HPC clusters** (Slurm, shared filesystems, Apptainer, air-gapped
environments).

Unlike a classic HTTP exporter, nf-prometheus is built HPC-first: the head job
often runs on a compute node behind a firewall, unreachable by your Prometheus
server. The primary export mode therefore requires **no network connectivity at
all**.

## Export modes

| Mode | Status | How it works |
|---|---|---|
| **Textfile collector** | ✅ available | Writes a `.prom` file (atomic rename) for the [node_exporter textfile collector](https://github.com/prometheus/node_exporter#textfile-collector) — typically on a shared filesystem. Works air-gapped. |
| HTTP `/metrics` | 🚧 planned | Classic scrape endpoint on the head job |
| Pushgateway | 🚧 planned | Push to your Prometheus Pushgateway |

## Metrics (v0.1)

- `nf_workflow_info{run_name,session_id}` — run information
- `nf_workflow_status{run_name}` — 0=running, 1=complete, 2=error
- `nf_workflow_duration_seconds{run_name}`
- `nf_tasks_total{run_name,process,status}` — submitted / completed / failed / cached
- `nf_task_queue_seconds_total{run_name,process}` — time spent waiting in the scheduler queue
- `nf_task_realtime_seconds_total{run_name,process}`
- `nf_task_cpus_requested_total{run_name,process}`
- `nf_task_peak_rss_bytes_max{run_name,process}`

## Usage

```groovy
// nextflow.config
plugins {
    id 'nf-prometheus'
}

prometheus {
    // where to write the metrics file (relative to the launch directory)
    path = '/shared/metrics/my-pipeline.prom'
}
```

Then point your node_exporter at the directory:

```
node_exporter --collector.textfile.directory=/shared/metrics
```

## Development

```bash
make assemble   # build the plugin
make test       # unit tests
make install    # install into the local Nextflow plugins dir
```

End-to-end check:

```bash
make install
cd validation && nextflow run main.nf && cat nf-prometheus.prom
```

## License

[Apache 2.0](COPYING)
