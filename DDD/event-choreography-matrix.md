# Event Choreography Matrix — CRM Bounded Contexts

> Derived from `DDD/api/asyncapi.yaml` v1.1.0 (55 domain events).
> Generated: 2026-06-16

## Topic Naming Convention

```
crm.{bounded-context}.{aggregate-name}.{event-name}
```

| Component | Values |
|---|---|
| Bounded Context | `ciam` \| `sales` \| `support` \| `marketing` \| `communication` \| `billing` |
| Aggregate Name | lowercase, hyphenated (e.g., `opportunity`, `subscription`) |
| Event Name | lowercase, hyphenated past tense (e.g., `created`, `closed`) |

## Consumer Group Convention

```
{context}.{purpose}
```

---

## CIAM — Customer Identity & Access Management (7 events)

| # | Triggering Event | Kafka Topic | Consuming Service(s) | Downstream Reaction |
|---|---|---|---|---|
| 1 | `crm.ciam.customer.registered` | `crm.ciam.customer.registered` | sales-service, marketing-service, communication-service | Sales: opportunity seeding. Marketing: lead ingestion. Communication: welcome flow. |
| 2 | `crm.ciam.lead.qualified` | `crm.ciam.lead.qualified` | sales-service | Sales: trigger opportunity creation. |
| 3 | `crm.ciam.customer.merged` | `crm.ciam.customer.merged` | **all services** | All contexts: update stale references (customer ID mapping). |
| 4 | `crm.ciam.consent.changed` | `crm.ciam.consent.changed` | marketing-service, communication-service | Marketing: campaign eligibility update. Communication: channel opt-in enforcement. |
| 5 | `crm.ciam.email.verified` | `crm.ciam.email.verified` | communication-service | Communication: enable email channel delivery. |
| 6 | `crm.ciam.customer.deactivated` | `crm.ciam.customer.deactivated` | support-service, billing-service, communication-service | Support: suspend open tickets. Billing: cancel subscriptions. Communication: suppress outbound messages. |
| 7 | `crm.ciam.lifecycle-stage.changed` | `crm.ciam.lifecycle-stage.changed` | sales-service | Sales: pipeline stage synchronization. |

### CIAM Failure-Path Events (3 events)

| # | Triggering Event | Kafka Topic | Consuming Service(s) | Downstream Reaction |
|---|---|---|---|---|
| 8 | `crm.ciam.lead.disqualified` | `crm.ciam.lead.disqualified` | sales-service | Sales: flag stale opportunities for review. |
| 9 | `crm.ciam.customer.reactivated` | `crm.ciam.customer.reactivated` | **all services** | All contexts: re-enable services, update lifecycle. |
| 10 | `crm.ciam.customer.merge-conflict` | `crm.ciam.customer.merge-conflict` | **all services** | All contexts: resolve conflicting references. |

---

## Sales / Opportunity Pipeline (8 events)

| # | Triggering Event | Kafka Topic | Consuming Service(s) | Downstream Reaction |
|---|---|---|---|---|
| 11 | `crm.sales.opportunity.created` | `crm.sales.opportunity.created` | communication-service | Communication: stakeholder notifications. |
| 12 | `crm.sales.opportunity.stage-advanced` | `crm.sales.opportunity.stage-advanced` | communication-service | Communication: status update notifications. |
| 13 | `crm.sales.opportunity.closed` | `crm.sales.opportunity.closed` | billing-service, communication-service | Billing: won → create subscription/invoice. Communication: team notifications. |
| 14 | `crm.sales.quote.generated` | `crm.sales.quote.generated` | communication-service | Communication: deliver quote to customer. |
| 15 | `crm.sales.quote.sent` | `crm.sales.quote.sent` | communication-service | Communication: delivery tracking. |
| 16 | `crm.sales.quote.accepted` | `crm.sales.quote.accepted` | billing-service | Billing: trigger subscription creation or invoice generation. |
| 17 | `crm.sales.forecast.updated` | `crm.sales.forecast.updated` | communication-service | Communication: report distribution. |
| 18 | `crm.sales.opportunity.owner-reassigned` | `crm.sales.opportunity.owner-reassigned` | communication-service | Communication: notify new owner. |

---

## Support / Ticketing (9 events)

| # | Triggering Event | Kafka Topic | Consuming Service(s) | Downstream Reaction |
|---|---|---|---|---|
| 19 | `crm.support.ticket.created` | `crm.support.ticket.created` | communication-service | Communication: assignee/customer notifications. |
| 20 | `crm.support.ticket.assigned` | `crm.support.ticket.assigned` | communication-service | Communication: agent notification. |
| 21 | `crm.support.ticket.comment-added` | `crm.support.ticket.comment-added` | communication-service | Communication: notify customer (public) or team (internal). |
| 22 | `crm.support.ticket.priority-changed` | `crm.support.ticket.priority-changed` | communication-service | Communication: SLA breach alerts. |
| 23 | `crm.support.ticket.escalated` | `crm.support.ticket.escalated` | communication-service | Communication: manager notification. |
| 24 | `crm.support.ticket.resolved` | `crm.support.ticket.resolved` | communication-service | Communication: customer notification. |
| 25 | `crm.support.ticket.closed` | `crm.support.ticket.closed` | communication-service, ciam-service | Communication: CSAT survey trigger. CIAM: customer health scoring. |
| 26 | `crm.support.ticket.reopened` | `crm.support.ticket.reopened` | communication-service | Communication: assignee notification. |
| 27 | `crm.support.sla.breached` | `crm.support.sla.breached` | communication-service | Communication: escalation alerts. |

### Support Failure-Path Events (2 events)

| # | Triggering Event | Kafka Topic | Consuming Service(s) | Downstream Reaction |
|---|---|---|---|---|
| 28 | `crm.support.sla.recovered` | `crm.support.sla.recovered` | communication-service | Communication: management dashboard updates. |
| 29 | `crm.support.csat.survey-delivery-failed` | `crm.support.csat.survey-delivery-failed` | support-service | Support: manual follow-up tracking. |

---

## Marketing / Campaigns (9 events)

| # | Triggering Event | Kafka Topic | Consuming Service(s) | Downstream Reaction |
|---|---|---|---|---|
| 30 | `crm.marketing.campaign.created` | `crm.marketing.campaign.created` | communication-service | Communication: delivery infrastructure preparation. |
| 31 | `crm.marketing.segment.defined` | `crm.marketing.segment.defined` | *(internal)* | Marketing: internal analytics only. |
| 32 | `crm.marketing.campaign.scheduled` | `crm.marketing.campaign.scheduled` | communication-service | Communication: schedule message delivery. |
| 33 | `crm.marketing.campaign.launched` | `crm.marketing.campaign.launched` | communication-service | Communication: start outbound delivery. |
| 34 | `crm.marketing.campaign.paused` | `crm.marketing.campaign.paused` | communication-service | Communication: stop outbound delivery. |
| 35 | `crm.marketing.engagement.recorded` | `crm.marketing.engagement.recorded` | ciam-service | CIAM: lead scoring update. |
| 36 | `crm.marketing.conversion.declared` | `crm.marketing.conversion.declared` | sales-service, ciam-service | Sales: opportunity attribution. CIAM: lead scoring. |
| 37 | `crm.marketing.ab-test.winner-selected` | `crm.marketing.ab-test.winner-selected` | communication-service | Communication: apply winning variant to remaining audience. |
| 38 | `crm.marketing.campaign.completed` | `crm.marketing.campaign.completed` | communication-service | Communication: final report distribution. |

### Marketing Failure-Path Events (1 event)

| # | Triggering Event | Kafka Topic | Consuming Service(s) | Downstream Reaction |
|---|---|---|---|---|
| 39 | `crm.marketing.campaign.delivery-failed` | `crm.marketing.campaign.delivery-failed` | marketing-service | Marketing: metrics accuracy and campaign health monitoring. |

---

## Communication Hub (6 events)

| # | Triggering Event | Kafka Topic | Consuming Service(s) | Downstream Reaction |
|---|---|---|---|---|
| 40 | `crm.communication.message.sent` | `crm.communication.message.sent` | support-service, sales-service | Support: ticket thread tracking. Sales: interaction logging. |
| 41 | `crm.communication.message.delivered` | `crm.communication.message.delivered` | support-service, sales-service | Support/Sales: interaction tracking. |
| 42 | `crm.communication.message.failed` | `crm.communication.message.failed` | communication-service | Communication: retry orchestration and alerting. |
| 43 | `crm.communication.message.read` | `crm.communication.message.read` | marketing-service, sales-service | Marketing: engagement metrics. Sales: interaction logging. |
| 44 | `crm.communication.message.inbound-received` | `crm.communication.message.inbound-received` | support-service, sales-service | Support: auto-create ticket. Sales: log interaction. |
| 45 | `crm.communication.opt-out.recorded` | `crm.communication.opt-out.recorded` | ciam-service | CIAM: update consent records. |

---

## Billing & Subscription (11 events)

| # | Triggering Event | Kafka Topic | Consuming Service(s) | Downstream Reaction |
|---|---|---|---|---|
| 46 | `crm.billing.subscription.created` | `crm.billing.subscription.created` | communication-service | Communication: welcome/onboarding emails. |
| 47 | `crm.billing.subscription.plan-changed` | `crm.billing.subscription.plan-changed` | communication-service | Communication: confirmation notifications. |
| 48 | `crm.billing.subscription.cancelled` | `crm.billing.subscription.cancelled` | communication-service, ciam-service | Communication: confirmation. CIAM: lifecycle stage update. |
| 49 | `crm.billing.invoice.generated` | `crm.billing.invoice.generated` | communication-service | Communication: invoice delivery. |
| 50 | `crm.billing.invoice.issued` | `crm.billing.invoice.issued` | communication-service | Communication: delivery tracking. |
| 51 | `crm.billing.payment.succeeded` | `crm.billing.payment.succeeded` | communication-service, ciam-service | Communication: receipt delivery. CIAM: customer health scoring. |
| 52 | `crm.billing.payment.failed` | `crm.billing.payment.failed` | communication-service | Communication: dunning notice delivery. |
| 53 | `crm.billing.credit-note.issued` | `crm.billing.credit-note.issued` | communication-service | Communication: customer notification. |
| 54 | `crm.billing.trial.started` | `crm.billing.trial.started` | communication-service | Communication: welcome/onboarding flow. |
| 55 | `crm.billing.trial.expiring` | `crm.billing.trial.expiring` | communication-service | Communication: conversion reminder emails. |
| 56 | `crm.billing.dunning.escalated` | `crm.billing.dunning.escalated` | communication-service | Communication: escalation notice delivery. |

### Billing Failure-Path Events (5 events)

| # | Triggering Event | Kafka Topic | Consuming Service(s) | Downstream Reaction |
|---|---|---|---|---|
| 57 | `crm.billing.subscription.creation-failed` | `crm.billing.subscription.creation-failed` | sales-service, communication-service | Sales: pipeline follow-up. Communication: customer notification. |
| 58 | `crm.billing.invoice.generation-failed` | `crm.billing.invoice.generation-failed` | communication-service, support-service | Communication: customer notification. Support: ticket creation. |
| 59 | `crm.billing.subscription.cancellation-failed` | `crm.billing.subscription.cancellation-failed` | ciam-service, communication-service | CIAM: lifecycle state reconciliation. Communication: alerting. |
| 60 | `crm.billing.dunning.exhausted` | `crm.billing.dunning.exhausted` | communication-service, ciam-service | Communication: final notice delivery. CIAM: churn risk scoring. |
| 61 | `crm.billing.subscription.suspended` | `crm.billing.subscription.suspended` | communication-service, ciam-service | Communication: customer notification. CIAM: lifecycle scoring. |
| 62 | `crm.billing.invoice.overdue` | `crm.billing.invoice.overdue` | billing-service, ciam-service | Billing: dunning trigger. CIAM: customer health score adjustment. |

---

## Cross-Context Reaction Summary

### Consumer → Event Count

| Consuming Service | Events Consumed | Publishing Context(s) |
|---|---|---|
| **communication-service** | 28 | CIAM(7), Sales(8), Support(8), Billing(11), Marketing(5) |
| **ciam-service** | 9 | Support(1), Marketing(2), Communication(1), Billing(3), CIAM(self, 3) |
| **sales-service** | 4 | CIAM(2), Marketing(1), Communication(1) |
| **billing-service** | 3 | CIAM(1), Sales(2) |
| **support-service** | 3 | CIAM(1), Communication(1), Billing(1) |
| **marketing-service** | 3 | CIAM(1), Communication(1), Support(1) |

### Critical Business Workflows (Event Chains)

#### 1. New Customer Onboarding
```
CIAM: customer.registered
  → Sales: opportunity seeding
  → Marketing: lead ingestion
  → Communication: welcome flow
```

#### 2. Opportunity-to-Revenue (Happy Path)
```
CIAM: lead.qualified
  → Sales: opportunity created
Sales: quote.generated → Communication: deliver quote
Sales: quote.accepted → Billing: subscription created
Billing: subscription.created → Communication: welcome email
Billing: invoice.generated → Communication: invoice delivery
Billing: payment.succeeded → Communication: receipt + CIAM: health score+
```

#### 3. Opportunity-to-Revenue (Failure Path)
```
Sales: quote.accepted
  → Billing: (fails) subscription.creation-failed
    → Sales: pipeline follow-up
    → Communication: customer notification

Billing: invoice.generation-failed
  → Communication: customer notification
  → Support: ticket creation
```

#### 4. Customer Churn / Deactivation
```
CIAM: customer.deactivated
  → Support: suspend open tickets
  → Billing: cancel subscriptions
  → Communication: suppress outbound

Billing: dunning.exhausted → CIAM: churn risk scoring
Billing: subscription.suspended → CIAM: lifecycle scoring
CIAM: lifecycle-stage.changed → Sales: pipeline sync
```

#### 5. Support Escalation
```
Support: ticket.escalated → Communication: manager notification
Support: sla.breached → Communication: escalation alerts
Support: ticket.closed
  → Communication: CSAT survey trigger
  → CIAM: customer health scoring
```

#### 6. Marketing Campaign Lifecycle
```
Marketing: campaign.created → Communication: infra prep
Marketing: campaign.scheduled → Communication: schedule delivery
Marketing: campaign.launched → Communication: start delivery
Marketing: engagement.recorded → CIAM: lead scoring
Marketing: conversion.declared
  → Sales: opportunity attribution
  → CIAM: scoring update
Marketing: campaign.completed → Communication: report distribution
```

#### 7. Customer Merge (Cross-Cutting)
```
CIAM: customer.merged → ALL SERVICES: update stale references
CIAM: customer.merge-conflict → ALL SERVICES: resolve conflicts
CIAM: customer.reactivated → ALL SERVICES: re-enable services
```
