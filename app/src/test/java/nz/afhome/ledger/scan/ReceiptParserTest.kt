package nz.afhome.ledger.scan

import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.PayAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {
    private val today = LocalDate.of(2026, 9, 26)
    private val cat = Categorizer()
    private fun parse(text: String) = ReceiptParser.parse(text.trimIndent().lines(), cat, today)

    @Test fun woolworths() {
        val d = parse(
            """
            WOOLWORTHS
            Mt Roskill
            GST No: 123-456-789
            ANCHOR BLUE TOP MILK 2L   4.29
            VOGELS ORIGINAL 750G   5.50
            2 @ $2.75   5.50
            BANANAS KG   2.99
            0.842 kg @ $3.55/kg
            WHITTAKERS CHOC 250G   6.49
            CLUB DEAL SAVING   -1.50
            SUBTOTAL   17.77
            TOTAL   $17.77
            EFTPOS   $17.77
            GST INCLUDED   2.32
            12/09/2026 14:22
            """
        )
        assertEquals("Woolworths", d.store)
        assertEquals(LocalDate.of(2026, 9, 12).toEpochDay(), d.date)
        assertEquals(17.77, d.total!!, 0.001)
        assertEquals(2.32, d.gst!!, 0.001)
        assertEquals(4, d.items.size)
        val bread = d.items[1]
        assertEquals(2.0, bread.qty, 0.0)
        assertEquals(2.75, bread.unitPrice, 0.001)
        assertEquals(Category.BAKERY, bread.category)
        assertEquals(Category.DAIRY, d.items[0].category)
        assertEquals(4.99, d.items[3].total, 0.001) // 6.49 − 1.50 saving
        assertEquals(d.total!!, d.itemsSum, 0.01)
        assertEquals("EFTPOS", d.paymentHint)
        assertNull(d.account)
    }

    @Test fun paknsaveQuantityAfterName() {
        val d = parse(
            """
            PAK'nSAVE
            Royal Oak
            TAX INVOICE
            CHICKEN THIGH FILLETS
            1.20 kg @ $11.99/kg   14.39
            ONIONS BROWN 1.5KG   3.49
            TOTAL   17.88
            VISA   17.88
            3 Sep 2026
            """
        )
        assertEquals("PAK'nSAVE", d.store)
        assertEquals(LocalDate.of(2026, 9, 3).toEpochDay(), d.date)
        assertEquals(2, d.items.size)
        assertEquals("Chicken Thigh Fillets", d.items[0].name)
        assertEquals(14.39, d.items[0].total, 0.001)
        assertEquals("kg", d.items[0].unit)
        assertEquals(Category.MEAT, d.items[0].category)
        assertEquals(Category.PRODUCE, d.items[1].category)
    }

    @Test fun deshiGrocer() {
        val d = parse(
            """
            Bengal Spice & Halal Mart
            Sandringham Rd
            CHINIGURA RICE 5KG   24.99
            MASOOR DAL 2KG   9.50
            RADHUNI PANCH PHORON   3.99
            HILSA FISH FROZEN   18.00
            MUSTARD OIL 1L   7.49
            Total   63.97
            Cash   70.00
            Change   6.03
            21-08-2026
            """
        )
        assertEquals(5, d.items.size)
        assertTrue(d.items.all { it.category == Category.DESHI })
        assertEquals(63.97, d.total!!, 0.001)
        assertEquals(PayAccount.CASH, d.account)
    }

    @Test fun fuel() {
        val d = parse(
            """
            Z Energy Greenlane
            TAX INVOICE
            Pump 4 Z91 Unleaded
            32.45 L @ $2.899/L   94.07
            TOTAL NZD   94.07
            Mastercard   94.07
            24/09/26
            """
        )
        assertEquals("Z", d.store)
        assertEquals(32.45, d.fuelLitres!!, 0.001)
        assertEquals(2.899, d.fuelPricePerL!!, 0.0001)
        assertEquals(94.07, d.total!!, 0.001)
        assertTrue(Categorizer.isFuelStation(d.store))
        assertEquals(LocalDate.of(2026, 9, 24).toEpochDay(), d.date)
    }

    @Test fun missingTotalAndDateTriggerQuestions() {
        val d = parse(
            """
            Some Corner Dairy
            MILK 2L   4.20
            BREAD   3.00
            """
        )
        assertNull(d.total)
        assertNull(d.date)
        val ids = Clarifier.questions(d).map { it.id }
        assertTrue("who" in ids && "account" in ids && "date" in ids && "total" in ids)
    }

    @Test fun mismatchQuestion() {
        val d = parse(
            """
            New World Remuera
            EGGS DOZEN   8.99
            TOTAL   12.49
            01/09/2026
            """
        )
        assertNotNull(Clarifier.questions(d).firstOrNull { it.kind == Clarification.Kind.MISMATCH })
    }

    @Test fun wordsInsideNamesAreNotSkipped() {
        val d = parse(
            """
            Farro Fresh
            CASHEW NUTS 300G   9.99
            COFFEE BEANS   14.50
            BIRTHDAY GREETING CARD   6.99
            TOTAL   31.48
            """
        )
        assertEquals(3, d.items.size)
        assertEquals(Category.GIFTS, d.items[2].category)
    }
}
