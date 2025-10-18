package com.wang_lab.mkm_core.algebra.expr

import com.wang_lab.mkm_core.algebra.number_math.simplify

class ExprConst(_value: Number): AlgebraExpr(true) {
    val value = _value.simplify()
    private var name: String? = null
    override fun toString() = name ?: value.toString()
    override fun simplify() = this
    override fun derivative(varName: String, sub: Map<String, AlgebraExpr>) = eZERO
    override fun arithmetic(
        values: Map<String, Number>,
        sub: Map<String, AlgebraExpr>?,
        subDict: MutableMap<AlgebraExpr, Number>?
    ): Number = value
    override fun substitute(sub: Map<String, AlgebraExpr>) = this
    override fun getVariables(): Set<String> = setOf()
    override fun equals(other: Any?): Boolean {
        if(other !is ExprConst) return false
        return value == other.value
    }
    override fun hashCode() = value.hashCode()
    override fun simpleSolve(a: Number): Number {
        throw Exception("No solution!")
    }
    companion object{
        val eZERO = ExprConst(0).apply { setSimplified() }
        val eONE = ExprConst(1).apply { setSimplified() }
        val eE = ExprConst(Math.E).apply { name = "e" }.apply { setSimplified() }
        val ePI = ExprConst(Math.PI).apply { name = "π" }.apply { setSimplified() }
        val eTEN = ExprConst(10).apply { setSimplified() }
    }
}