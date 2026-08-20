/*
 * Demo pipeline for dashboard screenshots: realistic process names and
 * varied durations/memory footprints, all fake work (sleep + allocation).
 * Runs through the Slurm executor of the dev cluster.
 */

def burn(secs, mb) {
    """
    python3 -c "
import time
buf = bytearray(${mb} * 1024 * 1024)
time.sleep(${secs})
print('done', len(buf))
"
    """
}

process FASTQC {
    input: val sample
    output: val sample
    script: burn(4, 30)
}

process ALIGN_BWA {
    cpus 2
    input: val sample
    output: val sample
    script: burn(12, 120)
}

process MARKDUPLICATES {
    input: val sample
    output: val sample
    script: burn(7, 80)
}

process CALL_VARIANTS {
    cpus 2
    input: val sample
    output: val sample
    script: burn(10, 60)
}

process MULTIQC {
    input: val samples
    output: stdout
    script: burn(3, 20)
}

workflow {
    channel.of('sample_A', 'sample_B', 'sample_C', 'sample_D')
        | FASTQC
        | ALIGN_BWA
        | MARKDUPLICATES
        | CALL_VARIANTS
        | collect
        | MULTIQC
        | view
}
