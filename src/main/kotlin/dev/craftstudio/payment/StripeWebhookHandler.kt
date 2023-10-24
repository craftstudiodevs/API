package dev.craftstudio.payment

import com.stripe.model.Invoice
import com.stripe.model.StripeObject
import com.stripe.model.Subscription
import dev.craftstudio.data.respondError
import dev.craftstudio.db.account.accountsDAO
import io.ktor.http.*
import com.stripe.model.checkout.Session as CheckoutSession
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*

typealias RouteContext = PipelineContext<Unit, ApplicationCall>

suspend fun RouteContext.handleWebhookEvent(stripeObject: StripeObject, eventType: String) = when (eventType) {
    // This webhook is called when a user completes the checkout process
    // it is only called on the initial payment of a subscription, not the returning ones
    "checkout.session.completed" -> handleCheckoutSessionCompleted(stripeObject as CheckoutSession)

    "customer.subscription.updated" -> handleCustomerSubscriptionUpdated(stripeObject as Subscription)

    // This webhook should be used to provision
    // a subscription for repeating payments, not the initial one
    "invoice.paid" -> handleInvoicePaid(stripeObject as Invoice)

    // Should notify users about failed payment
    "invoice.payment_failed" -> handleInvoicePaymentFailed(stripeObject as Invoice)

    "customer.subscription.deleted" -> handleCustomerSubscriptionDeleted(stripeObject as Subscription)

    else -> {
        LOGGER.warn("Unhandled event type: $eventType")
        call.respond(HttpStatusCode.OK)
    }
}

suspend fun RouteContext.handleCheckoutSessionCompleted(session: CheckoutSession) {
    LOGGER.info("Received 'checkout.session.completed' event for checkout session ${session.id}.")

    // clientReferenceId is our db's account id that we set when creating the checkout session in /payment/create-checkout-session
    val accountId = session.clientReferenceId?.toIntOrNull()
        ?: return call.respondError(HttpStatusCode.BadRequest, "Invalid client reference")

    // update the account with the customer id
    accountsDAO.updateStripeCustomerId(accountId, session.customer)

    // provision the subscription
    val invoice = Invoice.retrieve(session.invoice)
    return provisionSubscriptionFirstTime(invoice, call)
}

suspend fun RouteContext.handleCustomerSubscriptionUpdated(subscription: Subscription) {
    val customerId = subscription.customer
    val account = accountsDAO.readByStripeCustomerId(customerId)?.id
        ?: return call.respondError(HttpStatusCode.BadRequest, "Invalid customer id")

    when (subscription.status) {
        "active" -> {

        }

        "incomplete" -> {

        }

        "incomplete_expired" -> {

        }

        "past_due" -> {

        }

        "canceled" -> {

        }

        "unpaid" -> {

        }
    }

    call.respond(HttpStatusCode.OK)
}

suspend fun RouteContext.handleCustomerSubscriptionDeleted(subscription: Subscription) {
    call.respond(HttpStatusCode.OK)
}

suspend fun RouteContext.handleInvoicePaid(invoice: Invoice) {
    return provisionSubscriptionReturning(invoice, call)
}

suspend fun RouteContext.handleInvoicePaymentFailed(invoice: Invoice) {
    call.respond(HttpStatusCode.OK)
}
