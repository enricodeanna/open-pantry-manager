package eu.studiodeanna.openpantrymanager.receipt

import eu.studiodeanna.openpantrymanager.costs.Measure
import eu.studiodeanna.openpantrymanager.costs.MeasureUnit
import eu.studiodeanna.openpantrymanager.costs.sizeIn
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptParserTest {
    private fun parse(vararg rows: String) = ReceiptParser.parseRows(rows.toList())

    @Test
    fun italianReceiptWithVatColumnQuantitiesWeightsAndDiscounts() {
        val lines = parse(
            "ESSELUNGA S.P.A.",
            "VIA ROMA 12 - UDINE",
            "P.IVA 01255720169",
            "DOCUMENTO COMMERCIALE",
            "di vendita o prestazione",
            "DESCRIZIONE IVA Prezzo(€)",
            "LATTE P.S. ESSELUNGA 1L 4% 1,15",
            "BANANE 4% 1,89",
            "1,234 kg x 1,53 €/kg",
            "2 x 0,89",
            "YOGURT MAGRO BIANCO 10% 1,78",
            "SCONTO 10% -0,30",
            "FAGE TOTAL 0% 170G 10% 1,99",
            "SHOPPER BIO 22% 0,10",
            "TOTALE COMPLESSIVO 6,61",
            "di cui IVA 0,40",
            "PAGAMENTO ELETTRONICO 6,61",
            "PANE CHE NON E' UN PRODOTTO 1,00",
        )
        assertEquals(
            listOf(
                ReceiptLine("LATTE P.S. ESSELUNGA 1L", 1, 1.15),
                // Weighed: 1,234 kg at 1,53 €/kg makes its 1,89.
                ReceiptLine("BANANE", 1, 1.89, kilograms = 1.234),
                // Less its discount of 0,30.
                ReceiptLine("YOGURT MAGRO BIANCO", 2, 1.48),
                ReceiptLine("FAGE TOTAL 0% 170G", 1, 1.99),
                ReceiptLine("SHOPPER BIO", 1, 0.10),
            ),
            lines,
        )
    }

    @Test
    fun vatLettersAndQuantityAfterTheProduct() {
        val lines = parse(
            "Latte intero UHT 0,99 A",
            "Mozzarella 2,58 A",
            "2 x 1,29",
            "Banane 1,29 A",
            "0,846 kg x 1,52 EUR/kg",
            "Summe 4,86",
        )
        assertEquals(
            listOf(
                ReceiptLine("Latte intero UHT", 1, 0.99),
                ReceiptLine("Mozzarella", 2, 2.58),
                ReceiptLine("Banane", 1, 1.29, kilograms = 0.846),
            ),
            lines,
        )
    }

    @Test
    fun descriptionAndPriceOnSeparateRows() {
        val lines = parse(
            "PASSATA DI POMODORO",
            "3 X 0,79 2,37",
            "PROSCIUTTO CRUDO",
            "2,49",
            "TOTALE 4,86",
        )
        assertEquals(
            listOf(ReceiptLine("PASSATA DI POMODORO", 3, 2.37), ReceiptLine("PROSCIUTTO CRUDO", 1, 2.49)),
            lines,
        )
    }

    @Test
    fun toleratesTheSpacesTextRecognizersPutInAmounts() {
        // As ML Kit read a printed receipt on a phone.
        val lines = parse("1,234 kg x 1,53 €/kg", "2 x 0, 89", "YOGURT MAGRO BIANCO  10%  1,78", "PROSC. CRUDO S.DANIELE  10%  3, 49")
        assertEquals(listOf(ReceiptLine("YOGURT MAGRO BIANCO", 2, 1.78), ReceiptLine("PROSC. CRUDO S.DANIELE", 1, 3.49)), lines)
    }

    @Test
    fun headerIsNotTakenForAProductBeforeAQuantity() {
        val lines = parse("DESCRIZIONE IVA PREZZO", "2 x 0,89", "YOGURT 1,78")
        assertEquals(listOf(ReceiptLine("YOGURT", 2, 1.78)), lines)
    }

    @Test
    fun rowsAreRebuiltFromSeparateColumnsOfATiltedPhoto() {
        // Descriptions on the left, prices on the right, the photo tilted by 2 degrees: the right column
        // sits about 21 px lower than its row's description, more than half a line.
        val tan = kotlin.math.tan(2 * kotlin.math.PI / 180).toFloat()
        fun line(text: String, left: Float, y: Float, width: Float) =
            OcrLine(text, left, y + left * tan, left + width, y + left * tan + 30f, angle = 2f)
        val lines = listOf(
            line("LATTE INTERO 1L", 50f, 100f, 400f),
            line("1,15", 650f, 100f, 80f),
            line("BANANE", 50f, 145f, 180f),
            line("1,89", 650f, 145f, 80f),
            line("TOTALE", 50f, 190f, 160f),
            line("3,04", 650f, 190f, 80f),
        )
        assertEquals(listOf("LATTE INTERO 1L  1,15", "BANANE  1,89", "TOTALE  3,04"), ReceiptParser.rows(lines))
        assertEquals(listOf(ReceiptLine("LATTE INTERO 1L", 1, 1.15), ReceiptLine("BANANE", 1, 1.89)), ReceiptParser.parse(lines))
    }

    @Test
    fun guessesTidyNames() {
        assertEquals("Latte esselunga", guessName("LATTE P.S. ESSELUNGA 1L"))
        assertEquals("Yogurt magro bianco", guessName("YOGURT MAGRO BIANCO 2X125G"))
        assertEquals("Passata di pomodoro", guessName("PASSATA DI POMODORO 700 G"))
        assertEquals("Caffè macinato", guessName("CAFFÈ MACINATO 250GR"))
        assertEquals("Acqua naturale", guessName("ACQUA NATURALE 6X1,5L"))
        assertEquals("Zucchero", guessName("ZUCCHERO1KG"))
    }

    @Test
    fun photosOfALongReceiptAreJoinedWithoutTheRowsTheyRepeat() {
        val first = listOf(
            "SUPERMERCATO FRIULI",
            "LATTE P.S. FRIULI 1L 4% 1,15",
            "BANANE 4% 1,89",
            "YOGURT MAGRO BIANCO 10% 1,78",
            "PROSC.CRUDO S.DANIELE 10% 3,49",
            // Cut by the bottom of the photo.
            "PASSATA P0M0D",
        )
        val second = listOf(
            // Cut by the top of the photo.
            "Y0GURT MAGR",
            // Read a little differently the second time.
            "PROSC. CRUDO S.DANIELE 10% 3,49",
            "PASSATA POMODORO 700G 4% 0,99",
            "ACQUA FRIZZ. 6X1,5L 22% 2,34",
        )
        val third = listOf(
            "ACQUA FRIZZ. 6X1,5L 22% 2,34",
            "TOTALE COMPLESSIVO 11,54",
        )
        assertEquals(
            listOf(
                "SUPERMERCATO FRIULI",
                "LATTE P.S. FRIULI 1L 4% 1,15",
                "BANANE 4% 1,89",
                "YOGURT MAGRO BIANCO 10% 1,78",
                // From the second photo, where it is further from the edge.
                "PROSC. CRUDO S.DANIELE 10% 3,49",
                "PASSATA POMODORO 700G 4% 0,99",
                "ACQUA FRIZZ. 6X1,5L 22% 2,34",
                "TOTALE COMPLESSIVO 11,54",
            ),
            ReceiptParser.joinPages(listOf(first, second, third)),
        )
    }

    @Test
    fun photosThatShareNoRowsAreJoinedWhole() {
        val first = listOf("LATTE P.S. FRIULI 1L 4% 1,15", "4%")
        val second = listOf("4%", "BANANE 4% 1,89")
        // A short row in common is not taken for an overlap.
        assertEquals(first + second, ReceiptParser.joinPages(listOf(first, second)))
    }

    @Test
    fun theSameFoodRungUpTwiceIsKeptTwiceWhenThePhotosShowBoth() {
        val first = listOf("PANE 1,20", "YOGURT 0,89", "YOGURT 0,89")
        val second = listOf("YOGURT 0,89", "YOGURT 0,89", "MELE 2,10")
        assertEquals(listOf("PANE 1,20", "YOGURT 0,89", "YOGURT 0,89", "MELE 2,10"), ReceiptParser.joinPages(listOf(first, second)))
    }

    @Test
    fun theTotalAndTheShopAreReadToo() {
        val receipt = ReceiptParser.readRows(
            listOf(
                "COOP ALLEANZA 3.0 SOC. COOP.",
                "VIA ROMA 12 - UDINE",
                "P.IVA 01255720169",
                "DOCUMENTO COMMERCIALE",
                "MELE GOLDEN",
                "0,846 kg x 1,52 €/kg  1,29",
                "YOGURT INTERO 500G  1,99",
                "SCONTO  -0,50",
                "TOTALE COMPLESSIVO  2,78",
            ),
        )
        assertEquals(
            listOf(ReceiptLine("MELE GOLDEN", 1, 1.29, kilograms = 0.846), ReceiptLine("YOGURT INTERO 500G", 1, 1.49)),
            receipt.lines,
        )
        assertEquals(2.78, receipt.total)
        assertEquals("Coop Alleanza", receipt.shop)
    }

    @Test
    fun packageSizesAreReadFromDescriptions() {
        assertEquals(Measure(500.0, MeasureUnit.Gram), sizeIn("YOGURT INTERO 500G"))
        assertEquals(Measure(1000.0, MeasureUnit.Millilitre), sizeIn("LATTE P.S. ESSELUNGA 1L"))
        assertEquals(Measure(1500.0, MeasureUnit.Millilitre), sizeIn("ACQUA FRIZZ. 6X1,5L"))
        assertEquals(Measure(125.0, MeasureUnit.Gram), sizeIn("YOGURT 4X125G"))
        assertEquals(Measure(1000.0, MeasureUnit.Gram), sizeIn("ZUCCHERO1KG"))
        assertEquals(Measure(330.0, MeasureUnit.Millilitre), sizeIn("BIRRA 33 CL"))
        assertEquals(null, sizeIn("BANANE"))
        assertEquals(null, sizeIn("SCONTO 10%"))
    }
}
