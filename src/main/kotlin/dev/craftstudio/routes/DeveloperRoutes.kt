package dev.craftstudio.routes

import dev.craftstudio.auth.AccountPrinciple
import dev.craftstudio.auth.requireToken
import dev.craftstudio.data.developer.*
import dev.craftstudio.data.respondError
import dev.craftstudio.db.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select

fun Routing.configureDeveloperRoutes() {
    requireToken {
        get("/developer/available-commissions") {
            val account = call.principal<AccountPrinciple>()?.account
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            // authenticate request
            if (!account.isDeveloper)
                return@get call.respond(HttpStatusCode.Forbidden)
            val devAccount = account.developerAccount?.resolve()
                ?: return@get call.respond(HttpStatusCode.Forbidden)

            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 10
            val searchQuery = call.parameters["searchQuery"] ?: ""
            val sortFunction = call.parameters["sortFunction"]?.let { CommissionSortFunction.valueOf(it) }
                ?: CommissionSortFunction.DATE_CREATED
            val inverseSortFunction = call.parameters["invertSort"]?.toBoolean() ?: false

            val commissions = dbQuery {
                Commissions
                    .select { (Commissions.status eq CommissionStatus.Bidding) and (Commissions.title like "%$searchQuery%") and ((Commissions.fixedPriceAmount lessEq devAccount.subscriptionType.fixedOfferLimit) or (Commissions.hourlyPriceAmount lessEq devAccount.subscriptionType.hourlyOfferLimit)) }
                    .orderBy(
                        when (sortFunction) {
                            CommissionSortFunction.DATE_CREATED -> Commissions.creationTime
                            CommissionSortFunction.DATE_EXPIRY -> Commissions.expiryTime minus Commissions.creationTime
                            CommissionSortFunction.FIXED_PRICE -> Commissions.fixedPriceAmount
                            CommissionSortFunction.HOURLY_PRICE -> Commissions.hourlyPriceAmount
                            CommissionSortFunction.REPUTATION -> Commissions.minimumReputation
                        }, if (!inverseSortFunction) SortOrder.DESC else SortOrder.ASC
                    )
                    .limit(pageSize, ((page - 1) * pageSize).toLong())
                    .map { Commission(it).toPreview() }
            }

            val response = GetAvailableCommissionsResponse(
                commissions = commissions
            )

            call.respond(response)
        }

        get("/developer/in-progress-commissions") {
            val account = call.principal<AccountPrinciple>()?.account
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 10

            val commissions: InProgressCommissionsResponse = dbQuery {
                Commissions
                    .select { (Commissions.status eq CommissionStatus.Accepted) and (Commissions.developer eq account.id) }
                    .orderBy(Commissions.creationTime, SortOrder.DESC)
                    .limit(pageSize, ((page - 1) * pageSize).toLong())
                    .map { Commission(it).toPreview() }
            }

            call.respond(commissions)
        }

        get("/developer/commission/{commissionId}") {
            val account = call.principal<AccountPrinciple>()?.account
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (!account.isDeveloper) // save a DB call
                return@get call.respondError(HttpStatusCode.Forbidden, reason = "You are not a developer.")

            val commissionId = call.parameters["commissionId"]
                ?: return@get call.respondError(HttpStatusCode.BadRequest, reason = "Invalid commission ID.")
            val commission = commissionsDAO.read(commissionId.toInt())
                ?: return@get call.respondError(HttpStatusCode.NotFound, reason = "Commission not found.")

            if (commission.developer?.id != account.id)
                return@get call.respondError(HttpStatusCode.Forbidden, reason = "You are not the developer for this commission.")

            call.respond(DeveloperCommissionResponse(commission))
        }

        post("/developer/commission/{commissionId}/submit-bid") {
            val account = call.principal<AccountPrinciple>()?.account
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            if (!account.isDeveloper)
                return@post call.respondError(HttpStatusCode.Forbidden, reason = "You are not a developer.")
            if ((account.developerAccount?.resolve()?.remainingBids ?: 0) < 1)
                return@post call.respondError(HttpStatusCode.Forbidden, reason = "You have ran out of bids this month.")

            val commissionId = call.parameters["commissionId"]?.toIntOrNull()
                ?: return@post call.respondError(HttpStatusCode.BadRequest, reason = "Invalid commission ID.")

            val data = call.receive<SubmitBidRequest>()

            val bid = bidsDAO.create(
                commissionId = commissionId,
                bidderId = account.id,
                fixedPriceAmount = data.fixedBidAmount,
                hourlyPriceAmount = data.hourlyBidAmount,
                testimonial = data.testimony,
            )!!

            call.respond(SubmitBidResponse(success = true, bidId = bid.id))
        }
    }
}