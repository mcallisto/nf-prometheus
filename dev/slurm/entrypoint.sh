#!/bin/bash
# Boot munge + slurmctld + slurmd in a single container, then run CMD.
set -e

# munge key (throwaway, test only)
if [ ! -f /etc/munge/munge.key ]; then
    dd if=/dev/urandom bs=1 count=1024 of=/etc/munge/munge.key 2>/dev/null
    chown munge:munge /etc/munge/munge.key
    chmod 400 /etc/munge/munge.key
fi
mkdir -p /run/munge && chown munge:munge /run/munge
echo "starting munged..."
runuser -u munge -- /usr/sbin/munged

echo "starting slurmctld..."
slurmctld

# cgroup v2 scope dir expected by slurmd (no systemd in container)
mkdir -p /sys/fs/cgroup/system.slice 2>/dev/null || true

echo "starting slurmd..."
slurmd

# wait for the node to be ready
for i in $(seq 1 30); do
    if sinfo -h 2>/dev/null | grep -q main; then
        echo "Slurm is up:"; sinfo
        break
    fi
    sleep 1
done

# after a container restart the controller may hold the node in drain/down
# ("unexpectedly rebooted"): put it back in service
scontrol update nodename=slurmnode state=resume 2>/dev/null || true

# dev-env watchdog: pgid proctrack in containers occasionally drains the
# node with "Kill task failed" — resume it automatically
( while true; do
    sleep 30
    sinfo -h -o '%T' | grep -qE 'drain|down' && \
        scontrol update nodename=slurmnode state=resume 2>/dev/null
  done ) &

# install the nf-prometheus plugin if the repo build is mounted at /workspace
PLUGIN_SRC=$(ls -d /workspace/build/plugins/nf-prometheus-* 2>/dev/null | head -1 || true)
if [ -z "$PLUGIN_SRC" ]; then
    PLUGIN_ZIP=$(ls /workspace/build/distributions/nf-prometheus-*.zip 2>/dev/null | head -1 || true)
fi
mkdir -p /root/.nextflow/plugins
if [ -n "$PLUGIN_SRC" ]; then
    cp -r "$PLUGIN_SRC" /root/.nextflow/plugins/
    echo "Installed plugin from $PLUGIN_SRC"
elif [ -n "$PLUGIN_ZIP" ]; then
    NAME=$(basename "$PLUGIN_ZIP" .zip)
    unzip -qo "$PLUGIN_ZIP" -d "/root/.nextflow/plugins/$NAME"
    echo "Installed plugin from $PLUGIN_ZIP"
else
    echo "WARNING: no plugin build found under /workspace/build — run 'make assemble' on the host first"
fi

exec "$@"
