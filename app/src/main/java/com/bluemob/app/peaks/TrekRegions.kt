package com.bluemob.app.peaks

/** A trek or a region to download peaks around, with the other spellings people type. */
data class TrekRegion(val name: String, val lat: Double, val lon: Double, val kind: String, val aliases: List<String> = emptyList())

/** Popular trekking areas, built in: found as you type, even offline and with spelling mistakes. Positions are approximate centres. */
object TrekRegions {
    val ALL = listOf(
        TrekRegion("Uttarakhand", 30.30, 79.10, "State", listOf("uttrakhand", "uttaranchal", "uttarakand", "garhwal", "kumaon")),
        TrekRegion("Himachal Pradesh", 31.95, 77.20, "State", listOf("himachal", "himanchal", "hp")),
        TrekRegion("Ladakh", 34.15, 77.58, "Region", listOf("leh", "ladak", "laddakh")),
        TrekRegion("Kashmir", 34.08, 74.80, "Region", listOf("jammu", "srinagar", "kashmeer")),
        TrekRegion("Sikkim", 27.53, 88.51, "State", listOf("gangtok", "sikim")),
        TrekRegion("Darjeeling", 27.04, 88.26, "Region", listOf("darjiling")),
        TrekRegion("Arunachal Pradesh", 27.59, 91.86, "State", listOf("arunachal", "tawang")),
        TrekRegion("Everest region, Nepal", 27.90, 86.80, "Region", listOf("everest base camp", "ebc", "khumbu", "nepal")),
        TrekRegion("Annapurna, Nepal", 28.53, 83.88, "Region", listOf("annapurna base camp", "abc", "pokhara")),
        TrekRegion("Sahyadri, Maharashtra", 18.90, 73.60, "Region", listOf("sahyadri", "maharashtra", "western ghats", "kalsubai")),
        TrekRegion("Munnar, Kerala", 10.09, 77.06, "Region", listOf("munnar", "kerala", "anamudi")),
        TrekRegion("Kedarkantha", 31.02, 78.17, "Trek", listOf("kedar kantha", "kedarkanta", "sankri")),
        TrekRegion("Har Ki Dun", 31.15, 78.40, "Trek", listOf("harkidun", "har ki doon")),
        TrekRegion("Valley of Flowers", 30.73, 79.60, "Trek", listOf("hemkund", "hemkund sahib", "ghangaria")),
        TrekRegion("Roopkund", 30.26, 79.73, "Trek", listOf("rupkund", "bedni bugyal")),
        TrekRegion("Kuari Pass", 30.47, 79.63, "Trek", listOf("kuari", "auli")),
        TrekRegion("Brahmatal", 30.20, 79.57, "Trek", listOf("brahmatal", "brahmatal lake")),
        TrekRegion("Kedarnath", 30.73, 79.07, "Trek", listOf("kedar nath", "gaurikund")),
        TrekRegion("Gaumukh and Tapovan", 30.92, 79.08, "Trek", listOf("gaumukh", "tapovan", "gangotri")),
        TrekRegion("Chopta and Tungnath", 30.49, 79.22, "Trek", listOf("chopta", "tungnath", "chandrashila")),
        TrekRegion("Dayara Bugyal", 30.85, 78.55, "Trek", listOf("dayara", "dayara bugyal")),
        TrekRegion("Nag Tibba", 30.58, 78.15, "Trek", listOf("nagtibba")),
        TrekRegion("Hampta Pass", 32.27, 77.36, "Trek", listOf("hampta", "hamta", "hamta pass")),
        TrekRegion("Manali", 32.24, 77.19, "Town", listOf("solang", "kullu")),
        TrekRegion("Bhrigu Lake", 32.30, 77.23, "Trek", listOf("bhrigu")),
        TrekRegion("Triund", 32.26, 76.34, "Trek", listOf("mcleodganj", "dharamshala", "dharamsala", "indrahar")),
        TrekRegion("Kheerganga", 31.99, 77.46, "Trek", listOf("kheer ganga", "parvati valley", "kasol")),
        TrekRegion("Pin Parvati Pass", 31.90, 77.80, "Trek", listOf("pin parvati")),
        TrekRegion("Spiti", 32.23, 78.07, "Region", listOf("kaza", "spiti valley")),
        TrekRegion("Zanskar", 33.50, 76.90, "Region", listOf("chadar", "chadar trek", "padum")),
        TrekRegion("Markha Valley", 33.85, 77.45, "Trek", listOf("markha")),
        TrekRegion("Kashmir Great Lakes", 34.30, 75.30, "Trek", listOf("kgl", "sonamarg", "great lakes")),
        TrekRegion("Goechala", 27.45, 88.20, "Trek", listOf("goecha la", "dzongri", "yuksom")),
        TrekRegion("Sandakphu", 27.10, 88.00, "Trek", listOf("sandakfu", "phalut")),
    )

    /** Shown before anything is typed. */
    val POPULAR = listOf("Kedarkantha", "Hampta Pass", "Valley of Flowers", "Triund", "Sandakphu", "Uttarakhand", "Himachal Pradesh", "Ladakh").mapNotNull { n -> ALL.firstOrNull { it.name == n } }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Edit distance, so "uttrakhand" still finds Uttarakhand. */
    fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val t = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return d[b.length]
    }

    fun search(q: String): List<TrekRegion> {
        val t = norm(q); if (t.length < 2) return emptyList()
        val allowed = when { t.length >= 8 -> 2; t.length >= 5 -> 1; else -> 0 }
        return ALL.mapNotNull { r ->
            val names = (listOf(r.name) + r.aliases).map(::norm)
            val score = names.minOf { n ->
                when {
                    n.startsWith(t) -> 0
                    t in n -> 1
                    allowed > 0 && distance(t, n.take(t.length)) <= allowed -> 2
                    allowed > 0 && distance(t, n) <= allowed -> 2
                    else -> 9
                }
            }
            if (score < 9) score to r else null
        }.sortedBy { it.first }.map { it.second }.take(8)
    }
}
