package com.wang_lab.mkm_core.algebra.expr

import com.wang_lab.mkm_core.algebra.number_math.ln
import com.wang_lab.mkm_core.algebra.number_math.power

class ExprLn(val value: AlgebraExpr): AlgebraExpr() {
    override fun derivative(varName: String, sub: Map<String, AlgebraExpr>): AlgebraExpr {
        val vs = value.simplify()
        return (vs.derivative(varName, sub) / vs).simplify()
    }
    override fun simplify(): AlgebraExpr{
        if(simplified) return this
        return when(val sv = value.simplify()){
            is ExprConst -> ExprConst(sv.value.ln())
            is ExprVar -> {
                if(sv.power == 1){
                    ExprLn(sv).apply { setSimplified() }
                }else{
                    (ExprConst(sv.power) * ExprLn(ExprVar(sv.name))).simplify()
                }
            }
            is ExprMultiply -> {
                ExprAdd(sv.expr.map{ Pair(it.second * ExprLn(it.first), true) }).simplify()
            }
            else -> ExprLn(sv).apply { setSimplified() }
        }
    }
    override fun arithmetic(
        values: Map<String, Number>,
        sub: Map<String, AlgebraExpr>?,
        subDict: MutableMap<AlgebraExpr, Number>?
    ): Number{
        subDict?.get(this)?.let { return it }
        return value.arithmetic(values, sub).ln().also{ subDict?.set(this, it) }
    }
    override fun substitute(sub: Map<String, AlgebraExpr>) = ExprLn(value.substitute(sub))
    override fun getVariables(): Set<String> = value.getVariables()

    override fun equals(other: Any?): Boolean {
        if(other !is ExprLn) return false
        return value == other.value
    }
    override fun hashCode() = value.hashCode()

    override fun toString(): String = "ln($value)"
    override fun simpleSolve(a: Number) = value.simpleSolve(Math.E.power(a))
}