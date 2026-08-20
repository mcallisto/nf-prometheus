# Single-node Slurm cluster for testing nf-prometheus with the slurm executor.
# Not for production use.
FROM rockylinux:9

RUN dnf install -y epel-release && \
    dnf install -y \
        slurm slurm-slurmctld slurm-slurmd \
        munge \
        java-17-openjdk-headless \
        procps-ng hostname which unzip curl-minimal \
    && dnf clean all

# Nextflow launcher; framework is pre-warmed at build time
RUN curl -fsSL https://www.nextflow.io/releases/latest/nextflow -o /usr/local/bin/nextflow && \
    chmod +x /usr/local/bin/nextflow && \
    NXF_HOME=/root/.nextflow nextflow -version

COPY slurm.conf /etc/slurm/slurm.conf
COPY cgroup.conf /etc/slurm/cgroup.conf
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN id slurm >/dev/null 2>&1 || useradd -r -M -d /var/lib/slurm -s /sbin/nologin slurm
RUN chmod +x /usr/local/bin/entrypoint.sh && \
    mkdir -p /var/spool/slurmctld /var/spool/slurmd /var/log/slurm && \
    chown slurm:slurm /var/spool/slurmctld /var/spool/slurmd /var/log/slurm

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["sleep", "infinity"]
