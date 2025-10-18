package com.wang_lab.mkm_core.algebra.expr

import com.wang_lab.mkm_core.algebra.expr.ExprConst.Companion.eONE
import com.wang_lab.mkm_core.algebra.number_math.*

class ExprPow(val base: AlgebraExpr, val exponent: AlgebraExpr): AlgebraExpr() {

    override fun toString() = "${bracket(base, 1)} ^ ${bracket(exponent, 1)}"
    override fun derivative(varName: String, sub: Map<String, AlgebraExpr>) =
        if(exponent is ExprConst) base.derivative(varName, sub) * ExprConst(exponent.value) * base.pow(ExprConst(exponent.value - 1))
        else if(base is ExprConst) ExprConst(base.value.ln()) * this * exponent.derivative(varName, sub)
        else throw Exception("Expression on power must be constant when calculating derivative!")
    override fun simplify(): AlgebraExpr {
        if(simplified) return this
        val newBase = base.simplify()
        val newExp = exponent.simplify()
        if(newExp is ExprConst){
            if(newBase is ExprConst) return ExprConst(newBase.value.power(newExp.value))
            if(newBase is ExprVar) return ExprVar(newBase.name, newExp.value * newExp.value).simplify()
            if(newBase is ExprMultiply) return ExprMultiply(newBase.expr.map{ (e, exp) -> Pair(e, exp * newExp) }).simplify()
            if(newExp.value.isInteger()){
                if(newExp.value.equalTo(0)) return eONE
                if(newExp.value.equalTo(1)) return newBase
            }
        }
        return newBase.pow(newExp).apply { setSimplified() }
    }
    override fun arithmetic(
        values: Map<String, Number>,
        sub: Map<String, AlgebraExpr>?,
        subDict: MutableMap<AlgebraExpr, Number>?
    ): Number{
        subDict?.get(this)?.let { return it }
        return base.arithmetic(values, sub).power(exponent.arithmetic(values, sub)).also{ subDict?.set(this, it) }
    }

    override fun substitute(sub: Map<String, AlgebraExpr>) = ExprPow(base.substitute(sub), exponent.substitute(sub))
    override fun getOrder() = 1
    override fun getVariables(): Set<String>{
        val vars = mutableSetOf<String>()
        vars.addAll(base.getVariables())
        vars.addAll(exponent.getVariables())
        return vars
    }
    override fun equals(other: Any?): Boolean {
        if(other !is ExprPow) return false
        return base == other.base && exponent == other.exponent
    }

    override fun hashCode() = base.hashCode() * 31 + exponent.hashCode()
    override fun contains(b: AlgebraExpr) = this == b || b in base
    override fun simpleSolve(a: Number): Number {
        if(exponent.getVariables().isEmpty()) return base.simpleSolve(a.power(1 / exponent.arithmetic()))
        if(base.getVariables().isEmpty()) return exponent.simpleSolve(a.ln() / exponent.arithmetic().ln())
        throw Exception("Can not be simply solved!")
    }

    override fun expand(): AlgebraExpr {
        if(exponent !is ExprConst || !exponent.value.isInteger() || exponent.value == -1) return this
        if(exponent.value == 1) return base.expand()
        if(exponent.value > 1) return ExprMultiply(List(exponent.value.toInt()){ Pair(base, eONE) })
        return eONE / ExprMultiply(List(-exponent.value.toInt()){ Pair(base, eONE) })
    }
}