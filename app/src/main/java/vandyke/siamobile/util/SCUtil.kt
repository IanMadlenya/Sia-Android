package vandyke.siamobile.util

import vandyke.siamobile.prefs
import java.math.BigDecimal

val HASTINGS_PER_SC = "1000000000000000000000000"

fun BigDecimal.toSC(): BigDecimal = divide(BigDecimal(HASTINGS_PER_SC))

fun String.toSC(): BigDecimal = if (this.isBlank()) BigDecimal.ZERO else BigDecimal(this).divide(BigDecimal(HASTINGS_PER_SC))

fun BigDecimal.toHastings(): BigDecimal = multiply(BigDecimal(HASTINGS_PER_SC))

fun String.toHastings(): BigDecimal = if (this.isBlank()) BigDecimal.ZERO else BigDecimal(this).multiply(BigDecimal(HASTINGS_PER_SC))

fun BigDecimal.round(): BigDecimal = setScale(prefs.displayedDecimalPrecision, BigDecimal.ROUND_CEILING)

fun BigDecimal.toUsd(usdPrice: BigDecimal): BigDecimal = multiply(usdPrice)

object SCUtil {
    fun estimatedBlockHeightAt(time: Long): Long {
        val block100kTimestamp: Long = 1492126789 // Unix timestamp; seconds
        val blockTime = 9 // overestimate
        val diff = time - block100kTimestamp
        return (100000 + diff / 60 / blockTime.toLong())
    }

    fun estimatedTimeAtHeight(height: Long): Long {
        val block100kTimestamp: Long = 1492126789
        val blockTime = 10
        val diff = height - 100000
        val timeDiff = diff * blockTime
        return block100kTimestamp + timeDiff
    }
}