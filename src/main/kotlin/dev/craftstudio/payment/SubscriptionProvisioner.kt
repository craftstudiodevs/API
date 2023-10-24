package dev.craftstudio.payment

import com.stripe.exception.StripeException
import com.stripe.model.Invoice
import com.stripe.model.Subscription
import dev.craftstudio.data.respondError
import dev.craftstudio.db.account.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun provisionSubscriptionFirstTime(invoice: Invoice, call: ApplicationCall) {
    val subscription = Subscription.retrieve(invoice.subscription)
    if (subscription.status != "active")
        LOGGER.warn("Tried to provision subscription that is not active. ${invoice.subscription}")

    // TODO: could be bad that we could potentially fail out of the function without cancelling the previous subscription
    val customerId = invoice.customer
        ?: return call.respondError(HttpStatusCode.BadRequest, "Invalid customer id")
    val account = accountsDAO.readByStripeCustomerId(customerId)
        ?: return call.respondError(HttpStatusCode.BadRequest, "Invalid customer id")

    val planId = invoice.lines.data[0].plan.product // assume there is only one thing in the invoice

    // could be either a buyer subscription or a developer subscription, so we need to check both
    BuyerSubscriptionType.getSubscriptionTypeFromStripeId(planId)
        ?.let { buyerSubscriptionType ->
            // cancel the previous subscription plan to prevent double billing
            buyerAccountDAO.read(account.id)?.subscriptionId?.let { cancelSubscription(it) }

            // update the account with the new subscription type
            buyerAccountDAO.updateSubscriptionType(account.id, buyerSubscriptionType, subscription.id, false)
        }
        ?: DeveloperSubscriptionType.getSubscriptionTypeFromStripeId(planId)
            ?.let { developerSubscriptionType ->
                // cancel the previous subscription plan to prevent double billing
                developerAccountDAO.read(account.id)?.subscriptionId?.let { cancelSubscription(it) }

                // update the account with the new subscription type
                developerAccountDAO.updateSubscriptionType(account.id, developerSubscriptionType, subscription.id, false)
                println("UPDATED SUBSCRIPTION TYPE TO ${developerSubscriptionType.name}")
            }
        ?: return call.respondError(HttpStatusCode.BadRequest, "Invalid plan id")

    LOGGER.info("Successfully provisioned subscription for account ${account.id} with plan $planId")
    call.respond(HttpStatusCode.OK)
}

fun cancelSubscription(subscriptionId: String) {
    val subscription = Subscription.retrieve(subscriptionId)
    try {
        // this way of cancelling prevents the user from being able to
        // renew this subscription later, so we can ensure that there is only ever one plan
        subscription.cancel()
    } catch (e: StripeException) {
        e.printStackTrace()
    }
}

suspend fun provisionSubscriptionReturning(invoice: Invoice, call: ApplicationCall) {
    val account = accountsDAO.readByStripeCustomerId(invoice.customer)
        ?: return call.respondError(HttpStatusCode.BadRequest, "Invalid customer id")

    // finds the subscription associated with this customer to reset limits
    // this could return without a match if this is a new subscription and doesn't match the one in our DB,
    // which will safely be handled in checkout.session.completed

    // could be either a buyer subscription or a developer subscription, so we need to check both
    account.buyerAccount?.resolve()
        ?.takeIf { it.subscriptionId == invoice.subscription }
        ?.let {
            // reset the remaining commissions to the max amount
            buyerAccountDAO.updateCommissionCount(
                it.id,
                it.subscriptionType.commissionsPerMonth,
                it.totalCommissions
            )
        }
        ?: account.developerAccount?.resolve()
            ?.takeIf { it.subscriptionId == invoice.subscription }
            ?.let {
                // reset the remaining bids to the max amount
                developerAccountDAO.updateBidCount(
                    it.id,
                    it.subscriptionType.bidsPerMonth,
                    it.totalBids
                )
            }
        ?: return call.respondError(HttpStatusCode.BadRequest, "Invalid subscription id")

    LOGGER.info("Successfully re-provisioned subscription for account ${account.id} with plan ${invoice.lines.data[0].plan.product}")
    call.respond(HttpStatusCode.OK)
}