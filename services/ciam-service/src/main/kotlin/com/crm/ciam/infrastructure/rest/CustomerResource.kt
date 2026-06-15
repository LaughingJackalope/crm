package com.crm.ciam.infrastructure.rest

import com.crm.ciam.application.CustomerCommandService
import com.crm.ciam.domain.customer.Customer
import com.crm.ciam.domain.customer.CustomerRepository
import com.crm.ciam.domain.customer.LifecycleStage
import com.crm.ciam.domain.event.DisqualificationReason
import com.crm.openapi.ciam.model.ChangeLifecycleStageRequest
import com.crm.openapi.ciam.model.ContactResponse
import com.crm.openapi.ciam.model.RegisterContactRequest
import com.crm.openapi.ciam.model.UpdateConsentRequest
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * RESTEasy Reactive endpoint for Contact (Customer) operations.
 *
 * Uses OpenAPI-generated DTOs from [com.crm.openapi.ciam.model] for all
 * request/response bodies, ensuring the HTTP contract is always in sync
 * with the API specification.
 */
@Path("/api/v1/contacts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class CustomerResource @Inject constructor(
    private val commandService: CustomerCommandService,
    private val customerRepository: CustomerRepository,
) {

    @POST
    @RolesAllowed("crm:admin", "crm:agent")
    fun registerContact(request: RegisterContactRequest): Response {
        val customer = commandService.registerContact(
            displayName = "${request.firstName} ${request.lastName}",
            firstName = request.firstName,
            lastName = request.lastName,
            email = request.email,
            phone = request.phone?.value,
            title = request.title,
            registrationSource = request.source,
        )
        return Response.status(Response.Status.CREATED)
            .entity(customer.toResponse())
            .build()
    }

    @GET
    @Path("/{contactId}")
    fun getContact(@PathParam("contactId") contactId: UUID): Response {
        val customer = customerRepository.findById(contactId)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(
                    com.crm.openapi.ciam.model.ErrorResponse(
                        error = com.crm.openapi.ciam.model.ErrorDetail(
                            code = "not-found",
                            message = "Contact $contactId not found",
                            target = "contactId",
                        ),
                    )
                ).build()
        return Response.ok(customer.toResponse()).build()
    }

    @POST
    @Path("/{contactId}/lifecycle")
    @RolesAllowed("crm:admin", "crm:sales")
    fun changeLifecycleStage(
        @PathParam("contactId") contactId: UUID,
        request: ChangeLifecycleStageRequest,
    ): Response {
        val targetStage = request.targetStage.toDomain()
        val customer = commandService.changeLifecycleStage(contactId, targetStage)
        return Response.ok(customer.toResponse()).build()
    }

    @POST
    @Path("/{contactId}/qualify")
    @RolesAllowed("crm:admin", "crm:sales")
    fun qualifyLead(@PathParam("contactId") contactId: UUID): Response {
        val customer = commandService.qualifyLead(contactId)
        return Response.ok(customer.toResponse()).build()
    }

    @DELETE
    @Path("/{contactId}/qualify")
    @RolesAllowed("crm:admin")
    fun disqualifyLead(@PathParam("contactId") contactId: UUID): Response {
        val customer = commandService.disqualifyLead(
            contactId,
            DisqualificationReason.MANUAL_DISQUALIFICATION,
        )
        return Response.ok(customer.toResponse()).build()
    }

    @PUT
    @Path("/{contactId}/consents")
    @RolesAllowed("crm:admin", "crm:agent")
    fun updateConsent(
        @PathParam("contactId") contactId: UUID,
        request: UpdateConsentRequest,
    ): Response {
        val customer = commandService.updateConsent(
            contactId,
            request.purpose,
            request.granted,
        )
        return Response.ok(customer.toResponse()).build()
    }

    @DELETE
    @Path("/{contactId}/consents/{purpose}")
    @RolesAllowed("crm:admin", "crm:agent")
    fun revokeConsent(
        @PathParam("contactId") contactId: UUID,
        @PathParam("purpose") purpose: String,
    ): Response {
        val customer = commandService.updateConsent(contactId, purpose, false)
        return Response.ok(customer.toResponse()).build()
    }

    @DELETE
    @Path("/{contactId}")
    @RolesAllowed("crm:admin")
    fun deactivateContact(@PathParam("contactId") contactId: UUID): Response {
        commandService.deactivateCustomer(contactId)
        return Response.noContent().build()
    }

    @POST
    @Path("/{contactId}/reactivate")
    @RolesAllowed("crm:admin")
    fun reactivateContact(@PathParam("contactId") contactId: UUID): Response {
        val customer = commandService.reactivateCustomer(contactId)
        return Response.ok(customer.toResponse()).build()
    }
}

// ── Mapping Extensions: Generated DTO ↔ Domain ────────────────────────────────

/**
 * Convert the OpenAPI-generated [com.crm.openapi.ciam.model.LifecycleStage]
 * to the domain [LifecycleStage].
 */
fun com.crm.openapi.ciam.model.LifecycleStage.toDomain(): LifecycleStage = when (this) {
    com.crm.openapi.ciam.model.LifecycleStage.LEAD -> LifecycleStage.LEAD
    com.crm.openapi.ciam.model.LifecycleStage.QUALIFIED -> LifecycleStage.QUALIFIED
    com.crm.openapi.ciam.model.LifecycleStage.OPPORTUNITY -> LifecycleStage.OPPORTUNITY
    com.crm.openapi.ciam.model.LifecycleStage.CUSTOMER -> LifecycleStage.CUSTOMER
    com.crm.openapi.ciam.model.LifecycleStage.ADVOCATE -> LifecycleStage.ADVOCATE
    com.crm.openapi.ciam.model.LifecycleStage.CHURNED -> LifecycleStage.CHURNED
}

/**
 * Map domain [LifecycleStage] to the OpenAPI-generated enum.
 */
fun LifecycleStage.toOpenApi(): com.crm.openapi.ciam.model.LifecycleStage = when (this) {
    LifecycleStage.LEAD -> com.crm.openapi.ciam.model.LifecycleStage.LEAD
    LifecycleStage.QUALIFIED -> com.crm.openapi.ciam.model.LifecycleStage.QUALIFIED
    LifecycleStage.OPPORTUNITY -> com.crm.openapi.ciam.model.LifecycleStage.OPPORTUNITY
    LifecycleStage.CUSTOMER -> com.crm.openapi.ciam.model.LifecycleStage.CUSTOMER
    LifecycleStage.ADVOCATE -> com.crm.openapi.ciam.model.LifecycleStage.ADVOCATE
    LifecycleStage.CHURNED -> com.crm.openapi.ciam.model.LifecycleStage.CHURNED
}

/**
 * Convert a domain [Customer] to the OpenAPI [ContactResponse] DTO.
 */
fun Customer.toResponse(): ContactResponse {
    val primaryContact = contacts.firstOrNull()
    return ContactResponse(
        contactId = customerId,
        firstName = primaryContact?.firstName ?: "",
        lastName = primaryContact?.lastName ?: "",
        email = primaryContact?.email?.let {
            com.crm.openapi.ciam.model.EmailAddress(
                value = it.value,
                isPrimary = true,
                isVerified = false,
            )
        } ?: throw IllegalStateException("Customer $customerId has no primary contact"),
        lifecycleStage = lifecycleStage.toOpenApi(),
        createdAt = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
        displayName = displayName,
        title = primaryContact?.title,
        phone = primaryContact?.phone?.let { domainPhone ->
            com.crm.openapi.ciam.model.PhoneNumber(
                value = domainPhone.value,
                countryCode = domainPhone.countryCode,
                type = when (domainPhone.type) {
                    com.crm.ciam.domain.customer.PhoneType.MOBILE ->
                        com.crm.openapi.ciam.model.PhoneNumber.Type.MOBILE
                    com.crm.ciam.domain.customer.PhoneType.LANDLINE ->
                        com.crm.openapi.ciam.model.PhoneNumber.Type.LANDLINE
                    com.crm.ciam.domain.customer.PhoneType.OTHER ->
                        com.crm.openapi.ciam.model.PhoneNumber.Type.OTHER
                },
            )
        },
        source = source,
        isActive = isActive,
        updatedAt = OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC),
        score = null,
        accountId = null,
        address = null,
    )
}
