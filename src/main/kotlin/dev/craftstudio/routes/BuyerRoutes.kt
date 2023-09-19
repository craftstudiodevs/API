package dev.craftstudio.routes

import dev.craftstudio.auth.AccountPrinciple
import dev.craftstudio.auth.requireToken
import dev.craftstudio.data.AccountDetails
import dev.craftstudio.data.BidInfo
import dev.craftstudio.data.buyer.BuyerCommissionPreview
import dev.craftstudio.data.buyer.GetCommissionBidsResponse
import dev.craftstudio.data.buyer.GetMyCommissionsResponse
import dev.craftstudio.data.buyer.SubmitCommissionRequestData
import dev.craftstudio.data.respondError
import dev.craftstudio.db.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*

fun Routing.configureBuyerRoutes() {
    requireToken {
        get("/buyer/list-commissions") {
            val account = call.principal<AccountPrinciple>()?.account
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 10

            val buyerAccount = account.buyerAccount?.resolve()
                ?: return@get call.respondError(HttpStatusCode.Forbidden, reason = "You are not a buyer")

            val commissions: GetMyCommissionsResponse = dbQuery {
                Commissions
                    .select { Commissions.owner eq buyerAccount.id }
                    .limit(pageSize, ((page - 1) * pageSize).toLong())
                    .orderBy(Commissions.status to SortOrder.DESC, Commissions.creationTime to SortOrder.DESC)
                    .map(::Commission)
                    .map {
                        BuyerCommissionPreview(
                            commissionId = it.id,
                            title = it.title,
                            summary = it.summary,
                            status = it.status,
                            developer = it.developer?.resolve()?.let(::AccountDetails),
                        )
                    }
            }

            call.respond(commissions)
        }

        post("/buyer/submit-commission") {
            val account = call.principal<AccountPrinciple>()?.account
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            if ((account.buyerAccount?.resolve()?.remainingCommissions ?: 0) < 1)
                return@post call.respond(HttpStatusCode.Forbidden)

            val data = call.receive<SubmitCommissionRequestData>()

            val success = commissionsDAO.create(
                data.title,
                data.summary,
                data.requirements,
                data.fixedPriceAmount,
                data.hourlyPriceAmount,
                data.expiryDays,
                data.minimumReputation,
                account.id,
                CommissionStatus.Bidding,
            ) != null

            call.respond(if (success) HttpStatusCode.Accepted else HttpStatusCode.InternalServerError)
        }

        get("/buyer/commission/{commissionId}/bids") {
            val commissionId = call.parameters["commissionId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val account = call.principal<AccountPrinciple>()?.account
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            if (!account.isBuyer) // saves a DB query
                return@get call.respond(HttpStatusCode.Forbidden)
            if (commissionsDAO.read(commissionId)?.owner?.id != account.id)
                return@get call.respond(HttpStatusCode.Forbidden)

            val bids: GetCommissionBidsResponse = bidsDAO.getBidsForCommission(commissionId).map {
                BidInfo(
                    bidId = it.id,
                    bidder = AccountDetails(it.bidder.resolve()),
                    fixedBidAmount = it.fixedPriceAmount,
                    hourlyBidAmount = it.hourlyPriceAmount,
                    testimony = it.developerTestimonial,
                )
            }

            call.respond(bids)
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
            if (!account.isBuyer)
                return@get call.respond(HttpStatusCode.Forbidden)

            // get commission
            val commission = commissionsDAO.read(commissionId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            if (commission.owner.id != account.id)
                return@get call.respond(HttpStatusCode.Forbidden)

            // get bid
            val bid = bidsDAO.read(bidId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            if (bid.commission.id != commissionId)
                return@get call.respond(HttpStatusCode.Forbidden)

            // update commission details
            val accepted = commissionsDAO.acceptBid(commissionId, bidId)

            call.respond(if (accepted) HttpStatusCode.OK else HttpStatusCode.InternalServerError)
        }
    }
}