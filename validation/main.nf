/*
 * End-to-end validation pipeline for nf-prometheus.
 * Runs a couple of trivial processes and expects the plugin to
 * produce a metrics file in Prometheus exposition format.
 */

process SAY_HELLO {
    input:
    val target

    output:
    stdout

    script:
    """
    echo "Hello, ${target}!"
    """
}

process COUNT_CHARS {
    input:
    val greeting

    output:
    stdout

    script:
    """
    echo -n "${greeting}" | wc -c
    """
}

workflow {
    channel.of('Monde', 'Mondo', 'World', 'Mundo')
        | SAY_HELLO
        | COUNT_CHARS
        | view
}
