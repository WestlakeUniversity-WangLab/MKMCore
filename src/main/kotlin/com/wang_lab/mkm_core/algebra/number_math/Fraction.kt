package com.wang_lab.mkm_core.algebra.number_math

import com.wang_lab.mkm_core.algebra.big_decimal_math.precision
import java.math.BigDecimal

class Fraction(_a: Int, _b: Int): Number() {
    val a: Int
    val b: Int
    init{
        if(_b == 0) throw ArithmeticException("Zero on denominator!")
        if(_a == 0){
            a = 0
            b = 1
        }else{
            val gcd = gcd(abs(_a), abs(_b))
            b = abs(_b) / gcd
            a = if((_a < 0 && _b > 0) || (_a > 0 && _b < 0)) -abs(_a) / gcd else abs(_a) / gcd
        }
    }
    fun abs() = if(a >= 0) this else Fraction(-a, b)
    override fun toByte() = toInt().toByte()
    override fun toChar() = toInt().toChar()
    override fun toDouble() = a.toDouble() / b.toDouble()
    fun toBigDecimal(): BigDecimal = BigDecimal(a).divide(BigDecimal(b), precision)
    override fun toFloat() = a.toFloat() / b.toFloat()
    override fun toInt() = toDouble().toInt()
    override fun toLong() = toDouble().toLong()
    override fun toShort() = toInt().toShort()
    override fun toString() = "($a / $b)"
    operator fun unaryMinus() = Fraction(-a, b)
    operator fun plus(other: Fraction): Fraction{
        val gcd = gcd(b, other.b)
        return Fraction(a * (other.b / gcd ) + other.a * (b / gcd), b / gcd * other.b)
    }
    operator fun minus(other: Fraction): Fraction{
        val gcd = gcd(b, other.b)
        return Fraction(a * (other.b / gcd ) - other.a * (b / gcd), b / gcd * other.b)
    }
    operator fun times(other: Fraction) = Fraction(a * other.a, b * other.b)
    operator fun div(other: Fraction) = Fraction(a * other.b, b * other.a)
    companion object{
        fun gcd(a: Int, b: Int): Int {
            var x = a
            var y = b
            while (y != 0) {
                val temp = y
                y = x % y
                x = temp
            }
            return x
        }
    }
}