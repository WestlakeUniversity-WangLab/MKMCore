package com.wang_lab.mkm_core.algebra.number_math

import ch.obermuhlner.math.big.BigDecimalMath
import com.wang_lab.mkm_core.algebra.big_decimal_math.nToBigDecimal
import com.wang_lab.mkm_core.algebra.big_decimal_math.powerBD
import com.wang_lab.mkm_core.algebra.big_decimal_math.precision
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.pow
import kotlin.math.roundToInt

const val unsupported = "Only Int, Long, Double, Fraction, BigInteger and BigDecimal are supported!"

operator fun Number.unaryMinus(): Number{
    return when(this){
        is Int -> -this
        is Long -> -this
        is Double -> -this
        is Fraction -> -this
        is BigInteger -> this.negate()
        is BigDecimal -> this.negate()
        else -> throw Exception(unsupported)
    }
}
fun Long.bitLength(): Int{
    if(this == 0L) return 0
    if (this < 0) return (-this).bitLength()
    var i = this
    var n = 0
    var j = i shr 32
    if(j != 0L){i = j; n += 32}
    j = i shr 16
    if(j != 0L){i = j; n += 16}
    j = i shr 8
    if(j != 0L){i = j; n += 8}
    j = i shr 4
    if(j != 0L){i = j; n += 4}
    j = i shr 2
    if(j != 0L){i = j; n += 2}
    return n + i.toInt()
}
fun Int.bitLength(): Int{
    if(this == 0) return 0
    if (this < 0) return (-this).bitLength()
    var i = this
    var n = 0
    var j = i shr 16
    if(j != 0){i = j; n += 16}
    j = i shr 8
    if(j != 0){i = j; n += 8}
    j = i shr 4
    if(j != 0){i = j; n += 4}
    j = i shr 2
    if(j != 0){i = j; n += 2}
    return n + i
}
fun Int.bitOver(i: Int) = (this shr i) != 0
fun Long.bitOver(i: Int) = (this shr i) != 0L
fun Number.simplify() = when(this){
    is Int -> this
    is Long -> if(bitLength() <= 31) toInt() else this
    is Double -> {
        if(abs(this) < Int.MAX_VALUE){
            val j = roundToInt()
            if(this == j.toDouble()) j else this
        }else{
            this
        }
    }
    is Fraction -> if(b == 1) a else this
    is BigInteger -> if(abs() < Int.MAX_VALUE) toInt() else this
    is BigDecimal -> this
    else -> throw Exception(unsupported)
}
operator fun Number.plus(i: Number): Number{
    return when(this){
        is Int -> when(i){
            is Int -> {
                if(bitOver(30) || i.bitOver(30)) this.toLong() + i.toLong()
                else this + i
            }
            is Long -> {
                if(i.bitOver(62)) toBigInteger().add(i.toBigInteger())
                else this + i
            }
            is Double -> this + i
            is Fraction -> {
                if(i.a.bitOver(29) || bitLength() + i.b.bitLength() > 29) this + i.toDouble()
                else Fraction(this * i.b + i.a, i.b)
            }
            is BigInteger -> BigInteger.valueOf(this.toLong()).add(i)
            is BigDecimal -> this.toBigDecimal().add(i)
            else -> throw Exception(unsupported)
        }
        is Long -> when(i){
            is Int -> {
                if(bitOver(61)) BigInteger.valueOf(this).add(BigInteger.valueOf(i.toLong()))
                else this + i
            }
            is Long -> {
                if(bitOver(61) || i.bitOver(61)) BigInteger.valueOf(this).add(BigInteger.valueOf(i))
                else this + i
            }
            is Double -> this + i
            is Fraction -> this.toDouble() + i.toDouble()
            is BigInteger -> BigInteger.valueOf(this).add(i)
            is BigDecimal -> this.toBigDecimal().add(i)
            else -> throw Exception(unsupported)
        }
        is Double -> when(i){
            is Int -> this + i
            is Long -> this + i
            is Double -> this + i
            is Fraction -> this + i.toDouble()
            is BigInteger -> this.toBigDecimal().add(i.toBigDecimal())
            is BigDecimal -> this.toBigDecimal().add(i)
            else -> throw Exception(unsupported)
        }
        is Fraction -> when(i){
            is Int -> {
                if(a.bitOver(29) || i.bitLength() + b.bitLength() > 29) this + i.toDouble()
                else Fraction(i * b + a, b)
            }
            is Long -> i.toDouble() + this.toDouble()
            is Double -> i + this.toDouble()
            is Fraction -> {
                if(a.bitLength() + i.b.bitLength() > 29 || b.bitLength() + i.a.bitLength() > 29) toDouble() + i.toDouble()
                else this + i
            }
            is BigInteger -> this.toBigDecimal() + i.toBigDecimal()
            is BigDecimal -> this.toBigDecimal() + i
            else -> throw Exception(unsupported)
        }
        is BigInteger -> when(i){
            is Int -> this.add(BigInteger.valueOf(i.toLong()))
            is Long -> this.add(BigInteger.valueOf(i))
            is Double -> this.toBigDecimal().add(i.toBigDecimal())
            is Fraction -> this.toBigDecimal() + i.toBigDecimal()
            is BigInteger -> this.add(i)
            is BigDecimal -> this.toBigDecimal().add(i)
            else -> throw Exception(unsupported)
        }
        is BigDecimal -> when(i){
            is Int -> this.add(i.toBigDecimal())
            is Long -> this.add(i.toBigDecimal())
            is Double -> this.add(i.toBigDecimal())
            is Fraction -> this.add(i.toBigDecimal())
            is BigInteger -> this.add(i.toBigDecimal())
            is BigDecimal -> this.add(i)
            else -> throw Exception(unsupported)
        }
        else -> throw Exception(unsupported)
    }.simplify()
}

operator fun Number.minus(i: Number) = this + -i

operator fun Number.times(i: Number): Number{
    if(this == 0) return 0
    if(i == 0) return 0
    return when(this){
        is Int -> when(i){
            is Int -> {
                if(bitLength() + i.bitLength() > 29) this.toLong() * i
                else this * i
            }
            is Long -> {
                if(bitLength() + i.bitLength() > 61) BigInteger.valueOf(this.toLong()).multiply(BigInteger.valueOf(i))
                else this * i
            }
            is Double -> this * i
            is Fraction -> {
                if(bitLength() + i.a.bitLength() > 29) this.toDouble() * i.toDouble()
                else Fraction(this * i.a, i.b)
            }
            is BigInteger -> BigInteger.valueOf(this.toLong()).multiply(i)
            is BigDecimal -> this.toBigDecimal().multiply(i, precision)
            else -> throw Exception(unsupported)
        }
        is Long -> when(i){
            is Int -> {
                if(bitLength() + i.bitLength() > 61) BigInteger.valueOf(this).multiply(BigInteger.valueOf(i.toLong()))
                else this * i
            }
            is Long -> {
                if(bitLength() + i.bitLength() > 61) BigInteger.valueOf(this).multiply(BigInteger.valueOf(i))
                else this * i
            }
            is Double -> this * i
            is Fraction -> toDouble() * i.toDouble()
            is BigInteger -> BigInteger.valueOf(this).multiply(i)
            is BigDecimal -> this.toBigDecimal().multiply(i, precision)
            else -> throw Exception(unsupported)
        }
        is Double -> when(i){
            is Int -> this * i
            is Long -> this * i
            is Double -> this * i
            is Fraction -> toDouble() * i.toDouble()
            is BigInteger -> this.toBigDecimal().multiply(i.toBigDecimal(), precision)
            is BigDecimal -> this.toBigDecimal().multiply(i, precision)
            else -> throw Exception(unsupported)
        }
        is Fraction -> when(i){
            is Int -> {
                if(i.bitLength() + a.bitLength() > 29) this.toDouble() * i.toDouble()
                else Fraction(i * a, b)
            }
            is Long -> toDouble() * i.toDouble()
            is Double -> toDouble() * i.toDouble()
            is Fraction -> {
                if(a.bitLength() + i.a.bitLength() > 29 || b.bitLength() + i.b.bitLength() > 29) toDouble() * i.toDouble()
                else Fraction(a * i.a, b * i.b)
            }
            is BigInteger -> toBigDecimal().multiply(i.toBigDecimal(), precision)
            is BigDecimal -> toBigDecimal().multiply(i, precision)
            else -> throw Exception(unsupported)
        }
        is BigInteger -> when(i){
            is Int -> this.multiply(BigInteger.valueOf(i.toLong()))
            is Long -> this.multiply(BigInteger.valueOf(i))
            is Double -> this.toBigDecimal().multiply(i.toBigDecimal(), precision)
            is Fraction -> toBigDecimal().multiply(i.toBigDecimal(), precision)
            is BigInteger -> this.multiply(i)
            is BigDecimal -> this.toBigDecimal().multiply(i, precision)
            else -> throw Exception(unsupported)
        }
        is BigDecimal -> when(i){
            is Int -> this.multiply(i.toBigDecimal(), precision)
            is Long -> this.multiply(i.toBigDecimal(), precision)
            is Double -> this.multiply(i.toBigDecimal(), precision)
            is Fraction -> this.multiply(i.toBigDecimal(), precision)
            is BigInteger -> this.multiply(i.toBigDecimal(), precision)
            is BigDecimal -> this.multiply(i, precision)
            else -> throw Exception(unsupported)
        }
        else -> throw Exception(unsupported)
    }.simplify()
}

operator fun Number.div(i: Number): Number{
    if(this == 0) return 0
    return when(this){
        is Int -> when(i){
            is Int -> {
                if(this % i == 0) this / i
                else Fraction(this, i)
            }
            is Long -> {
                if(this % i == 0L) this / i
                else this.toDouble() / i
            }
            is Double -> this / i
            is Fraction -> {
                if(bitLength() + i.b.bitLength() > 29) this.toDouble() / i.toDouble()
                else Fraction(this * i.b, i.a)
            }
            is BigInteger -> this.toBigDecimal().divide(i.toBigDecimal(), precision)
            is BigDecimal -> this.toBigDecimal().divide(i, precision)
            else -> throw Exception(unsupported)
        }
        is Long -> when(i){
            is Int -> {
                if(this % i == 0L) this / i
                else this.toDouble() / i
            }
            is Long -> {
                if(this % i == 0L) this / i
                else this.toDouble() / i
            }
            is Double -> this / i
            is Fraction -> this.toDouble() / i.toDouble()
            is BigInteger -> this.toBigDecimal().divide(i.toBigDecimal(), precision)
            is BigDecimal -> this.toBigDecimal().divide(i, precision)
            else -> throw Exception(unsupported)
        }
        is Double -> when(i){
            is Int -> this / i
            is Long -> this / i
            is Double -> this / i
            is Fraction -> this / i.toDouble()
            is BigInteger -> this.toBigDecimal().divide(i.toBigDecimal(), precision)
            is BigDecimal -> this.toBigDecimal().divide(i, precision)
            else -> throw Exception(unsupported)
        }
        is Fraction -> when(i){
            is Int -> {
                if(b.bitLength() + i.bitLength() > 29) this.toDouble() / i.toDouble()
                else Fraction(a, b * i)
            }
            is Long -> this / i
            is Double -> this / i
            is Fraction -> {
                if(a.bitLength() + i.b.bitLength() > 29 || b.bitLength() + i.a.bitLength() > 29) toDouble() * i.toDouble()
                else Fraction(a * i.b, b * i.a)
            }
            is BigInteger -> this.toBigDecimal().divide(i.toBigDecimal(), precision)
            is BigDecimal -> this.toBigDecimal().divide(i, precision)
            else -> throw Exception(unsupported)
        }
        is BigInteger -> when(i){
            is Int -> this.toBigDecimal().divide(i.toBigDecimal(), precision)
            is Long -> this.toBigDecimal().divide(i.toBigDecimal(), precision)
            is Double -> this.toBigDecimal().divide(i.toBigDecimal(), precision)
            is Fraction -> this.toBigDecimal() / i.toBigDecimal()
            is BigInteger -> this.toBigDecimal().divide(i.toBigDecimal(), precision)
            is BigDecimal -> this.toBigDecimal().divide(i, precision)
            else -> throw Exception(unsupported)
        }
        is BigDecimal -> when(i){
            is Int -> this.divide(i.toBigDecimal(), precision)
            is Long -> this.divide(i.toBigDecimal(), precision)
            is Double -> this.divide(i.toBigDecimal(), precision)
            is Fraction -> this.divide(i.toBigDecimal(), precision)
            is BigInteger -> this.divide(i.toBigDecimal(), precision)
            is BigDecimal -> this.divide(i, precision)
            else -> throw Exception(unsupported)
        }
        else -> throw Exception(unsupported)
    }.simplify()
}

fun Number.power(exponent: Number): Number{
    if(exponent == 0) return 1
    if(exponent == 1) return this
    if(this == 0) return 0
    if(exponent is Int){
        when(this){
            is Int -> {
                if(abs(bitLength() * exponent) < 32){
                    return if(exponent >= 0) pow(exponent) else Fraction(1, pow(-exponent))
                }else if(exponent > 0 && abs(bitLength() * exponent) < 64){
                    return toLong().pow(exponent)
                }else if(exponent > 0){
                    return this.toBigInteger().pow(exponent).simplify()
                }
            }
            is Long -> {
                if(exponent > 0){
                    return if(abs(bitLength() * exponent) < 64) pow(exponent) else this.toBigInteger().pow(exponent).simplify()
                }
            }
            is Fraction -> {
                if(abs(a.bitLength() * exponent) < 32 && abs(b.bitLength() * exponent) < 32){
                    return if(exponent > 0) Fraction(a.pow(exponent), b.pow(exponent)) else Fraction(b.pow(-exponent), a.pow(-exponent))
                }
            }
            is BigInteger -> {
                if(exponent > 0) return this.pow(exponent).simplify()
            }
        }
    }
    if(this is BigInteger) return toBigDecimal().powerBD(exponent.nToBigDecimal()).simplify()
    if(this is BigDecimal && exponent is Int) return this.pow(exponent, precision).simplify()
    if(this is BigDecimal) return powerBD(exponent.nToBigDecimal()).simplify()
    if(exponent is BigDecimal) return nToBigDecimal().powerBD(exponent).simplify()
    return this.toDouble().pow(exponent.toDouble()).simplify()
}
fun Number.equalTo(i: Number) = when(this){
    is Int -> when(i){
        is Int -> this == i
        is Long -> this.toLong() == i
        is Double -> this.toDouble() == i
        is Fraction -> i.b == 1 && this == i.a
        is BigInteger -> BigInteger.valueOf(this.toLong()) == i
        is BigDecimal -> BigDecimal(this) == i
        else -> throw Exception(unsupported)
    }
    is Long -> when(i){
        is Int -> this == i.toLong()
        is Long -> this == i
        is Double -> this.toDouble() == i
        is Fraction -> i.b == 1 && this == i.a.toLong()
        is BigInteger -> BigInteger.valueOf(this) == i
        is BigDecimal -> this.toBigDecimal() == i
        else -> throw Exception(unsupported)
    }
    is Double -> when(i){
        is Int -> this == i.toDouble()
        is Long -> this == i.toDouble()
        is Double -> this == i
        is Fraction -> this == i.toDouble()
        is BigInteger -> this.toBigDecimal() == i.toBigDecimal()
        is BigDecimal -> this.toBigDecimal() == i
        else -> throw Exception(unsupported)
    }
    is Fraction -> when(i){
        is Int -> b == 1 && a == i
        is Long -> b == 1 && a.toLong() == i
        is Double -> toDouble() == i
        is Fraction -> a == i.a && b == i.b
        is BigInteger -> b == 1 && a.toBigInteger() == i
        is BigDecimal -> toBigDecimal() == i
        else -> throw Exception(unsupported)
    }
    is BigInteger -> when(i){
        is Int -> this == BigInteger.valueOf(i.toLong())
        is Long -> this == BigInteger.valueOf(i)
        is Double -> this.toDouble() == i
        is Fraction -> i.b == 1 && i.a.toBigInteger() == this
        is BigInteger -> this == i
        is BigDecimal -> this.toBigDecimal() == i
        else -> throw Exception(unsupported)
    }
    is BigDecimal -> when(i){
        is Int -> this == BigDecimal(i)
        is Long -> this == i.toBigDecimal()
        is Double -> this == i.toBigDecimal()
        is Fraction -> this == i.toBigDecimal()
        is BigInteger -> this == i.toBigDecimal()
        is BigDecimal -> this == i
        else -> throw Exception(unsupported)
    }
    else -> throw Exception(unsupported)
}

inline fun <T> Collection<T>.nSumOf(selector: (T) -> Number): Number {
    var sum: Number = 0
    for (element in this) sum += selector(element)
    return sum
}
inline fun <T> Collection<T>.dSumOf(selector: (T) -> Double): Double {
    var sum = 0.0
    for (element in this) sum += selector(element)
    return sum
}
inline fun <T> Collection<T>.iSumOf(selector: (T) -> Int): Int {
    var sum = 0
    for (element in this) sum += selector(element)
    return sum
}
inline fun <T> Iterable<T>.nProductOf(selector: (T) -> Number): Number {
    var sum: Number = 1
    for (element in this) sum *= selector(element)
    return sum
}
inline fun <T> Iterable<T>.iProductOf(selector: (T) -> Int): Int {
    var sum = 1
    for (element in this) sum *= selector(element)
    return sum
}
fun <T: Number> abs(n: T): T =
    when(n){
        is Int -> kotlin.math.abs(n)
        is Long -> kotlin.math.abs(n)
        is Double -> kotlin.math.abs(n)
        is Fraction -> n.abs()
        is BigInteger -> n.abs()
        is BigDecimal -> n.abs()
        else -> throw Exception(unsupported)
    } as T

fun Int.setBit(bit: Int, value: Boolean): Int{
    return if(value){
        this or (1 shl bit)
    }else{
        this and (1 shl bit).inv()
    }
}
fun Int.getBit(bit: Int): Boolean = (this and (1 shl bit)) != 0

operator fun Number.compareTo(b: Number) = this.nToBigDecimal().compareTo(b.nToBigDecimal())
fun Number.ln(): Number =
    when(this){
        is Int -> kotlin.math.ln(this.toDouble())
        is Long -> kotlin.math.ln(this.toDouble())
        is Double -> kotlin.math.ln(this)
        is Fraction ->  kotlin.math.ln(toDouble())
        is BigInteger -> BigDecimalMath.log(this.toBigDecimal(), precision)
        is BigDecimal -> BigDecimalMath.log(this, precision)
        else -> throw Exception(unsupported)
    }
fun Int.pow(exponent: Int): Int {
    if (exponent < 0) throw IllegalArgumentException("Exponent must be non-negative")
    var result = 1
    var base = this
    var exp = exponent
    while (exp > 0) {
        if (exp % 2 == 1) result *= base
        base *= base
        exp /= 2
    }
    return result
}

fun Long.pow(exponent: Int): Long {
    if (exponent < 0) throw IllegalArgumentException("Exponent must be non-negative")
    var result: Long = 1
    var base = this
    var exp = exponent
    while (exp > 0) {
        if (exp % 2 == 1) result *= base
        base *= base
        exp /= 2
    }
    return result
}
fun Number.isInteger() = this is Int || this is Long || this is BigInteger || (this is Fraction && this.b == 1)
fun maxNum(a: Number, b: Number) = if(a >= b) a else b
fun minNum(a: Number, b: Number) = if(a <= b) a else b