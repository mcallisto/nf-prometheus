# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Config scope registration for the `prometheus` block (planned; currently
  emits a harmless "Unrecognized config option" warning)
- HTTP `/metrics` scrape endpoint (planned)
- Pushgateway export mode (planned)

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
