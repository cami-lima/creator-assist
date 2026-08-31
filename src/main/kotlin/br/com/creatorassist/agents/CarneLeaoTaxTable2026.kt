package br.com.creatorassist.agents

import java.math.BigDecimal
import java.math.RoundingMode

data class VerificationResult(
    val passed: Boolean,
    val recalculatedTax: BigDecimal,
    val difference: BigDecimal
)

/**
 * Source of the current "Carne-Leao" tax table (Brazil's monthly
 * withholding tax for self-employed / freelance income). Under Law
 * 15.270/2025, 2026 values are: tax-free up to R$5,000, a phase-out
 * band up to R$7,350, and the regular bracket above that.
 *
 * In production this should come from a search tool, the table
 * changes by law, it cannot stay hardcoded forever. For the hackathon's
 * scope we fix the currently valid version here, explicitly and
 * documented, instead of leaving it implicit inside a prompt.
 */
object CarneLeaoTaxTable2026 {
    private val exemptionThreshold = BigDecimal("5000.00")
    private val phaseOutCeiling = BigDecimal("7350.00")
    private val topBracketRate = BigDecimal("0.275")
    private val deductibleAmount = BigDecimal("908.73")

    fun calculateTax(base: BigDecimal): BigDecimal {
        val b = base.setScale(2, RoundingMode.HALF_UP)
        val tax = when {
            b <= exemptionThreshold -> BigDecimal.ZERO
            b <= phaseOutCeiling -> calculatePhaseOutBand(b)
            else -> calculateTopBracket(b)
        }
        return tax.setScale(2, RoundingMode.HALF_UP)
    }

    private fun calculateTopBracket(base: BigDecimal): BigDecimal =
        base.multiply(topBracketRate).subtract(deductibleAmount)

    private fun calculatePhaseOutBand(base: BigDecimal): BigDecimal {
        val fullTax = calculateTopBracket(base)
        val ratio = base.subtract(exemptionThreshold)
            .divide(phaseOutCeiling.subtract(exemptionThreshold), 10, RoundingMode.HALF_UP)
        return fullTax.multiply(ratio)
    }

    /**
     * Independent verification: re-derives the phase-out band's result
     * by algebraically expanding the formula into standard quadratic
     * form (a·x² + b·x + c) / width, instead of literally re-evaluating
     * the same "(x − exemption) / width × (rate·x − deduction)" a
     * second time. The two forms are mathematically equivalent, but
     * arrive there via different arithmetic operations (factor-and-
     * multiply vs. polynomial expansion), a coding mistake in one is
     * very unlikely to reproduce itself, identically, in the other.
     * This is what makes it a genuine independent check rather than
     * running the same three lines twice.
     *
     * Derivation: let x = base, E = exemption, D = deduction, R = rate,
     * W = width (ceiling − exemption).
     *   original:  tax(x) = (x − E)/W × (R·x − D)
     *   expanded:  tax(x) = [R·x² − (D + E·R)·x + E·D] / W
     */
    fun verify(base: BigDecimal, calculatedTax: BigDecimal): VerificationResult {
        val b = base.setScale(2, RoundingMode.HALF_UP)
        val exemption = BigDecimal("5000.00")
        val ceiling = BigDecimal("7350.00")
        val rate = BigDecimal("0.275")
        val deduction = BigDecimal("908.73")
        val width = ceiling.subtract(exemption)

        val expectedTax: BigDecimal = when {
            b <= exemption -> BigDecimal.ZERO
            b <= ceiling -> {
                val coefA = rate
                val coefB = deduction.add(exemption.multiply(rate)).negate()
                val coefC = exemption.multiply(deduction)
                val numerator = coefA.multiply(b).multiply(b)
                    .add(coefB.multiply(b))
                    .add(coefC)
                numerator.divide(width, 10, RoundingMode.HALF_UP)
            }
            else -> b.multiply(rate).subtract(deduction)
        }.setScale(2, RoundingMode.HALF_UP)

        val difference = calculatedTax.subtract(expectedTax).abs()
        val withinTolerance = difference <= BigDecimal("0.05")
        val withinBounds = calculatedTax.signum() >= 0 && calculatedTax <= b

        return VerificationResult(
            passed = withinTolerance && withinBounds,
            recalculatedTax = expectedTax,
            difference = difference
        )
    }
}