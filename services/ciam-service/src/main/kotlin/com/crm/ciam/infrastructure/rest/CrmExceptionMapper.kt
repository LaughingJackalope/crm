package com.crm.ciam.infrastructure.rest

import com.crm.ciam.domain.CiamDomainException
import com.crm.common.error.CrmException
import com.crm.common.error.ProblemDetail
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/**
 * Maps [CrmException] subclasses to RFC 7807 Problem+JSON responses.
 */
@Provider
class CrmExceptionMapper : ExceptionMapper<CrmException> {
    override fun toResponse(exception: CrmException): Response =
        Response.status(exception.httpStatus)
            .entity(
                ProblemDetail(
                    type = exception.problemType,
                    title = exception.problemTitle,
                    status = exception.httpStatus,
                    detail = exception.message,
                    errors = exception.fieldErrors,
                )
            )
            .build()
}

/**
 * Maps CIAM domain exceptions to appropriate HTTP 409 Conflict responses.
 */
@Provider
class CiamDomainExceptionMapper : ExceptionMapper<CiamDomainException> {
    override fun toResponse(exception: CiamDomainException): Response {
        val (status, type, title) = when (exception) {
            is CiamDomainException.InvalidLifecycleTransition ->
                Triple(409, "https://crm.example.com/errors/invalid-transition", "Invalid State Transition")
            is CiamDomainException.CustomerInactive ->
                Triple(409, "https://crm.example.com/errors/customer-inactive", "Customer Inactive")
            is CiamDomainException.LeadNotQualified ->
                Triple(409, "https://crm.example.com/errors/lead-not-qualified", "Lead Not Qualified")
            is CiamDomainException.DuplicateContact ->
                Triple(409, "https://crm.example.com/errors/duplicate-contact", "Duplicate Contact")
            is CiamDomainException.InvalidReactivation ->
                Triple(409, "https://crm.example.com/errors/invalid-reactivation", "Invalid Reactivation")
        }
        return Response.status(status)
            .entity(
                ProblemDetail(
                    type = type,
                    title = title,
                    status = status,
                    detail = exception.message,
                )
            )
            .build()
    }
}
