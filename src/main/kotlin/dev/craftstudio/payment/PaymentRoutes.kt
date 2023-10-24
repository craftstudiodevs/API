package dev.craftstudio.payment

import com.google.gson.JsonSyntaxException
import com.stripe.Stripe
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.model.Price
import com.stripe.model.billingportal.Session as BillingPortalSession
import com.stripe.model.checkout.Session as CheckoutSession
import com.stripe.net.ApiResource
import com.stripe.net.Webhook
import com.stripe.param.PriceListParams
import dev.craftstudio.auth.authenticateUser
import com.stripe.param.billingportal.SessionCreateParams as BillingPortalSessionCreateParams
import com.stripe.param.checkout.SessionCreateParams as CheckoutSessionCreateParams
import dev.craftstudio.data.respondError
import dev.craftstudio.db.account.*
import dev.craftstudio.utils.Environment
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlin.jvm.optionals.getOrNull

val LOGGER = KtorSimpleLogger("Payment Routes")

// TODO: figure out a way to provision free-tier subscriptions without relying on stripe's webhooks

// USEFUL RESOURCES FOR IMPLEMENTING STRIPE PAYMENT FLOW
// Java Example, Basic Subscription - https://github.com/stripe-samples/checkout-single-subscription/blob/main/server/java/src/main/java/com/stripe/sample/Server.java
// Guide to provisioning subscriptions - https://stripe.com/docs/billing/subscriptions/build-subscriptions?ui=stripe-hosted#provision-and-monitor
// Subscription webhooks - https://stripe.com/docs/billing/subscriptions/webhooks
fun Application.configurePaymentRoutes() {
    Stripe.apiKey = Environment.STRIPE_SECRET

    routing {
        authenticateUser { accountGetter ->
            // We are responsible for creating a checkout for the user.
            // This means we are in control of when the user can pay us, and we can specify
            // explicitly what they are paying for and the price. Because we are so in control
            // it is very easy to manage the payment flow and update our database accordingly.
            // This redirects the user to stripe's site to checkout
            //
            // Example: /payment/create-checkout-session?type=buyer&plan=unlimited&currency=usd
            // TODO: revert this to a POST request. GET is easier for testing in chrome
            get("/payment/create-checkout-session") {
                val account = accountGetter()

                // get what account type of subscription we're trying to purchase
                val accountType = call.parameters["type"]
                    ?: return@get call.respondError(HttpStatusCode.BadRequest, "Missing type")
                // get the specific plan for this account type
                val subscriptionPlanName = call.parameters["plan"]
                    ?: return@get call.respondError(HttpStatusCode.BadRequest, "Missing product")

                // retrieve the stripe product id from the subscription plan name and account type
                val stripeProduct = when (accountType) {
                    "buyer" -> {
                        // make sure the account has already been created as a buyer thru signup.
                        // subscriptions shouldn't implicitly register people as buyers or developers
                        if (!account.isBuyer) {
                            return@get call.respondError(HttpStatusCode.BadRequest, "You are not a buyer")
                        }

                        // get the subscription type from the name
                        val newType = BuyerSubscriptionType.allTypes.values.find { it.name == subscriptionPlanName }
                            ?: return@get call.respondError(HttpStatusCode.BadRequest, "Invalid product")

                        newType.stripeId
                    }

                    "developer" -> {
                        // make sure the account has already been created as a developer thru signup.
                        // subscriptions shouldn't implicitly register people as buyers or developers
                        if (!account.isDeveloper) {
                            return@get call.respondError(HttpStatusCode.BadRequest, "You are not a developer")
                        }

                        // get the subscription type from the name
                        val newType = DeveloperSubscriptionType.allTypes.values.find { it.name == subscriptionPlanName }
                            ?: return@get call.respondError(HttpStatusCode.BadRequest, "Invalid product")

                        newType.stripeId
                    }

                    else -> return@get call.respondError(HttpStatusCode.BadRequest, "Invalid type")
                }

                // 3 letter currency code. e.g. USD, CAD, EUR, GBP
                val currency = call.parameters["currency"]
                    ?: return@get call.respondError(HttpStatusCode.BadRequest, "Missing currency")

                // the same subscription plan can have multiple prices for different currencies
                // and other properties like discount prices. we want the default price for the specified currency
                val price = PriceListParams.builder().apply {
                    setProduct(stripeProduct)
                    setCurrency(currency)
                }.build().let { Price.list(it) }.data.first()

                val params = CheckoutSessionCreateParams.builder().apply {
                    // adding the actual subscription to the checkout session
                    addLineItem(
                        CheckoutSessionCreateParams.LineItem.builder().apply {
                            setPrice(price.id)
                            setQuantity(1L)
                        }.build()
                    )
                    // specify we're in subscription mode
                    setMode(CheckoutSessionCreateParams.Mode.SUBSCRIPTION)
                    // once the payment has completed successfully redirect to this URL
                    // this is triggered after all the webhooks and is truly the last thing to happen
                    setSuccessUrl("http://localhost:8080/payment/callback?success=true&session_id={CHECKOUT_SESSION_ID}")
                    // if the payment fails, redirect to this URL
                    setCancelUrl("http://localhost:8080/payment/callback?success=false")
                    // if we already have a customer id for this account, use it. this pre-fills various fields
                    account.stripeCustomerId?.let { setCustomer(it) }
                        ?: setCustomerEmail(account.email) // can't have customer and email at the same time
                    // very important. correlates the checkout with a specific account in our database.
                    // when retrieving a checkout session thru webhooks, we will always have this to correlate to an account
                    setClientReferenceId(account.id.toString())
                }.build()
                // actually sends the request build above ^
                val session = CheckoutSession.create(params)

                // redirect to stripe's site to checkout
                call.respondRedirect(session.url)
            }

            // A portal session is where users can manage their subscriptions and payment methods
            // in the future this could possibly be on-site instead of a redirect to stripe's site
            //
            // Example: /payment/create-portal-session?return_url=http://localhost:8080/account
            get("/payment/create-portal-session") {
                val account = accountGetter()

                val params = BillingPortalSessionCreateParams.builder().apply {
                    setCustomer(account.stripeCustomerId)
                    setReturnUrl(call.parameters["return_url"]!!)
                }.build()
                val session = BillingPortalSession.create(params)

                call.respondRedirect(session.url)
            }
        }

        // called after payment has been fully completed (or failed)
        get("/payment/callback") {
            val success = call.parameters["success"]?.toBoolean() ?: false

            if (success) {
                call.respondText("Thank you for your purchase!")
            } else {
                call.respondText("Payment failed")
            }


        }

        // this is called by stripe directly and contains key events
        // that we need to handle subscriptions
        // non-exhaustive list of events: https://stripe.com/docs/billing/subscriptions/webhooks
        post("/payment/webhook") {
            val payload = call.receive<String>()

            // construct the event from the raw string provided
            val event = try {
                // events are signed with our secret key to prevent spoofing
                call.request.header("Stripe-Signature")?.let { sig ->
                    try {
                        Webhook.constructEvent(payload, sig, Environment.STRIPE_WH_SECRET!!)
                    } catch (e: SignatureVerificationException) {
                        return@post call.respondError(HttpStatusCode.BadRequest, "Invalid signature")
                    }
                } ?: ApiResource.GSON.fromJson(payload, Event::class.java)
            } catch (e: JsonSyntaxException) {
                return@post call.respondError(HttpStatusCode.BadRequest, "Invalid payload")
            }

            // get the object from the event. essentially the payload of the event
            val stripeObject = event.dataObjectDeserializer.`object`.getOrNull()
                ?: return@post call.respondError(HttpStatusCode.BadRequest, "Invalid payload")

            // TODO: implement cancelling, pausing, resuming subscriptions
            handleWebhookEvent(stripeObject, event.type)
        }
    }
}

