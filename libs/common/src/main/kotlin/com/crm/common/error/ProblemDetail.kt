package com.crm.common.error

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * RFC 7807 Problem Detail response body.
 * Used across all services for consistent error responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProblemDetail(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<FieldError>? = null,
    val traceId: String? = null,
)

data class FieldError(
    val field: String,
    val message: String,
    val rejectedValue: Any? = null,
)

/**
 * Base exception that maps to a ProblemDetail response.
 */
abstract class CrmException(
    val problemType: String,
    val problemTitle: String,
    val httpStatus: Int,
    override val message: String,
    val fieldErrors: List<FieldError>? = null,
) : RuntimeException(message)

class NotFoundException(
    resource: String,
    id: String,
) : CrmException(
    problemType = "https://crm.example.com/errors/not-found",
    problemTitle = "Resource Not Found",
    httpStatus = 404,
    message = "$resource with id '$id' not found",
)

class ConflictException(
    message: String,
) : CrmException(
    problemType = "https://crm.example.com/errors/conflict",
    problemTitle = "Conflict",
    httpStatus = 409,
    message = message,
)

class ValidationException(
    fieldErrors: List<FieldError>,
) : CrmException(
    problemType = "https://crm.example.com/errors/validation",
    problemTitle = "Validation Failed",
    httpStatus = 422,
    message = "Request validation failed",
    fieldErrors = fieldErrors,
)

class UnauthorizedException(
    message: String = "Authentication required",
) : CrmException(
    problemType = "https://crm.example.com/errors/unauthorized",
    problemTitle = "Unauthorized",
    httpStatus = 401,
    message = message,
)

class ForbiddenException(
    message: String = "Insufficient permissions",
) : CrmException(
    problemType = "https://crm.example.com/errors/forbidden",
    problemTitle = "Forbidden",
    httpStatus = 403,
    message = message,
)
