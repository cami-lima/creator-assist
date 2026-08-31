package br.com.creatorassist.memory

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * State that must survive between monthly executions: which tax
 * regime the creator has already chosen, and how much revenue they
 * have accumulated so far this year (needed to detect the MEI
 * (micro-entrepreneur) revenue cap being exceeded — see test case
 * F5 in docs/). Without this, the agent would have to ask the user
 * to re-enter everything on every execution.
 */
@Entity
@Table(name = "creator_month_state")
data class CreatorMonthState(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val creatorId: String,

    @Column(nullable = false)
    val referenceMonth: String, // format "2026-05"

    @Column(nullable = false)
    val taxRegime: String, // e.g. "SELF_EMPLOYED", "MEI"

    @Column(nullable = false)
    val yearToDateRevenue: BigDecimal,

    @Column(nullable = false)
    val taxPaidThisMonth: BigDecimal
)
