package com.wang_lab.mkm_core.algebra.big_decimal_math

import ch.obermuhlner.math.big.BigDecimalMath
import com.wang_lab.mkm_core.algebra.number_math.Fraction
import com.wang_lab.mkm_core.algebra.number_math.unsupported
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext


private val doublePrecision = MathContext(16)
var decimalPrecision = 75
var precision = MathContext(77)

fun Iterable<BigDecimal>.product(): BigDecimal {
    var sum: BigDecimal = BigDecimal.ONE
    for (element in this) sum *= element
    return sum
}
fun BigDecimal.clamp(min: BigDecimal?, max: BigDecimal?) =
    if(min != null && this < min) min else if(max != null && this > max) max else this

fun BigDecimal.order() = precision() - scale()
operator fun BigDecimal.plus(b: BigDecimal): BigDecimal = this.add(b)
operator fun BigDecimal.minus(b: BigDecimal): BigDecimal = this.subtract(b)
operator fun BigDecimal.times(b: BigDecimal): BigDecimal = this.multiply(b, precision)
operator fun BigDecimal.times(b: Int): BigDecimal = this.multiply(BigDecimal(b), precision)
operator fun BigDecimal.div(b: BigDecimal): BigDecimal = this.divide(b, precision)
operator fun BigDecimal.div(b: Double): BigDecimal = this.divide(BigDecimal(b, precision), precision)
fun BigDecimal.exp(): BigDecimal
        = BigDecimalMath.exp(this, precision)
fun BigDecimal.ln(): BigDecimal
        = BigDecimalMath.log(this, precision)
fun BigDecimal.powerBD(e: BigDecimal): BigDecimal
        = BigDecimalMath.pow(this, e, precision)

fun BigDecimal.isZero(): Boolean = abs() < BigDecimal(BigInteger.ONE, decimalPrecision - 4)
fun bdExp(a: BigDecimal) = a.exp()

fun Number.nToBigDecimal(): BigDecimal = when(this){
    is Int -> BigDecimal(this)
    is Long -> BigDecimal(this)
    is Double -> BigDecimal(this, doublePrecision)
    is Fraction -> toBigDecimal()
    is BigInteger -> BigDecimal(this)
    is BigDecimal -> this
    else -> throw Exception(unsupported)
}
inline fun <T> Array<T>.indexedSumOf(selector: (Int, T) -> BigDecimal): BigDecimal {
    var sum = BigDecimal.ZERO
    this.forEachIndexed{ i, element ->
        sum = sum.plus(selector(i, element))
    }
    return sum
}
