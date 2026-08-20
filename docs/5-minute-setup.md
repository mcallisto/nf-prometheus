# nf-prometheus in 5 minutes

From zero to a Grafana dashboard of your Nextflow runs, on an HPC cluster
you don't control the network of. No root required on the cluster side —
only your `nextflow.config` and a metrics file on a shared filesystem.

## 0. Prerequisites

- Nextflow **25.10 or later** (`nextflow -version`)
- Somewhere to run Prometheus + Grafana (a VM, a workstation, an existing
  monitoring stack — not the cluster itself)

## 1. Enable the plugin

```groovy
// nextflow.config
plugins {
    id 'nf-prometheus'
}

prometheus {
    path = '/shared/metrics/rnaseq.prom'
}
```

Pick a `path` on a **shared filesystem** that your monitoring host (or its
node_exporter) can read. Use **one file per pipeline** — the run name is a
label, so consecutive runs of the same pipeline replace the file cleanly,
while two different pipelines writing the same file would clobber each other.

That's it for the cluster side. Run your pipeline as usual.

## 2. Expose the file with node_exporter

On the machine that can read `/shared/metrics`:

```
node_exporter --collector.textfile.directory=/shared/metrics
```

The plugin writes the file atomically (temp + rename), which is exactly
what the textfile collector expects — no partial reads.

## 3. Scrape it with Prometheus

```yaml
scrape_configs:
  - job_name: node
    static_configs:
      - targets: ['localhost:9100']
```

## 4. Import the dashboard

In Grafana: *Dashboards → New → Import* →
[`grafana/nf-prometheus-dashboard.json`](../grafana/nf-prometheus-dashboard.json),
pointing at your Prometheus datasource. Use the `run` variable at the top to
switch between runs.

## 5. Verify

Launch a pipeline, then:

```bash
cat /shared/metrics/rnaseq.prom          # metrics are being written
curl -s localhost:9100/metrics | grep nf_  # node_exporter exposes them
```

Open the dashboard: run status, task counts, and — the number you came
for — **per-process scheduler queue wait**.

---

## Variants

### Live updates while the run is going (Pushgateway)

The textfile is updated continuously, but Prometheus only sees it at scrape
time via node_exporter. For lower-latency updates, add a
[Pushgateway](https://github.com/prometheus/pushgateway):

```groovy
prometheus {
    pushgateway = 'http://pushgateway.example.org:9091'
}
```

and scrape it with `honor_labels: true` (without it, Prometheus rewrites
`run_name` to `exported_run_name` and the dashboard shows nothing):

```yaml
  - job_name: pushgateway
    honor_labels: true
    static_configs:
      - targets: ['pushgateway.example.org:9091']
```

Each run owns the metric group `job/nextflow/run_name/<run>`; delete old
groups from the Pushgateway UI or API whenever you want.

### Direct scrape (HTTP)

If the node where the head job runs is reachable from your Prometheus
server:

```groovy
prometheus {
    httpPort = 9200
}
```

The endpoint lives for the duration of the run and stops with it — expect
`connection refused` between runs; that's by design for batch workloads.

## HPC notes

- **Head job on a compute node behind a firewall?** That's the normal case
  and the reason the textfile and Pushgateway modes exist. Use HTTP mode
  only when you know the head node is reachable.
- **Air-gapped cluster?** Textfile mode has zero network requirements on
  the cluster side.
- **Apptainer/Singularity pipelines**: nothing special to configure — the
  plugin runs in the head job's JVM, not in task containers.
- **Relative `path`** resolves against the launch directory; prefer an
  absolute path on a shared filesystem so you always know where the file is.
- **Permissions**: the metrics file is written with your user; make sure
  the node_exporter host can read that directory (read-only export or a
  common group is enough).
- The plugin never fails your pipeline: export errors are logged as
  warnings and the run continues.
