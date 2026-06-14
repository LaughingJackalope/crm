package com.crm.ciam.infrastructure.rest

import com.crm.common.error.CrmException
import com.crm.common.error.ProblemDetail
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/**
 * Maps [CrmException] subclasses to RFC 7807 Problem+JSON responses.
 * Reused across all service modules via :libs:common.
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
