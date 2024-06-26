package dev.craftstudio.routes

import dev.craftstudio.auth.AccountPrinciple
import dev.craftstudio.auth.authenticateUser
import dev.craftstudio.data.developer.*
import dev.craftstudio.data.respondError
import dev.craftstudio.db.*
import dev.craftstudio.db.account.developerAccountDAO
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
    authenticateUser { accountGetter ->
        get("/developer/available-commissions") {
            val account = accountGetter()
            val devAccount = account.developerAccount?.resolve()
                ?: return@get call.respondError(HttpStatusCode.Forbidden, reason = "You are not a developer.")

            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 10
            val searchQuery = call.parameters["searchQuery"] ?: ""
            val sortFunction = call.parameters["sortFunction"]?.let { sort -> CommissionSortFunction.entries.find { e -> e.name == sort } }
                ?: CommissionSortFunction.DATE_CREATED
            val inverseSortFunction = call.parameters["invertSort"]?.toBoolean() ?: false

            // TODO: improve searching functionality. regexp? fuzzy search?
            //       https://www.interviewquery.com/p/sql-fuzzy-matching
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
            val account = accountGetter()
            if (!account.isDeveloper) // save a DB call
                return@get call.respondError(HttpStatusCode.Forbidden, reason = "You are not a developer.")

            val commissionId = call.parameters["commissionId"]
                ?: return@get call.respondError(HttpStatusCode.BadRequest, reason = "Invalid commission ID.")
            val commission = commissionsDAO.read(commissionId.toInt())
                ?: return@get call.respondError(HttpStatusCode.NotFound, reason = "Commission not found.")

            if (commission.developer?.id != account.id) {
                val developer = account.developerAccount!!.resolve()

                if (commission.status != CommissionStatus.Bidding) {
                    return@get call.respond(HttpStatusCode.NotFound)
                }
                if (developer.remainingBids < 1 && developer.subscriptionType.fixedOfferLimit < commission.fixedPriceAmount && developer.subscriptionType.hourlyOfferLimit < commission.hourlyPriceAmount) {
                    return@get call.respond(HttpStatusCode.NotFound)
                }
            }

            call.respond(DeveloperCommissionResponse(commission))
        }

        post("/developer/commission/{commissionId}/submit-bid") {
            val account = accountGetter()
            val devAccount = account.developerAccount?.resolve()
                ?: return@post call.respondError(HttpStatusCode.Forbidden, reason = "You are not a developer.")

            if (devAccount.remainingBids < 1)
                return@post call.respondError(
                    HttpStatusCode.Forbidden,
                    reason = "You have ran out of bids this month."
                )

            val commissionId = call.parameters["commissionId"]?.toIntOrNull()
                ?: return@post call.respondError(HttpStatusCode.BadRequest, reason = "Invalid commission ID.")

            val data = call.receive<SubmitBidRequest>()

            val bid = bidsDAO.create(
                commissionId = commissionId,
                bidderId = account.id,
                fixedPriceAmount = data.fixedBidAmount,
                hourlyPriceAmount = data.hourlyBidAmount,
                testimonial = data.testimony,
            ) ?: return@post call.respondError(
                HttpStatusCode.InternalServerError,
                reason = "Failed to create bid."
            )

            developerAccountDAO.updateBidCount(
                devAccount.id,
                devAccount.remainingBids - 1,
                devAccount.totalBids + 1
            )

            call.respond(SubmitBidResponse(success = true, bidId = bid.id))
        }
    }

}