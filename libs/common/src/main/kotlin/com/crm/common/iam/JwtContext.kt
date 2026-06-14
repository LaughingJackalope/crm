package com.crm.common.iam

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT

/**
 * Parsed JWT context — extracted once per request and passed to domain services.
 * Framework-agnostic: works with Quarkus OIDC, raw headers, or any JWT source.
 */
data class JwtContext(
    val subject: String,
    val email: String?,
    val roles: Set<String>,
    val tenantId: String?,
    val rawToken: String,
    val decoded: DecodedJWT,
) {
    companion object {
        fun parse(token: String): JwtContext {
            val decoded = JWT.decode(token)
            return JwtContext(
                subject = decoded.subject,
                email = decoded.getClaim("email")?.asString(),
                roles = decoded.getClaim("realm_access")?.asMap()
                    ?.get("roles")
                    ?.let { (it as? List<*>)?.mapNotNull { r -> r as? String }?.toSet() }
                    ?: decoded.getClaim("roles")?.asArray(String::class.java)?.toSet()
                    ?: emptySet(),
                tenantId = decoded.getClaim("tenant_id")?.asString(),
                rawToken = token,
                decoded = decoded,
            )
        }
    }

    fun hasRole(role: String): Boolean = role in roles
    fun hasAnyRole(vararg required: String): Boolean = required.any { it in roles }
}
