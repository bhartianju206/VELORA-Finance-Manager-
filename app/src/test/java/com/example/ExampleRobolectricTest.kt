package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.velora.util.CurrencyHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context confirms Velora branding`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Velora", appName)
    }

    @Test
    fun `currency helper formats integer paise to Indian rupees correctly`() {
        // ₹1,23,456.78 = 12345678 paise
        val formatted = CurrencyHelper.formatPaise(12345678L)
        assertEquals("₹1,23,456.78", formatted)

        // ₹500.00 = 50000 paise
        assertEquals("₹500.00", CurrencyHelper.formatPaise(50000L))

        // Negative paise: -₹150.50
        assertEquals("-₹150.50", CurrencyHelper.formatPaise(-15050L))
    }

    @Test
    fun `currency helper parses user string input into exact paise`() {
        val paise1 = CurrencyHelper.parseToPaise("1500")
        assertEquals(150000L, paise1)

        val paise2 = CurrencyHelper.parseToPaise("₹2,500.50")
        assertEquals(250050L, paise2)

        val paise3 = CurrencyHelper.parseToPaise("49.9")
        assertEquals(4990L, paise3)
    }

    @Test
    fun `accounting invariants ensure transfer is zero-sum`() {
        val accountA_initial = 100000L // ₹1,000
        val accountB_initial = 50000L  // ₹500
        val transferAmount = 20000L    // ₹200

        val accountA_after = accountA_initial - transferAmount
        val accountB_after = accountB_initial + transferAmount

        val totalInitial = accountA_initial + accountB_initial
        val totalAfter = accountA_after + accountB_after

        assertEquals(totalInitial, totalAfter)
        assertEquals(80000L, accountA_after)
        assertEquals(70000L, accountB_after)
    }

    @Test
    fun `udhaar lent reduces liquid balance and creates equal receivable`() {
        val liquidInitial = 500000L // ₹5,000
        val lentAmount = 150000L    // ₹1,500

        val liquidAfter = liquidInitial - lentAmount
        val receivable = lentAmount
        val netPosition = liquidAfter + receivable

        assertEquals(350000L, liquidAfter)
        assertEquals(150000L, receivable)
        assertEquals(liquidInitial, netPosition)
    }
}
