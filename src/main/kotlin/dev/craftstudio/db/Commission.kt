package dev.craftstudio.db

import dev.craftstudio.data.developer.DeveloperCommissionPreview
import dev.craftstudio.db.account.Account
import dev.craftstudio.db.account.Accounts
import dev.craftstudio.db.account.accountsDAO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class Commission(
    override val id: Int,
    val title: String,
    val summary: String,
    val requirements: String,
    val fixedPriceAmount: Int,
    val hourlyPriceAmount: Int,
    val minimumReputation: Int,
    val creationTime: Long,
    val expiryTime: Long,
    val status: CommissionStatus,
    val category: CommissionCategory,
    val owner: Access<Account>,
    val developer: Access<Account>? = null,
) : Access<Commission> {
    constructor(row: ResultRow) : this(
        id = row[Commissions.id],
        title = row[Commissions.title],
        summary = row[Commissions.summary],
        requirements = row[Commissions.requirements],
        fixedPriceAmount = row[Commissions.fixedPriceAmount],
        hourlyPriceAmount = row[Commissions.hourlyPriceAmount],
        minimumReputation = row[Commissions.minimumReputation],
        creationTime = row[Commissions.creationTime],
        expiryTime = row[Commissions.expiryTime],
        status = row[Commissions.status],
        category = row[Commissions.category],
        owner = DatabaseAccess(row[Commissions.owner]) { accountsDAO.read(it)!! },
        developer = row[Commissions.developer]?.let { DatabaseAccess(it) { accountsDAO.read(it)!! } },
    )

    val daysRemaining: Long
        get() = (expiryTime - creationTime).milliseconds.inWholeDays

    val expired: Boolean
        get() = daysRemaining < 0

    fun toPreview() = DeveloperCommissionPreview(
        title = title,
        summary = summary,
        commissionId = id,
        fixedPriceAmount = fixedPriceAmount,
        hourlyPriceAmount = hourlyPriceAmount,
        category = category,
    )

    override suspend fun resolve(): Commission = this
}

@Serializable
enum class CommissionStatus {
    /**
     * The buyer has not yet submitted the commission, and can be edited freely.
     */
    @SerialName("draft")
    Draft,

    /**
     * Awaiting moderation, can still be edited freely, but editing moves commission to the back of moderation queue.
     */
    // TODO: implement moderation
    @SerialName("moderation")
    Submitted,

    /**
     * It has now been published. Developers can now view and bid on the commission (assuming the right subscription).
     */
    @SerialName("bidding")
    Bidding,

    /**
     * The buyer has accepted a bid. The pair can now go off-platform and develop the commission. Our job is done!
     * It will remain in this state for 2 weeks, within this two-week period, the buyer can re-submit the commission
     * with minor tweaks to the information, without using another submission token, which will move it back to
     * the [Submitted] state.
     */
    @SerialName("accepted")
    Accepted,

    /**
     * No bids were received within the bid period. Behaves identically as [Accepted],
     * but with an infinite resubmission period.
     */
    @SerialName("expired")
    Expired,

    /**
     * The two-week period has expired, and the buyer has not re-submitted the commission. We now assume the commission
     * has been completed or is in good work, this will server as purely an archive for the developer to look back on.
     * They cannot resubmit.
     */
    @SerialName("archived")
    Archived,
}

@Serializable
enum class CommissionCategory {
    Mod,
    Plugin,
    Modelling,
    Texturing,
    Multi,
}

object Commissions : Table() {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 255)
    val summary = varchar("summary", 255)
    val requirements = varchar("requirements", 10000)
    val fixedPriceAmount = integer("fixed_price_amount")
    val hourlyPriceAmount = integer("hourly_price_amount")
    val minimumReputation = integer("minimum_reputation")
    val creationTime = long("creation_time")
    val expiryTime = long("expiry_time")
    val status = enumeration<CommissionStatus>("status")
    val category = enumeration<CommissionCategory>("category")
    val owner = reference("owner", Accounts.id)
    val developer = reference("developer", Accounts.id).nullable()

    override val primaryKey = PrimaryKey(id)
}

interface CommissionsDAO {
    // CRUD
    suspend fun create(
        title: String,
        summary: String,
        requirements: String,
        fixedPriceAmount: Int,
        hourlyPriceAmount: Int,
        minimumReputation: Int,
        expiryDays: Int,
        accountId: Int,
        status: CommissionStatus,
        category: CommissionCategory,
    ): Commission?

    suspend fun read(id: Int): Commission?
    suspend fun update(id: Int, commission: Commission): Boolean
    suspend fun delete(id: Int): Boolean
    suspend fun all(): List<Commission>

    suspend fun acceptBid(commissionId: Int, bidId: Int): Boolean
}

val commissionsDAO: CommissionsDAO = CommissionsDAOImpl()

class CommissionsDAOImpl : CommissionsDAO {
    override suspend fun all(): List<Commission> = dbQuery {
        Commissions.selectAll().map(::Commission)
    }

    override suspend fun read(id: Int): Commission? = dbQuery {
        Commissions
            .select { Commissions.id eq id }
            .map(::Commission)
            .firstOrNull()
    }

    override suspend fun create(
        title: String,
        summary: String,
        requirements: String,
        fixedPriceAmount: Int,
        hourlyPriceAmount: Int,
        minimumReputation: Int,
        expiryDays: Int,
        accountId: Int,
        status: CommissionStatus,
        category: CommissionCategory,
    ): Commission? = dbQuery {
        val insertStatement = Commissions.insert {
            it[Commissions.title] = title
            it[Commissions.summary] = summary
            it[Commissions.requirements] = requirements
            it[Commissions.fixedPriceAmount] = fixedPriceAmount
            it[Commissions.hourlyPriceAmount] = hourlyPriceAmount
            it[Commissions.minimumReputation] = minimumReputation
            it[Commissions.creationTime] = System.currentTimeMillis()
            it[Commissions.expiryTime] = System.currentTimeMillis() + expiryDays.days.inWholeMilliseconds
            it[Commissions.status] = status
            it[Commissions.category] = category
            it[Commissions.owner] = accountId
            it[Commissions.developer] = null
        }
        insertStatement.resultedValues?.singleOrNull()?.let(::Commission)
    }

    override suspend fun update(id: Int, commission: Commission): Boolean = dbQuery {
        Commissions.update({ Commissions.id eq id }) {
            it[Commissions.title] = commission.title
            it[Commissions.summary] = commission.summary
            it[Commissions.requirements] = commission.requirements
            it[Commissions.fixedPriceAmount] = commission.fixedPriceAmount
            it[Commissions.hourlyPriceAmount] = commission.hourlyPriceAmount
            it[Commissions.minimumReputation] = commission.minimumReputation
            it[Commissions.creationTime] = commission.creationTime
            it[Commissions.expiryTime] = commission.expiryTime
            it[Commissions.status] = commission.status
            it[Commissions.owner] = commission.owner.id
            it[Commissions.developer] = commission.developer?.id
        } > 0
    }

    override suspend fun delete(id: Int): Boolean = dbQuery {
        val deleted = Commissions.deleteWhere { Commissions.id eq id } > 0
        if (deleted) {
            Bids.deleteWhere { Bids.commission eq id }
        }
        deleted
    }

    override suspend fun acceptBid(commissionId: Int, bidId: Int): Boolean = dbQuery {
        val bid = bidsDAO.read(bidId) ?: return@dbQuery false
        if (bid.commission.id != commissionId) return@dbQuery false

        Commissions.update({ Commissions.id eq commissionId }) {
            it[Commissions.developer] = bid.bidder.id
            it[Commissions.status] = CommissionStatus.Accepted
        } > 0 && Bids.update({ Bids.id eq bidId }) {
            it[Bids.accepted] = true
        } > 0
    }
}
