package com.crm.communication.domain.notification

/**
 * Anti-Corruption Layer interface for external notification providers.
 *
 * Implementations handle provider-specific API calls, authentication,
 * rate limiting, and error translation. The domain layer never depends
 * on external provider SDKs or API schemas.
 */
interface NotificationProvider {
    val channelType: NotificationChannel
    fun send(notification: Notification): ProviderResult
    fun healthCheck(): Boolean
}

interface EmailDeliveryProvider : NotificationProvider {
    override val channelType: NotificationChannel get() = NotificationChannel.EMAIL
}

interface SmsDeliveryProvider : NotificationProvider {
    override val channelType: NotificationChannel get() = NotificationChannel.SMS
}

data class ProviderResult(
    val success: Boolean,
    val providerMessageId: String? = null,
    val errorMessage: String? = null,
) {
    companion object {
        fun success(messageId: String) = ProviderResult(success = true, providerMessageId = messageId)
        fun failure(error: String) = ProviderResult(success = false, errorMessage = error)
    }
}

/**
 * Stub implementation for SendGrid email delivery.
 */
class SendGridEmailProvider : EmailDeliveryProvider {
    override fun send(notification: Notification): ProviderResult {
        // Stub: simulate successful send
        return ProviderResult.success("sg-msg-${notification.notificationId}")
    }

    override fun healthCheck(): Boolean = true
}

/**
 * Stub implementation for Twilio SMS delivery.
 */
class TwilioSmsProvider : SmsDeliveryProvider {
    override fun send(notification: Notification): ProviderResult {
        // Stub: simulate successful send
        return ProviderResult.success("tw-msg-${notification.notificationId}")
    }

    override fun healthCheck(): Boolean = true
}

/**
 * Stub for in-app push notifications.
 */
class InAppPushProvider : NotificationProvider {
    override val channelType: NotificationChannel = NotificationChannel.PUSH

    override fun send(notification: Notification): ProviderResult {
        return ProviderResult.success("push-${notification.notificationId}")
    }

    override fun healthCheck(): Boolean = true
}

/**
 * Registry mapping channels to their provider implementations.
 */
object NotificationProviderRegistry {
    private val providers: Map<NotificationChannel, NotificationProvider> = mapOf(
        NotificationChannel.EMAIL to SendGridEmailProvider(),
        NotificationChannel.SMS to TwilioSmsProvider(),
        NotificationChannel.PUSH to InAppPushProvider(),
    )

    fun get(channel: NotificationChannel): NotificationProvider =
        providers[channel]
            ?: throw IllegalArgumentException("No provider registered for channel: $channel")

    fun all(): Collection<NotificationProvider> = providers.values
}
