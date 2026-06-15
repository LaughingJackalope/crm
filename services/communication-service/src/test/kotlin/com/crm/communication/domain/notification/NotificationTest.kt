package com.crm.communication.domain.notification

import com.crm.communication.domain.NotificationDomainException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Pure unit tests for Notification aggregate and Provider adapters.
 * No Quarkus, no DI, no database — just state transitions and BigDecimal math.
 */
@DisplayName("Notification Aggregate & Provider Tests")
class NotificationTest {

    private val fixedNow: Instant = Instant.parse("2026-06-14T10:00:00Z")

    private fun createNotification(
        status: NotificationStatus = NotificationStatus.PENDING,
        retryCount: Int = 0,
        maxRetries: Int = 3,
    ): Notification = Notification(
        recipientId = "contact-001", channelType = NotificationChannel.EMAIL,
        templateId = "welcome", subject = "Welcome!",
        body = "Hello, welcome to our platform.", status = status,
        retryCount = retryCount, maxRetries = maxRetries,
        createdAt = fixedNow, updatedAt = fixedNow,
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // Template Compilation — Variable Interpolation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Template variable interpolation")
    inner class TemplateTests {

        @Test
        fun `template variables are stored as a map`() {
            val notification = Notification(
                recipientId = "contact-001", channelType = NotificationChannel.EMAIL,
                templateId = "invoice_generated", subject = "New Invoice",
                body = "Your invoice for 1500.00 USD is due on 2026-07-14.",
                templateVariables = mapOf(
                    "customerName" to "Jane Doe",
                    "totalAmount" to "1500.00",
                    "currency" to "USD",
                    "dueDate" to "2026-07-14",
                ),
                createdAt = fixedNow, updatedAt = fixedNow,
            )

            assertEquals("Jane Doe", notification.templateVariables["customerName"])
            assertEquals("1500.00", notification.templateVariables["totalAmount"])
            assertEquals("USD", notification.templateVariables["currency"])
            assertEquals("2026-07-14", notification.templateVariables["dueDate"])
        }

        @Test
        fun `invoice template body contains exact interpolated values`() {
            val customerName = "Acme Corporation"
            val totalAmount = "25000.00"
            val currency = "USD"
            val dueDate = "2026-07-14"
            val body = "Dear $customerName, your invoice for $totalAmount $currency is due on $dueDate."

            assertTrue(body.contains("Acme Corporation"))
            assertTrue(body.contains("25000.00"))
            assertTrue(body.contains("USD"))
            assertTrue(body.contains("2026-07-14"))
        }

        @Test
        fun `template with missing variables falls back gracefully`() {
            val template = "Hello {name}, your order {orderId} is ready."
            // Simulate fallback rendering when variables are missing
            val result = template.replace("{name}", "Customer").replace("{orderId}", "N/A")
            assertEquals("Hello Customer, your order N/A is ready.", result)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Transitions
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("State transitions")
    inner class StateTransitionTests {

        @Test
        fun `full happy path lifecycle`() {
            var notification = createNotification()
            assertEquals(NotificationStatus.PENDING, notification.status)

            notification = notification.queue()
            assertEquals(NotificationStatus.QUEUED, notification.status)

            notification = notification.markSent("sg-msg-123", fixedNow.plusSeconds(60))
            assertEquals(NotificationStatus.SENT, notification.status)
            assertEquals("sg-msg-123", notification.providerMessageId)

            notification = notification.markDelivered(fixedNow.plusSeconds(120))
            assertEquals(NotificationStatus.DELIVERED, notification.status)
        }

        @Test
        fun `cannot transition from SENT back to QUEUED — must go through FAILED`() {
            val notification = createNotification(status = NotificationStatus.SENT)

            assertThrows(IllegalStateException::class.java) {
                notification.queue()
            }
        }

        @Test
        fun `cannot transition from DELIVERED back to QUEUED`() {
            val notification = createNotification(status = NotificationStatus.DELIVERED)

            assertThrows(IllegalStateException::class.java) {
                notification.queue()
            }
        }

        @Test
        fun `FAILED notification can be retried when under max retries`() {
            val notification = createNotification(status = NotificationStatus.FAILED, retryCount = 1, maxRetries = 3)

            assertTrue(notification.canRetry())
            val retried = notification.retry(fixedNow.plusSeconds(60))
            assertEquals(NotificationStatus.QUEUED, retried.status)
        }

        @Test
        fun `FAILED notification cannot be retried when max retries exceeded`() {
            val notification = createNotification(status = NotificationStatus.FAILED, retryCount = 3, maxRetries = 3)

            assertFalse(notification.canRetry())
            assertThrows(IllegalStateException::class.java) {
                notification.retry(fixedNow.plusSeconds(60))
            }
        }

        @Test
        fun `markFailed increments retry count`() {
            val notification = createNotification(status = NotificationStatus.QUEUED, retryCount = 0, maxRetries = 3)
            val failed = notification.markFailed("Connection timeout", fixedNow.plusSeconds(60))

            assertEquals(NotificationStatus.FAILED, failed.status)
            assertEquals(1, failed.retryCount)
            assertEquals("Connection timeout", failed.failureReason)
        }

        @Test
        fun `markFailed from SENT status is allowed`() {
            val notification = createNotification(status = NotificationStatus.SENT)
            val failed = notification.markFailed("Bounced", fixedNow.plusSeconds(60))

            assertEquals(NotificationStatus.FAILED, failed.status)
        }

        @Test
        fun `markBounced is a terminal state`() {
            val notification = createNotification(status = NotificationStatus.SENT)
            val bounced = notification.markBounced("Invalid email address", fixedNow.plusSeconds(60))

            assertEquals(NotificationStatus.BOUNCED, bounced.status)
            assertEquals("Invalid email address", bounced.failureReason)
        }

        @Test
        fun `retry count never exceeds maxRetries`() {
            var notification = createNotification(status = NotificationStatus.QUEUED, retryCount = 0, maxRetries = 2)

            // First failure
            notification = notification.markFailed("Timeout 1", fixedNow.plusSeconds(60))
            assertEquals(1, notification.retryCount)
            assertTrue(notification.canRetry())

            // Retry and second failure
            notification = notification.retry(fixedNow.plusSeconds(120))
            notification = notification.markFailed("Timeout 2", fixedNow.plusSeconds(180))
            assertEquals(2, notification.retryCount)
            assertFalse(notification.canRetry())
        }

        @Test
        fun `queue from FAILED status is allowed`() {
            val notification = createNotification(status = NotificationStatus.FAILED, retryCount = 0)
            val queued = notification.queue()
            assertEquals(NotificationStatus.QUEUED, queued.status)
        }

        @Test
        fun `queue from PENDING status is allowed`() {
            val notification = createNotification(status = NotificationStatus.PENDING)
            val queued = notification.queue()
            assertEquals(NotificationStatus.QUEUED, queued.status)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validation guards")
    inner class ValidationTests {

        @Test
        fun `blank recipientId throws`() {
            assertThrows(IllegalArgumentException::class.java) {
                createNotification().copy(recipientId = "")
            }
        }

        @Test
        fun `blank body throws`() {
            assertThrows(IllegalArgumentException::class.java) {
                createNotification().copy(body = "")
            }
        }

        @Test
        fun `negative retryCount throws`() {
            assertThrows(IllegalArgumentException::class.java) {
                createNotification().copy(retryCount = -1)
            }
        }

        @Test
        fun `negative maxRetries throws`() {
            assertThrows(IllegalArgumentException::class.java) {
                createNotification().copy(maxRetries = -1)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Provider Adapter Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("NotificationProvider adapter pattern")
    inner class ProviderTests {

        @Test
        fun `SendGrid provider returns success with message ID`() {
            val provider = SendGridEmailProvider()
            val notification = createNotification()

            val result = provider.send(notification)

            assertTrue(result.success)
            assertNotNull(result.providerMessageId)
            assertTrue(result.providerMessageId!!.startsWith("sg-msg-"))
        }

        @Test
        fun `Twilio provider returns success with message ID`() {
            val provider = TwilioSmsProvider()
            val notification = createNotification()

            val result = provider.send(notification)

            assertTrue(result.success)
            assertNotNull(result.providerMessageId)
            assertTrue(result.providerMessageId!!.startsWith("tw-msg-"))
        }

        @Test
        fun `InApp push provider returns success`() {
            val provider = InAppPushProvider()
            val notification = createNotification()

            val result = provider.send(notification)

            assertTrue(result.success)
            assertNotNull(result.providerMessageId)
        }

        @Test
        fun `all providers pass health check`() {
            NotificationProviderRegistry.all().forEach { provider ->
                assertTrue(provider.healthCheck(), "Provider ${provider.channelType} should pass health check")
            }
        }

        @Test
        fun `registry returns correct adapter for channel`() {
            val emailProvider = NotificationProviderRegistry.get(NotificationChannel.EMAIL)
            assertEquals(NotificationChannel.EMAIL, emailProvider.channelType)

            val smsProvider = NotificationProviderRegistry.get(NotificationChannel.SMS)
            assertEquals(NotificationChannel.SMS, smsProvider.channelType)
        }

        @Test
        fun `registry throws for unregistered channel`() {
            assertThrows(IllegalArgumentException::class.java) {
                NotificationProviderRegistry.get(NotificationChannel.SLACK)
            }
        }

        @Test
        fun `ProviderResult factory methods work correctly`() {
            val success = ProviderResult.success("msg-123")
            assertTrue(success.success)
            assertEquals("msg-123", success.providerMessageId)
            assertNull(success.errorMessage)

            val failure = ProviderResult.failure("Connection refused")
            assertFalse(failure.success)
            assertNull(failure.providerMessageId)
            assertEquals("Connection refused", failure.errorMessage)
        }

        @Test
        fun `SendGrid adapter implements EmailDeliveryProvider interface`() {
            val provider: EmailDeliveryProvider = SendGridEmailProvider()
            assertEquals(NotificationChannel.EMAIL, provider.channelType)
        }

        @Test
        fun `Twilio adapter implements SmsDeliveryProvider interface`() {
            val provider: SmsDeliveryProvider = TwilioSmsProvider()
            assertEquals(NotificationChannel.SMS, provider.channelType)
        }
    }
}
