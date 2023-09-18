package dev.craftstudio.db

import dev.craftstudio.data.developer.DeveloperCommissionPreview
import dev.craftstudio.db.DatabaseFactory.dbQuery
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class Commission(
    override val commissionId: Int,
    val title: String,
    val summary: String,
    val requirements: String,
    val fixedPriceAmount: Int,
    val hourlyPriceAmount: Int,
    val minimumReputation: Int,
    val creationTime: Long,
    val expiryTime: Long,
    val status: CommissionStatus,
    val owner: ResolveableAccount,
    val developer: ResolveableAccount? = null,
) : ResolveableCommission {
    constructor(row: ResultRow) : this(
        commissionId = row[Commissions.id],
        title = row[Commissions.title],
        summary = row[Commissions.summary],
        requirements = row[Commissions.requirements],
        fixedPriceAmount = row[Commissions.fixedPriceAmount],
        hourlyPriceAmount = row[Commissions.hourlyPriceAmount],
        minimumReputation = row[Commissions.minimumReputation],
        creationTime = row[Commissions.creationTime],
        expiryTime = row[Commissions.expiryTime],
        status = row[Commissions.status],
        owner = DatabaseAccount(row[Commissions.owner], accountsDAO),
        developer = row[Commissions.developer]?.let { DatabaseAccount(it, accountsDAO) },
    )

    val daysRemaining: Long
        get() = (expiryTime - creationTime).milliseconds.inWholeDays

    val expired: Boolean
        get() = daysRemaining < 0

    fun toPreview() = DeveloperCommissionPreview(
        title = title,
        summary = summary,
        commissionId = commissionId,
        fixedPriceAmount = fixedPriceAmount,
        hourlyPriceAmount = hourlyPriceAmount,
    )

    override suspend fun resolve(): Commission = this
}

@Serializable
enum class CommissionStatus { Draft, Bidding, InProgress, Completed, Expired }

interface ResolveableCommission {
    val commissionId: Int

    suspend fun resolve(): Commission
}

class DatabaseCommission(
    override val commissionId: Int,
    private val commissionsDAO: CommissionsDAO,
) : ResolveableCommission {
    private var commission: Commission? = null

    override suspend fun resolve(): Commission {
        if (commission == null) {
            commission = commissionsDAO.read(commissionId) ?: error("Commission $commissionId does not exist")
        }
        return commission!!
    }
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
    val status = enumeration("status", CommissionStatus::class)
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
    ): Commission?

    suspend fun read(id: Int): Commission?
    suspend fun update(id: Int, commission: Commission): Boolean
    suspend fun delete(id: Int): Boolean
    suspend fun all(): List<Commission>

    suspend fun acceptBid(commissionId: Int, bidId: Int): Boolean
}

val commissionsDB: CommissionsDAO = CommissionsDAOImpl()

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
            it[Commissions.owner] = commission.owner.accountId
            it[Commissions.developer] = commission.developer?.accountId
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
        val bid = bidsDB.read(bidId) ?: return@dbQuery false
        if (bid.commission.commissionId != commissionId) return@dbQuery false

        Commissions.update({ Commissions.id eq commissionId }) {
            it[Commissions.developer] = bid.bidder.accountId
            it[Commissions.status] = CommissionStatus.InProgress
        } > 0 && Bids.update({ Bids.id eq bidId }) {
            it[Bids.accepted] = true
        } > 0
    }
}
