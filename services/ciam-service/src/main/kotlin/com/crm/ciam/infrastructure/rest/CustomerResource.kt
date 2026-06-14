package com.crm.ciam.infrastructure.rest

import com.crm.ciam.application.CustomerCommandService
import com.crm.ciam.domain.customer.CustomerRepository
import com.crm.ciam.domain.customer.LifecycleStage
import com.crm.common.error.ProblemDetail
import com.crm.common.iam.JwtContext
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import java.util.UUID

/**
 * RESTEasy Reactive endpoint for Customer operations.
 * Infrastructure layer — delegates to application service.
 */
@Path("/api/v1/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class CustomerResource @Inject constructor(
    private val commandService: CustomerCommandService,
    private val customerRepository: CustomerRepository,
) {

    @POST
    @RolesAllowed("crm:admin", "crm:agent")
    fun registerContact(
        request: RegisterContactRequest,
        @Context security: SecurityContext,
    ): Response {
        val actor = security.userPrincipal?.let {
            // In production, extract from Quarkus SecurityIdentity
            null // Simplified — real impl uses @SecurityIdentity
        }
        val customer = commandService.registerContact(
            displayName = request.displayName,
            firstName = request.firstName,
            lastName = request.lastName,
            email = request.email,
            phone = request.phone,
            title = request.title,
            source = request.source,
        )
        return Response.status(Response.Status.CREATED).entity(customer).build()
    }

    @GET
    @Path("/{id}")
    fun getCustomer(@PathParam("id") id: UUID): Response {
        val customer = customerRepository.findById(id)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(
                    ProblemDetail(
                        type = "https://crm.example.com/errors/not-found",
                        title = "Customer Not Found",
                        status = 404,
                        detail = "Customer $id not found",
                    )
                ).build()
        return Response.ok(customer).build()
    }

    @POST
    @Path("/{id}/qualify")
    @RolesAllowed("crm:admin", "crm:sales")
    fun qualifyLead(@PathParam("id") id: UUID): Response {
        val customer = commandService.qualifyLead(id)
        return Response.ok(customer).build()
    }

    @POST
    @Path("/{id}/consent")
    @RolesAllowed("crm:admin", "crm:agent")
    fun updateConsent(
        @PathParam("id") id: UUID,
        request: UpdateConsentRequest,
    ): Response {
        val customer = commandService.updateConsent(id, request.purpose, request.granted)
        return Response.ok(customer).build()
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("crm:admin")
    fun deactivateCustomer(
        @PathParam("id") id: UUID,
        @QueryParam("reason") reason: String?,
    ): Response {
        val customer = commandService.deactivateCustomer(id, reason)
        return Response.ok(customer).build()
    }
}

// ── Request DTOs (could also use generated OpenAPI models) ──────────────────

data class RegisterContactRequest(
    val displayName: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String? = null,
    val title: String? = null,
    val source: String? = null,
)

data class UpdateConsentRequest(
    val purpose: String,
    val granted: Boolean,
)
