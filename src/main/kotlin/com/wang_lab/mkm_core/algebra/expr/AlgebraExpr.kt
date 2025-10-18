package com.wang_lab.mkm_core.algebra.expr

import com.wang_lab.mkm_core.algebra.expr.ExprConst.Companion.eONE
import com.wang_lab.mkm_core.algebra.number_math.times
import com.wang_lab.mkm_core.algebra.number_math.unaryMinus


abstract class AlgebraExpr(var simplified: Boolean = false) {
    operator fun unaryMinus(): AlgebraExpr =
        when (this) {
            is ExprConst -> ExprConst(-this.value)
            is ExprMultiply -> {
                val l = expr.toMutableList()
                l.add(0, Pair(ExprConst(-1), eONE))
                ExprMultiply(l)
            }
            else -> ExprConst(-1) * this
        }
    operator fun unaryPlus() = this
    operator fun plus(b: AlgebraExpr) =
        if(this is ExprAdd){
            if(b is ExprAdd){
                val l = ArrayList<Pair<AlgebraExpr, Boolean>>(this.expr.size + b.expr.size)
                l.addAll(this.expr)
                l.addAll(b.expr)
                ExprAdd(l)
            }else{
                val l = ArrayList<Pair<AlgebraExpr, Boolean>>(this.expr.size + 1)
                l.addAll(this.expr)
                l.add(Pair(b, true))
                ExprAdd(l)
            }
        }else{
            if(b is ExprAdd){
                val l = ArrayList<Pair<AlgebraExpr, Boolean>>(b.expr.size + 1)
                l.add(Pair(this, true))
                l.addAll(b.expr)
                ExprAdd(l)
            }else{
                ExprAdd(this, b)
            }
        }
    operator fun minus(b: AlgebraExpr) = this + -b
    operator fun times(b: AlgebraExpr) =
        if(this is ExprMultiply){
            if(b is ExprMultiply){
                val l = ArrayList<Pair<AlgebraExpr, AlgebraExpr>>(this.expr.size + b.expr.size)
                l.addAll(this.expr)
                l.addAll(b.expr)
                ExprMultiply(l)
            }else{
                val l = ArrayList<Pair<AlgebraExpr, AlgebraExpr>>(this.expr.size + 1)
                l.addAll(this.expr)
                l.add(Pair(b, eONE))
                ExprMultiply(l)
            }
        }else{
            if(b is ExprMultiply){
                val l = ArrayList<Pair<AlgebraExpr, AlgebraExpr>>(b.expr.size + 1)
                l.add(Pair(this, eONE))
                l.addAll(b.expr)
                ExprMultiply(l)
            }else{
                ExprMultiply(this, b)
            }
        }
    operator fun div(b: AlgebraExpr) = this * b.pow(ExprConst(-1))
    fun pow(exponent: AlgebraExpr) = ExprPow(this, exponent)

    open fun getOrder() = 0
    abstract fun derivative(varName: String, sub: Map<String, AlgebraExpr> = mapOf()): AlgebraExpr
    abstract fun simplify(): AlgebraExpr
    open fun expand(): AlgebraExpr = this
    abstract fun arithmetic(values: Map<String, Number> = mapOf(), sub: Map<String, AlgebraExpr>? = null, subDict: MutableMap<AlgebraExpr, Number>? = null): Number
    abstract fun substitute(sub: Map<String, AlgebraExpr>): AlgebraExpr
    abstract fun getVariables(): Set<String>
    abstract fun simpleSolve(a: Number): Number
    abstract override operator fun equals(other: Any?): Boolean
    operator fun times(b: Number): AlgebraExpr {
        if(this is ExprConst) return ExprConst(this.value * b)
        return ExprMultiply(ExprConst(b), this)
    }
    fun isZero(): Boolean{
        val s = this.simplify()
        return s is ExprConst && s.value == 0
    }
    fun abs(): Number{
        return arithmetic(ZeroMap)
    }

    abstract override fun hashCode(): Int

    object ZeroMap: Map<String, Int>{
        override val entries: Set<Map.Entry<String, Int>> = setOf()
        override val keys: Set<String> = setOf()
        override val size: Int = -1
        override val values: Collection<Int> = listOf(0)
        override fun isEmpty() = false

        override fun get(key: String): Int = 0
        override fun containsValue(value: Int) = value == 0
        override fun containsKey(key: String) = true

    }
    open operator fun contains(b: AlgebraExpr): Boolean = this == b
    operator fun plus(i: Number) = this + ExprConst(i)
    operator fun minus(i: Number) = this - ExprConst(i)
    protected fun setSimplified(){
        this.simplified = true
    }
    companion object{
        fun bracket(exp: AlgebraExpr, order: Int) =
            if(exp.getOrder() >= order) "($exp)"
            else exp.toString()
    }
}