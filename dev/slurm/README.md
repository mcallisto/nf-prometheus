# Slurm test environment

Single-node Slurm cluster in a container, used to exercise nf-prometheus
with the `slurm` executor (queue wait metrics, real `sbatch`/`squeue` path).

```bash
# on the host: build the plugin first
make assemble

cd dev/slurm
docker compose up -d --build

# run the validation pipeline through Slurm
docker compose exec slurm \
    nextflow run main.nf -c ../dev/slurm/slurm-test.config

# inspect the metrics produced under the Slurm executor
docker compose exec slurm cat nf-prometheus-slurm.prom

docker compose down
```
