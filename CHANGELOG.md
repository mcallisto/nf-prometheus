# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-08-20

Initial release.

### Added
- `PrometheusObserver` on the `TraceObserverV2` event API (Nextflow >= 25.10;
  the v1 `TraceObserver` trait is deprecated upstream, see
  nextflow-io/nextflow#7516) collecting:
  - `nf_workflow_info`, `nf_workflow_status`, `nf_workflow_duration_seconds`
  - `nf_tasks_total{process,status}` (submitted / completed / failed / cached)
  - `nf_task_queue_seconds_total{process}` (scheduler queue wait)
  - `nf_task_realtime_seconds_total{process}`
  - `nf_task_cpus_requested_total{process}`
  - `nf_task_peak_rss_bytes_max{process}`
- `MetricsRegistry`: dependency-free Prometheus text exposition renderer
  (counters, gauges, labels, escaping)
- Three export modes:
  - **Textfile collector**: atomic write (temp file + rename) of a `.prom`
    file for the node_exporter textfile collector; no network connectivity
    required (air-gapped / firewalled HPC friendly)
  - **Pushgateway**: PUT of the full payload on the group
    `job/<pushJob>/run_name/<run>` — live updates during the run (throttled
    to one push per 5s, forced on completion/error), atomic replace per run,
    failures logged once and never propagated to the pipeline
  - **HTTP `/metrics`**: JDK built-in server, alive for the duration of the
    run; `0` binds an ephemeral port; a busy port logs a warning and the
    run continues
- Configuration scope `prometheus { enabled, path, pushgateway, pushJob,
  httpPort }`, registered as a config extension point (schema discovery,
  no "unrecognized config option" warnings)
- Grafana dashboard (`grafana/nf-prometheus-dashboard.json`): run status
  tiles, per-process queue wait / wall time / CPUs / peak RSS, task progress
  and a runs table, with a `run` template variable; queries deduplicate
  series across export modes with `max without (instance, job)`
- [5-minute setup guide](docs/5-minute-setup.md)
- Dev environment: single-node Slurm cluster in a container plus
  node-exporter, Pushgateway, Prometheus and auto-provisioned Grafana
  (`dev/slurm/`), with a demo pipeline for dashboard data
- Unit tests (Spock) and end-to-end validation pipeline, exercised against
  the real Slurm executor
- CI: GitHub Actions matrix over Java 17/21 and Nextflow 25.10/26.04 with
  unit tests, e2e validation run and metrics file assertion

[Unreleased]: https://github.com/mcallisto/nf-prometheus/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/mcallisto/nf-prometheus/releases/tag/v0.1.0
