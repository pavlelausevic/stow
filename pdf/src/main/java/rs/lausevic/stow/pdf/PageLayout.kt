// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.pdf

/**
 * Geometrija strane — sve što se menja između formata drži se ovde, da bi crtanje ostalo
 * jedno i isto.
 *
 * Postoje dva formata jer postoje dve upotrebe. [A4] je list koji se štampa i kači na
 * frižider. [PHONE] je uska strana: čitač je uklapa po širini, pa lista staje na ekran
 * telefona bez pomeranja levo-desno i bez uvećavanja — čita se palcem, kao spisak, a ne
 * kao dokument po kome se šetaš.
 *
 * Veličine slova su iste u oba formata (u tačkama). To i jeste mehanizam: uža strana se
 * uklapa uz veće uvećanje, pa isti tekst na telefonu ispadne otprilike dvostruko krupniji.
 */
data class PageLayout(
    val pageWidth: Double,
    val pageHeight: Double,
    val margin: Double,
    val headerHeight: Double,
    val continuationTop: Double,
    val footerHeight: Double,
    val sectionHeight: Double,
    val sectionGap: Double,
    val rowHeight: Double,
    val rowHeightWithMeta: Double,
    val boxSize: Double,
    val titleSize: Double,
    val stampWidth: Double,
    val stampSize: Double,
) {
    /** Visina reda za stavku sa datim opisom — jedino mesto koje zna za tu razliku. */
    fun rowHeight(hasMeta: Boolean): Double = if (hasMeta) rowHeightWithMeta else rowHeight

    companion object {
        /** ISO A4, 210 × 297 mm. Za štampu i za slanje nekome ko je na računaru. */
        val A4 = PageLayout(
            pageWidth = 595.28,
            pageHeight = 841.89,
            margin = 48.0,
            headerHeight = 92.0,
            continuationTop = 34.0,
            footerHeight = 30.0,
            sectionHeight = 22.0,
            sectionGap = 8.0,
            rowHeight = 19.0,
            rowHeightWithMeta = 27.0,
            boxSize = 12.0,
            titleSize = 22.0,
            stampWidth = 96.0,
            stampSize = 8.0,
        )

        /**
         * 300 × 540 pt. Odnos stranica 1 : 1,8 je blizu vidljivog dela ekrana telefona
         * pošto čitač oduzme svoje trake, pa jedna strana legne na jedan ekran.
         *
         * Kutija je 15 pt umesto 12: pri uklapanju po širini to je oko 54 px na ekranu
         * od 1080 px, dakle prst je pogodi bez ciljanja. Na A4 bi ista kutija bila
         * upola manja.
         */
        val PHONE = PageLayout(
            pageWidth = 300.0,
            pageHeight = 540.0,
            margin = 20.0,
            headerHeight = 66.0,
            continuationTop = 26.0,
            footerHeight = 24.0,
            sectionHeight = 20.0,
            sectionGap = 7.0,
            rowHeight = 23.0,
            rowHeightWithMeta = 31.0,
            boxSize = 15.0,
            titleSize = 16.0,
            stampWidth = 72.0,
            stampSize = 7.0,
        )
    }
}
