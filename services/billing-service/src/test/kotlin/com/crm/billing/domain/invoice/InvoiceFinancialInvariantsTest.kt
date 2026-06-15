package com.crm.billing.domain.invoice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pure unit tests for financial invariants.
 * No Quarkus, no DI, no database — just BigDecimal math.
 */
@DisplayName("Invoice Financial Invariants")
class InvoiceFinancialInvariantsTest {

    private val testDueDate: LocalDate = LocalDate.now().plusDays(30)

    private fun invoiceWithLineItems(vararg items: InvoiceLineItem): Invoice {
        var inv = Invoice(
            opportunityId = "opp-001",
            customerId = "cust-001",
            invoiceNumber = "INV-001",
            dueDate = testDueDate,
        )
        for (item in items) {
            inv = inv.addLineItem(item)
        }
        return inv
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rounding Mode Verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rounding Mode — HALF_UP enforcement")
    inner class RoundingModeTests {

        @Test
        fun `tax below HALF_UP boundary rounds down`() {
            val item = InvoiceLineItem("Round down", BigDecimal.ONE, BigDecimal("100.00"), BigDecimal("0.004"))
            val invoice = invoiceWithLineItems(item)
            assertEquals(BigDecimal("0.00"), invoice.totalTax, "0.004 rounds down to 0.00")
        }

        @Test
        fun `tax at exactly HALF_UP boundary rounds up`() {
            val item = InvoiceLineItem("Round up at 0.5", BigDecimal.ONE, BigDecimal("100.00"), BigDecimal("0.005"))
            val invoice = invoiceWithLineItems(item)
            assertEquals(BigDecimal("0.01"), invoice.totalTax, "0.005 rounds up to 0.01 with HALF_UP")
        }

        @Test
        fun `tax above HALF_UP boundary rounds up`() {
            val item = InvoiceLineItem("Round up above 0.5", BigDecimal.ONE, BigDecimal("100.00"), BigDecimal("0.006"))
            val invoice = invoiceWithLineItems(item)
            assertEquals(BigDecimal("0.01"), invoice.totalTax, "0.006 rounds up to 0.01")
        }

        @Test
        fun `line item total uses HALF_UP for quantity times unit price`() {
            val item = InvoiceLineItem("Fractional price", BigDecimal("3"), BigDecimal("33.333"), BigDecimal.ZERO)
            assertEquals(BigDecimal("100.00"), item.total, "3 x 33.333 = 99.999 rounds to 100.00")
        }

        @Test
        fun `subtotal sums rounded line item totals not raw values`() {
            val item = InvoiceLineItem("Fractional", BigDecimal.ONE, BigDecimal("33.333"), BigDecimal.ZERO)
            val invoice = invoiceWithLineItems(item, item, item)
            assertEquals(BigDecimal("99.99"), invoice.subtotal, "33.33 x 3 = 99.99, not round(99.999)")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Precision and High-Scale Calculations
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Precision — BigDecimal scale preservation")
    inner class PrecisionTests {

        @Test
        fun `steep tax rate of 33_333 percent preserves precision`() {
            val item = InvoiceLineItem("Steep tax", BigDecimal.ONE, BigDecimal("999.99"), BigDecimal("33.333"))
            val invoice = invoiceWithLineItems(item)
            // taxRate/100 at TAX_SCALE=4: 33.333/100 = 0.3333
            // 999.99 x 0.3333 = 333.296667 → rounded to 2 = 333.30
            assertEquals(BigDecimal("333.30"), invoice.totalTax, "33.333% on 999.99 = 333.30")
            assertEquals(BigDecimal("1333.29"), invoice.total, "999.99 + 333.30 = 1333.29")
        }

        @Test
        fun `compound tax with multiple line items sums per-item tax correctly`() {
            val item1 = InvoiceLineItem("8.25% tax", BigDecimal.ONE, BigDecimal("500.00"), BigDecimal("8.25"))
            val item2 = InvoiceLineItem("6.5% tax", BigDecimal.ONE, BigDecimal("250.00"), BigDecimal("6.5"))
            val invoice = invoiceWithLineItems(item1, item2)
            assertEquals(BigDecimal("57.50"), invoice.totalTax, "41.25 + 16.25 = 57.50")
            assertEquals(BigDecimal("807.50"), invoice.total, "750.00 + 57.50 = 807.50")
        }

        @Test
        fun `high precision unit price with fractional quantity`() {
            val item = InvoiceLineItem("Fractional qty", BigDecimal("2.5"), BigDecimal("199.995"), BigDecimal.ZERO)
            assertEquals(BigDecimal("499.99"), item.total, "2.5 x 199.995 = 499.9875 rounds to 499.99")
        }

        @Test
        fun `NYC tax rate 8_875 percent produces correct raw and rounded values`() {
            val item = InvoiceLineItem("NYC tax", BigDecimal.ONE, BigDecimal("100.00"), BigDecimal("8.875"))
            // taxRate/100 at TAX_SCALE=4: 8.875/100 = 0.0888 (HALF_UP)
            // 100.00 x 0.0888 = 8.88 → TAX_SCALE=4: 8.8800
            assertEquals(BigDecimal("8.8800"), item.taxRaw, "Raw tax preserves TAX_SCALE=4")
            assertEquals(BigDecimal("8.88"), item.taxRounded, "Rounded tax = 8.88")
        }

        @Test
        fun `very small tax amounts truncate correctly`() {
            val item = InvoiceLineItem("Minimal tax", BigDecimal.ONE, BigDecimal("10.00"), BigDecimal("0.01"))
            assertEquals(BigDecimal("0.00"), item.taxRounded, "0.001 rounds to 0.00 at monetary scale")
        }

        @Test
        fun `large monetary values maintain precision`() {
            val item = InvoiceLineItem("Large value", BigDecimal.ONE, BigDecimal("9999999.99"), BigDecimal("10.00"))
            val invoice = invoiceWithLineItems(item)
            assertEquals(BigDecimal("9999999.99"), invoice.subtotal)
            assertEquals(BigDecimal("1000000.00"), invoice.totalTax, "10% of 9999999.99 rounds to 1000000.00")
            assertEquals(BigDecimal("10999999.99"), invoice.total)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Boundary and Validation Failure
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Boundary and Validation — domain exception on invalid input")
    inner class ValidationTests {

        @Test
        fun `negative unit price throws`() {
            val ex = assertThrows(IllegalArgumentException::class.java) {
                InvoiceLineItem("Bad price", BigDecimal.ONE, BigDecimal("-50.00"), BigDecimal.ZERO)
            }
            assertTrue(ex.message!!.contains("non-negative"))
        }

        @Test
        fun `zero quantity throws`() {
            val ex = assertThrows(IllegalArgumentException::class.java) {
                InvoiceLineItem("Zero qty", BigDecimal.ZERO, BigDecimal("100.00"), BigDecimal.ZERO)
            }
            assertTrue(ex.message!!.contains("positive"))
        }

        @Test
        fun `negative quantity throws`() {
            assertThrows(IllegalArgumentException::class.java) {
                InvoiceLineItem("Negative qty", BigDecimal("-1"), BigDecimal("100.00"), BigDecimal.ZERO)
            }
        }

        @Test
        fun `negative tax rate throws`() {
            val ex = assertThrows(IllegalArgumentException::class.java) {
                InvoiceLineItem("Negative tax", BigDecimal.ONE, BigDecimal("100.00"), BigDecimal("-5.00"))
            }
            assertTrue(ex.message!!.contains("non-negative"))
        }

        @Test
        fun `unsupported currency throws`() {
            val ex = assertThrows(IllegalArgumentException::class.java) {
                Invoice(opportunityId = "opp-001", customerId = "cust-001",
                    invoiceNumber = "INV-001", dueDate = testDueDate, currency = "INVALID")
            }
            assertTrue(ex.message!!.contains("ISO 4217"))
        }

        @Test
        fun `lowercase currency throws`() {
            assertThrows(IllegalArgumentException::class.java) {
                Invoice(opportunityId = "opp-001", customerId = "cust-001",
                    invoiceNumber = "INV-001", dueDate = testDueDate, currency = "usd")
            }
        }

        @Test
        fun `blank invoice number throws`() {
            assertThrows(IllegalArgumentException::class.java) {
                Invoice(opportunityId = "opp-001", customerId = "cust-001",
                    invoiceNumber = "   ", dueDate = testDueDate)
            }
        }

        @Test
        fun `due date before issue date throws`() {
            assertThrows(IllegalArgumentException::class.java) {
                Invoice(opportunityId = "opp-001", customerId = "cust-001",
                    invoiceNumber = "INV-001", issueDate = LocalDate.now(),
                    dueDate = LocalDate.now().minusDays(1))
            }
        }

        @Test
        fun `cannot add line item to issued invoice`() {
            val invoice = invoiceWithLineItems(
                InvoiceLineItem("Test", BigDecimal.ONE, BigDecimal("100.00"), BigDecimal.ZERO)
            ).finalize()
            assertThrows(IllegalStateException::class.java) {
                invoice.addLineItem(InvoiceLineItem("Extra", BigDecimal.ONE, BigDecimal("50.00"), BigDecimal.ZERO))
            }
        }

        @Test
        fun `cannot finalize empty invoice`() {
            val emptyInvoice = Invoice(opportunityId = "opp-001", customerId = "cust-001",
                invoiceNumber = "INV-001", dueDate = testDueDate)
            assertThrows(IllegalStateException::class.java) { emptyInvoice.finalize() }
        }

        @Test
        fun `cannot void a paid invoice`() {
            val paidInvoice = invoiceWithLineItems(
                InvoiceLineItem("Test", BigDecimal.ONE, BigDecimal("100.00"), BigDecimal.ZERO)
            ).finalize().markPaid()
            assertThrows(IllegalStateException::class.java) { paidInvoice.`void`("test") }
        }

        @Test
        fun `cannot mark paid on draft`() {
            val draftInvoice = invoiceWithLineItems(
                InvoiceLineItem("Test", BigDecimal.ONE, BigDecimal("100.00"), BigDecimal.ZERO)
            )
            assertThrows(IllegalStateException::class.java) { draftInvoice.markPaid() }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Transitions
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("State Transitions")
    inner class StateTransitionTests {

        @Test
        fun `full lifecycle draft to issued to paid`() {
            var invoice = invoiceWithLineItems(
                InvoiceLineItem("Service", BigDecimal.ONE, BigDecimal("500.00"), BigDecimal("8.25"))
            )
            assertEquals(InvoiceStatus.DRAFT, invoice.status)
            invoice = invoice.finalize()
            assertEquals(InvoiceStatus.ISSUED, invoice.status)
            invoice = invoice.markPaid()
            assertEquals(InvoiceStatus.PAID, invoice.status)
        }

        @Test
        fun `overdue transition from issued then paid`() {
            var invoice = invoiceWithLineItems(
                InvoiceLineItem("Service", BigDecimal.ONE, BigDecimal("500.00"), BigDecimal.ZERO)
            ).finalize()
            invoice = invoice.markOverdue()
            assertEquals(InvoiceStatus.OVERDUE, invoice.status)
            invoice = invoice.markPaid()
            assertEquals(InvoiceStatus.PAID, invoice.status)
        }

        @Test
        fun `void from draft state`() {
            var invoice = invoiceWithLineItems(
                InvoiceLineItem("Service", BigDecimal.ONE, BigDecimal("500.00"), BigDecimal.ZERO)
            )
            invoice = invoice.`void`("cancelled")
            assertEquals(InvoiceStatus.VOID, invoice.status)
        }

        @Test
        fun `void from issued state`() {
            var invoice = invoiceWithLineItems(
                InvoiceLineItem("Service", BigDecimal.ONE, BigDecimal("500.00"), BigDecimal.ZERO)
            ).finalize()
            invoice = invoice.`void`("cancelled")
            assertEquals(InvoiceStatus.VOID, invoice.status)
        }

        @Test
        fun `void from overdue state`() {
            var invoice = invoiceWithLineItems(
                InvoiceLineItem("Service", BigDecimal.ONE, BigDecimal("500.00"), BigDecimal.ZERO)
            ).finalize().markOverdue()
            invoice = invoice.`void`("cancelled")
            assertEquals(InvoiceStatus.VOID, invoice.status)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Total Consistency
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Total Consistency — subtotal plus tax equals total")
    inner class TotalConsistencyTests {

        @Test
        fun `total equals subtotal plus totalTax for single item`() {
            val invoice = invoiceWithLineItems(
                InvoiceLineItem("Service", BigDecimal.ONE, BigDecimal("999.99"), BigDecimal("8.25"))
            )
            assertEquals(invoice.subtotal.add(invoice.totalTax), invoice.total)
        }

        @Test
        fun `total equals subtotal plus totalTax for multiple items`() {
            val invoice = invoiceWithLineItems(
                InvoiceLineItem("Item A", BigDecimal("2"), BigDecimal("149.99"), BigDecimal("8.25")),
                InvoiceLineItem("Item B", BigDecimal.ONE, BigDecimal("499.99"), BigDecimal("6.50")),
                InvoiceLineItem("Item C", BigDecimal("3"), BigDecimal("29.99"), BigDecimal.ZERO),
            )
            assertEquals(invoice.subtotal.add(invoice.totalTax), invoice.total)
        }

        @Test
        fun `zero tax rate produces total equal to subtotal`() {
            val invoice = invoiceWithLineItems(
                InvoiceLineItem("Tax-free", BigDecimal.ONE, BigDecimal("250.00"), BigDecimal.ZERO)
            )
            assertEquals(invoice.subtotal, invoice.total)
            assertEquals(BigDecimal("0.00"), invoice.totalTax)
        }
    }
}
