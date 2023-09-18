package dev.craftstudio.db

import dev.craftstudio.db.DatabaseFactory.dbQuery
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

data class Bid(
    val id: Int,
    val commission: ResolveableCommission,
    val bidder: ResolveableAccount,
    val fixedPriceAmount: Int,
    val hourlyPriceAmount: Int,
    val creationTime: Long,
    val developerTestimonial: String?,
    val accepted: Boolean,
) {
    constructor(row: ResultRow) : this(
        id = row[Bids.id],
        commission = DatabaseCommission(row[Bids.commission], commissionsDB),
        bidder = DatabaseAccount(row[Bids.bidder], accountsDAO),
        fixedPriceAmount = row[Bids.fixedPriceAmount],
        hourlyPriceAmount = row[Bids.hourlyPriceAmount],
        creationTime = row[Bids.creationTime],
        developerTestimonial = row[Bids.developerTestimonial],
        accepted = row[Bids.accepted],
    )
}

object Bids : Table() {
    val id = integer("id").autoIncrement()

    val commission = reference("commission", Commissions.id)
    val bidder = reference("bidder", Accounts.id)

    val fixedPriceAmount = integer("fixed_price_amount")
    val hourlyPriceAmount = integer("hourly_price_amount")

    val creationTime = long("creation_time")

    val developerTestimonial = varchar("developer_testimonial", 10000).nullable()

    val accepted = bool("accepted")

    override val primaryKey = PrimaryKey(id)
}

interface BidsDAO {
    // CRUD
    suspend fun create(
        commissionId: Int,
        bidderId: Int,
        fixedPriceAmount: Int,
        hourlyPriceAmount: Int,
        testimonial: String? = null,
    ): Bid?

    suspend fun read(id: Int): Bid?
    suspend fun update(id: Int, bid: Bid): Boolean
    suspend fun delete(id: Int): Boolean
    suspend fun all(): List<Bid>

    // Other
    suspend fun getBidsForCommission(commissionId: Int): List<Bid>
    suspend fun getBidsForAccount(accountId: Int): List<Bid>
}

val bidsDB: BidsDAO = BidsDAOImpl()

class BidsDAOImpl : BidsDAO {
    override suspend fun create(commissionId: Int, bidderId: Int, fixedPriceAmount: Int, hourlyPriceAmount: Int, testimonial: String?): Bid? = dbQuery {
        val insertStatement = Bids.insert {
            it[this.commission] = commissionId
            it[this.bidder] = bidderId
            it[this.fixedPriceAmount] = fixedPriceAmount
            it[this.hourlyPriceAmount] = hourlyPriceAmount
            it[this.creationTime] = System.currentTimeMillis()
            it[this.developerTestimonial] = testimonial
            it[this.accepted] = false
        }

        insertStatement.resultedValues?.singleOrNull()?.let(::Bid)
    }

    override suspend fun read(id: Int): Bid? = dbQuery {
        Bids
            .select { Bids.id eq id }
            .map(::Bid)
            .firstOrNull()
    }

    override suspend fun update(id: Int, bid: Bid): Boolean = dbQuery {
        Bids.update({ Bids.id eq id }) {
            it[commission] = bid.commission.commissionId
            it[bidder] = bid.bidder.accountId
            it[fixedPriceAmount] = bid.fixedPriceAmount
            it[hourlyPriceAmount] = bid.hourlyPriceAmount
            it[creationTime] = bid.creationTime
            it[developerTestimonial] = bid.developerTestimonial
            it[accepted] = bid.accepted
        } > 0
    }

    override suspend fun delete(id: Int): Boolean = dbQuery {
        Bids.deleteWhere { Bids.id eq id } > 0
    }

    override suspend fun all(): List<Bid> = dbQuery {
        Bids.selectAll().map(::Bid)
    }

    override suspend fun getBidsForCommission(commissionId: Int): List<Bid> = dbQuery {
        Bids
            .select { Bids.commission eq commissionId }
            .map(::Bid)
    }

    override suspend fun getBidsForAccount(accountId: Int): List<Bid> = dbQuery {
        Bids
            .select { Bids.bidder eq accountId }
            .map(::Bid)
    }
}