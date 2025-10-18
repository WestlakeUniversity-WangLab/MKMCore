package com.wang_lab.mkm_core.algebra.big_algebra

import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.algebra.big_decimal_math.*
import com.wang_lab.mkm_core.forEachZipped
import com.wang_lab.mkm_core.sum
import java.math.BigDecimal
import java.util.regex.Pattern

private val debugPattern = Pattern.compile("(?<index>\\d+) = \\{BigDecimal@\\d+} \"(?<value>.+)\"")
class BDVector(length: Int, c: (Int) -> BigDecimal): ArrayList<BigDecimal>(length), Cloneable {
    init{
        repeat(length){ add(c(it)) }
    }
    constructor(length: Int): this(length, { BigDecimal.ZERO })
    constructor(array: Array<BigDecimal>): this(array.size, { array[it] })
    operator fun unaryMinus() = transform { -it }
    operator fun times(b: BigDecimal) = transform { it * b }
    override fun equals(other: Any?): Boolean {
        if(other == null) return false
        if(other is BDVector){
            if(size != other.size) return false
            return this.indices.all{ this[it] == other[it] }
        }
        if(other is BDMatrix){
            if(other.rows == 1 && other.columns == size)
                return this.indices.all{ this[it] == other[0, it] }
            if(other.rows == size && other.columns == 1)
                return this.indices.all{ this[it] == other[it, 0] }
        }
        return false
    }
    operator fun times(b: BDVector): BigDecimal{
        if(size != b.size) throw Exception()
        return indices.sumOf { this[it] * b[it] }
    }
    operator fun plus(b: BDVector) = transformIndexed{ i, bd -> bd + b[i] }
    operator fun minus(b: BDVector) = transformIndexed{ i, bd -> bd - b[i] }
    fun addToVars(vars: MutableMap<String, BigDecimal>, name: (Int) -> String){
        this.forEachIndexed{ i, v ->
            vars[name(i)] = v
        }
    }
    fun transform(t: (BigDecimal) -> BigDecimal) = BDVector(size){ t(get(it)) }
    fun transformIndexed(t: (Int, BigDecimal) -> BigDecimal) = BDVector(size){ t(it, get(it)) }
    fun getVector(r: IntArray): BDVector = BDVector(r.size){ get(r[it]) }
    fun subVector(from: Int, to: Int): BDVector = BDVector(to - from){ get(from + it) }
    override fun clone() = BDVector(size){ get(it) }
    fun norm(): BigDecimal = maxOf{ it.abs() }
    override fun toString() = joinToString("\n")
    fun toStringFormatted(format: String) = joinToString("\t", "[", "]")
    fun asCoverage(rm: ReactionModel): String{
        val sb = StringBuilder()
        forEachZipped(rm.adsorbates, this){ t, u ->
            sb.append("$t: ${"%.6e".format(u)}\n")
        }
        return sb.toString()
    }
    fun ln(): BDVector = transform{ it.ln() }
    fun exp(): BDVector = transform{ it.exp() }
    fun toDoubleArray(): DoubleArray = DoubleArray(size){ this[it].toDouble() }
    fun isZero() = all{ it.isZero() }
    fun join(b: BDVector): BDVector{
        val r = BDVector(size + b.size)
        var i = 0
        forEach{
            r[i] = it
            i ++
        }
        b.forEach{
            r[i] = it
            i ++
        }
        return r
    }
    fun average(): BigDecimal {
        return this.sum() / this.size.nToBigDecimal()
    }
    override fun hashCode() = super.hashCode()
    fun read(s: String){
        s.split('\n').forEach{
            val matcher = debugPattern.matcher(it)
            if(!matcher.matches()) return@forEach
            try{
                this[matcher.group("index").toInt()] = BigDecimal(matcher.group("value"))
            }catch (_: Exception){}
        }
    }
}