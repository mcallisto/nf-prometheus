# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.1] - Unreleased

### Added
- HTTP export mode: `prometheus.httpPort` serves the current metrics on
  `/metrics` (JDK built-in server, zero dependencies) for the duration of
  the run; `0` binds an ephemeral port; a busy port logs a warning and the
  run continues
- Pushgateway export mode: `prometheus.pushgateway` (base URL) and
  `prometheus.pushJob` options; PUT of the full payload on the group
  `job/<pushJob>/run_name/<run>` — live updates during the run (throttled
  to one push per 5s, forced on completion/error), atomic replace per run,
  failures logged once and never propagated to the pipeline
- Dev stack: pushgateway service and Prometheus scrape with
  `honor_labels: true`; Slurm test node auto-resumed after container
  restart (controller holds it in drain after an unexpected reboot)
- Grafana dashboard (`grafana/nf-prometheus-dashboard.json`) with run status
  tiles, per-process queue wait / wall time / CPU / peak RSS, task progress
  and a runs table; `run` template variable
- Dev monitoring stack: node-exporter (textfile collector), Prometheus and
  auto-provisioned Grafana alongside the Slurm test container

### Changed
- Migrated the observer and factory to `TraceObserverV2` /
  `TraceObserverFactoryV2` (event-based API, available since Nextflow
  25.10). The v1 `TraceObserver` trait is deprecated on Nextflow main
  (see nextflow-io/nextflow#7516); v2 is `@Beta`, so event classes may
  still change shape — covered by the CI matrix

### Fixed
- Dashboard queries deduplicate series with `max without (instance, job)`:
  when two export modes are active at once (e.g. textfile + Pushgateway),
  every metric arrived through two scrape paths and all panels double-counted
- `enableMetrics()` now returns `true`, so Nextflow collects per-task
  resource metrics; without it `nf_task_peak_rss_bytes_max` was always empty

## [0.1.0] - Unreleased

### Added
- Initial plugin skeleton based on the official `nf-plugin-template`
- `MetricsRegistry`: dependency-free Prometheus text exposition renderer
  (counters, gauges, labels, escaping)
- `PrometheusObserver` (TraceObserver v1, Nextflow >= 25.10) collecting:
  - `nf_workflow_info`, `nf_workflow_status`, `nf_workflow_duration_seconds`
  - `nf_tasks_total{process,status}` (submitted / completed / failed / cached)
  - `nf_task_queue_seconds_total{process}` (scheduler queue wait)
  - `nf_task_realtime_seconds_total{process}`
  - `nf_task_cpus_requested_total{process}`
  - `nf_task_peak_rss_bytes_max{process}`
- Textfile-collector export mode: atomic write (temp file + rename) of a
  `.prom` file for the node_exporter textfile collector; no network
  connectivity required (air-gapped / firewalled HPC friendly)
- `prometheus { enabled, path }` configuration options
- Unit tests (Spock) and end-to-end validation pipeline
- CI: GitHub Actions matrix over Java 17/21 and Nextflow 25.10/26.04 with
  unit tests, e2e validation run and metrics file assertion
