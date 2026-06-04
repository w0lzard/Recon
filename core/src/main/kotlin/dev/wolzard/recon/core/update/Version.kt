package dev.wolzard.recon.core.update

/** Release tag version: `[v]MAJOR.MINOR.PATCH[-pre]`. A pre-release sorts before its release; pre labels compare as text. */
data class Version(val major: Int, val minor: Int, val patch: Int, val pre: String?) : Comparable<Version> {
    override fun compareTo(other: Version): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }, { it.pre == null }, { it.pre })

    companion object {
        private val re = Regex("""v?(\d{1,9})\.(\d{1,9})\.(\d{1,9})(?:-([0-9A-Za-z.]+))?""")

        fun parse(tag: String): Version? = re.matchEntire(tag)?.destructured?.let { (a, b, c, pre) ->
            Version(a.toInt(), b.toInt(), c.toInt(), pre.ifEmpty { null })
        }
    }
}
