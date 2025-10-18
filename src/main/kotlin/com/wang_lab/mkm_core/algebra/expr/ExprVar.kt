package com.wang_lab.mkm_core.algebra.expr

import com.wang_lab.mkm_core.algebra.expr.ExprConst.Companion.eONE
import com.wang_lab.mkm_core.algebra.expr.ExprConst.Companion.eZERO
import com.wang_lab.mkm_core.algebra.number_math.*


class ExprVar(val name: String, _power: Number): AlgebraExpr() {
    val power = _power.simplify()
    init{
        for(c in pattern_prohibited) if(c in name) throw Exception("Invalid character $c in variable name!")
    }
    constructor(name: String): this(name, 1)

    override fun toString() = if(power.equalTo(1)) name else "$name^$power"
    override fun simplify() =
        if(simplified) this
        else if(power.equalTo(0)) eONE
        else this.apply { setSimplified() }
    override fun derivative(varName: String, sub: Map<String, AlgebraExpr>) =
        if(varName == name)
            if(power.equalTo(1)) eONE
            else ExprConst(power) * ExprVar(name, power - 1)
        else if(name in sub.keys)
            if(power.equalTo(1)) sub[name]!!.derivative(varName, sub)
            else sub[name]!!.derivative(varName, sub) * ExprConst(power) * ExprVar(name, power - 1)
        else eZERO
    override fun arithmetic(
        values: Map<String, Number>,
        sub: Map<String, AlgebraExpr>?,
        subDict: MutableMap<AlgebraExpr, Number>?
    ): Number {
        subDict?.get(this)?.let { return it }
        if(name in values){
            return if(power.equalTo(1)) values[name]!!
            else values[name]!!.power(power)
        }
        if(sub != null && name in sub){
            val v = sub[name]!!.arithmetic(values, sub)
            return (if(power.equalTo(1)) v
            else v.power(power)).also{ subDict?.set(this, it) }
        }
        throw NullPointerException("$name not found in $values and $sub.")
    }
    override fun substitute(sub: Map<String, AlgebraExpr>): AlgebraExpr{
        val e = sub[name] ?: return this
        if(power.equalTo(1)) return e.substitute(sub)
        return ExprPow(e.substitute(sub), ExprConst(power))
    }
    override fun getOrder() = if(power.equalTo(1)) 0 else 1
    override fun getVariables(): Set<String> = setOf(name)
    override fun equals(other: Any?): Boolean {
        if(other !is ExprVar) return false
        return name == other.name && power == other.power
    }
    override fun hashCode() = 31 * name.hashCode() + power.hashCode()
    override fun simpleSolve(a: Number) = a.power(1 / power)
}