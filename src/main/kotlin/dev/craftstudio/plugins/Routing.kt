package dev.craftstudio.plugins

import dev.craftstudio.auth.AccountPrinciple
import dev.craftstudio.auth.requireToken
import dev.craftstudio.data.requests.AccountDetails
import dev.craftstudio.data.requests.BidInfo
import dev.craftstudio.data.requests.ErrorResponse
import dev.craftstudio.data.requests.buyer.GetCommissionBidsResponse
import dev.craftstudio.data.requests.buyer.SubmitCommissionRequestData
import dev.craftstudio.data.requests.developer.*
import dev.craftstudio.db.*
import dev.craftstudio.db.DatabaseFactory.dbQuery
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

fun Application.configureRouting() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.toString()))
        }
    }

    routing {
        requireToken {
            post("/buyer/submit-commission") {
                val account = call.principal<AccountPrinciple>()?.account
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

//                if (account.type != AccountType.BUYER)
//                    return@post call.respond(HttpStatusCode.Forbidden)

                val data = call.receive<SubmitCommissionRequestData>()

                val success = commissionsDB.create(
                    data.title,
                    data.summary,
                    data.requirements,
                    data.fixedPriceAmount,
                    data.hourlyPriceAmount,
                    data.expiryDays,
                    data.minimumReputation,
                    account.accountId,
                    CommissionStatus.Bidding,
                ) != null

                call.respond(if (success) HttpStatusCode.Accepted else HttpStatusCode.InternalServerError)
            }

            get("/buyer/commission/{commissionId}/bids") {
                val commissionId = call.parameters["commissionId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val account = call.principal<AccountPrinciple>()?.account
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

//                if (account.type != AccountType.BUYER)
//                    return@get call.respond(HttpStatusCode.Forbidden)
//                if (account.accountId != commissionsDB.read(commissionId)?.owner?.accountId)
//                    return@get call.respond(HttpStatusCode.Forbidden)

                val bids = bidsDB.getBidsForCommission(commissionId).map {
                    BidInfo(
                        bidId = it.id,
                        bidder = AccountDetails(it.bidder.resolve()),
                        fixedBidAmount = it.fixedPriceAmount,
                        hourlyBidAmount = it.hourlyPriceAmount,
                        testimony = it.developerTestimonial,
                    )
                }

                call.respond(bids as GetCommissionBidsResponse)
            }

            get("/buyer/commission/{commissionId}/accept-bid") {
                // get parameters
                val commissionId = call.parameters["commissionId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val bidId = call.parameters["bidId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                // get account
                val account = call.principal<AccountPrinciple>()?.account
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                if (account.type != AccountType.Buyer)
                    return@get call.respond(HttpStatusCode.Forbidden)

                // get commission
                val commission = commissionsDB.read(commissionId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                if (commission.owner.accountId != account.accountId)
                    return@get call.respond(HttpStatusCode.Forbidden)

                // get bid
                val bid = bidsDB.read(bidId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                if (bid.commission.commissionId != commissionId)
                    return@get call.respond(HttpStatusCode.Forbidden)

                // update commission details
                commissionsDB.acceptBid(commissionId, bidId)

                call.respond(HttpStatusCode.OK)
            }

            get("/developer/available-commissions") {
                val account = call.principal<AccountPrinciple>()?.account
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
//                if (account.type != AccountType.DEVELOPER)
//                    return@get call.respond(HttpStatusCode.Forbidden)

                val page = call.parameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 10
                val searchQuery = call.parameters["searchQuery"] ?: ""
                val sortFunction = call.parameters["sortFunction"]?.let { CommissionSortFunction.valueOf(it) }
                    ?: CommissionSortFunction.DATE_CREATED
                val inverseSortFunction = call.parameters["invertSort"]?.toBoolean() ?: false

                val commissions = dbQuery { // TODO: decide whether to keep DAO for everything
                    Commissions
                        .select { (Commissions.status eq CommissionStatus.Bidding) and (Commissions.title like "%$searchQuery%") }
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

            get("/developer/commission/{commissionId}") {
                val commissionId = call.parameters["commissionId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val commission = commissionsDB.read(commissionId.toInt())
                    ?: return@get call.respond(HttpStatusCode.NotFound)

                call.respond(DeveloperCommissionResponse(commission))
            }

            post("/developer/commission/{commissionId}/submit-bid") {
                val account = call.principal<AccountPrinciple>()?.account
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
//                if (account.type != AccountType.DEVELOPER)
//                    return@post call.respond(HttpStatusCode.Forbidden)

                val commissionId = call.parameters["commissionId"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest)

                val data = call.receive<SubmitBidRequest>()

                val bid = bidsDB.create(
                    commissionId = commissionId,
                    bidderId = account.accountId,
                    fixedPriceAmount = data.fixedBidAmount,
                    hourlyPriceAmount = data.hourlyBidAmount,
                    testimonial = data.testimony,
                )

                if (bid != null)
                    call.respond(SubmitBidResponse(success = true, bidId = bid.id))
                else
                    call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}
