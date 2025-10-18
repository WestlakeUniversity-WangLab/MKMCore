package com.wang_lab.mkm_core.algebra.expr

import com.wang_lab.mkm_core.algebra.expr.ExprAdd.Companion.exprAdd
import com.wang_lab.mkm_core.algebra.expr.ExprConst.Companion.eONE
import com.wang_lab.mkm_core.algebra.expr.ExprConst.Companion.eZERO
import com.wang_lab.mkm_core.algebra.number_math.*


class ExprMultiply(_expr: Iterable<Pair<AlgebraExpr, AlgebraExpr>>): AlgebraExpr() {
    val expr = _expr.toList()
    constructor(vararg _expr: AlgebraExpr): this(_expr.map{
        when (it) {
            is ExprPow -> Pair(it.base, it.exponent)
            is ExprVar -> Pair(ExprVar(it.name), ExprConst(it.power))
            else -> Pair(it, eONE)
        }
    })
    constructor(_expr: List<AlgebraExpr>): this(_expr.map{
        when (it) {
            is ExprPow -> Pair(it.base, it.exponent)
            is ExprVar -> Pair(ExprVar(it.name), ExprConst(it.power))
            else -> Pair(it, eONE)
        }
    })

    override fun toString(): String{
        val numerator = mutableListOf<Pair<AlgebraExpr, AlgebraExpr>>()
        val denominator = mutableListOf<Pair<AlgebraExpr, Number>>()
        expr.forEach { (e, exp) ->
            if(exp is ExprConst && exp.value < 0) denominator.add(Pair(e, -exp.value)) else numerator.add(Pair(e, exp))
        }
        val n = if(numerator.isEmpty()) "1"
        else numerator.joinToString(" * ") { (e, exp) ->
            if(exp is ExprConst && exp.value.equalTo(1)) bracket(e, 2) else "${bracket(e, 2)} ^ ${bracket(exp, 2)}"
        }
        val d = denominator.joinToString("") { (e, exp) ->
            " / " + if(exp.equalTo(1)) bracket(e, 2) else "${bracket(e, 2)} ^ $exp"
        }
        return n + d
    }
    override fun simplify(): AlgebraExpr {
        if(simplified) return this
        val se = expr.map{ (e, exp) -> Pair(e.simplify(), exp.simplify()) }
        var cst: Number = 1
        val vars = mutableMapOf<String, AlgebraExpr>()
        val other = mutableMapOf<AlgebraExpr, AlgebraExpr>()
        fun addItem(e: AlgebraExpr, exp: AlgebraExpr){
            when(e){
                is ExprConst -> {
                    if(exp is ExprConst) cst *= e.value.power(exp.value)
                    else other[e] = other[e]?.plus(exp) ?: exp
                }
                is ExprVar -> {
                    val pow = ExprConst(e.power) * exp
                    vars[e.name] = vars[e.name]?.plus(pow) ?: pow
                }
                is ExprMultiply -> {
                    e.expr.forEach { (e1, exp1) -> addItem(e1, exp * exp1) }
                }
                is ExprPow -> {
                    val pow = e.exponent * exp
                    other[e.base] = other[e.base]?.plus(pow) ?: pow
                }
                else -> other[e] = other[e]?.plus(exp) ?: exp
            }
        }
        se.forEach { (e, exp) -> addItem(e, exp) }
        if(cst == 0) return eZERO
        if(vars.isEmpty() && other.isEmpty()) return ExprConst(cst)
        val newExpr = mutableListOf<Pair<AlgebraExpr, AlgebraExpr>>()
        if(cst != 1) newExpr.add(Pair(ExprConst(cst), eONE))
        vars.forEach{ (name, e) ->
            val exp = e.simplify()
            if(!exp.isZero())
                if(exp is ExprConst) newExpr.add(Pair(ExprVar(name, exp.value), eONE))
                else newExpr.add(Pair(ExprVar(name), exp))
        }
        other.forEach{ (base, e) ->
            val exp = e.simplify()
            if(!exp.isZero()) newExpr.add(Pair(base.simplify(), exp))
        }
        if(newExpr.size == 1){
            val (e, exp) = newExpr[0]
            if(e is ExprVar && exp is ExprConst) return ExprVar(e.name, (e.power * exp.value).simplify()).simplify()
            return ExprPow(e, exp).simplify()
        }
        var flag = false
        newExpr.indices.forEach { i ->
            val (e, exp) = newExpr[i]
            if(exp !is ExprConst) return@forEach
            if(exp.value >= 0) return@forEach
            if(e !is ExprAdd) return@forEach
            val map = mutableMapOf<AlgebraExpr, Number>()
            e.expr.forEach { (add, _) ->
                if(add is ExprMultiply)
                    add.expr.forEach { (e2, ex2) ->
                        if(ex2 is ExprConst && ex2.value < 0){
                            if(e2 !in map) map[e2] = -ex2.value
                            else map[e2] = maxNum(map[e2]!!, -ex2.value)
                        }
                    }
            }
            if(map.isEmpty()) return@forEach
            flag = true
            val d = ExprMultiply(map.map{ (e3, ex3) -> Pair(e3, ExprConst(ex3)) })
            newExpr[i] = Pair(d / ExprAdd(e.expr.map{ (e4, sign) -> Pair((e4 * d).simplify(), sign) }), -exp)
        }
        if(flag) return ExprMultiply(newExpr).simplify()
        if(newExpr.size == 2){
            val (e01, ex01) = newExpr[0]
            val (e02, ex02) = newExpr[1]
            if(e01 is ExprConst && ex01 == eONE && e02 is ExprAdd && ex02 == eONE) return ExprAdd(e02.expr.map{ Pair(e01 * it.first, it.second) })
        }
        return ExprMultiply(newExpr).apply { setSimplified() }
    }
    override fun derivative(varName: String, sub: Map<String, AlgebraExpr>): AlgebraExpr {
        if(expr.isEmpty()) return eZERO
        if(expr.size == 1) return ExprPow(expr[0].first, expr[0].second).derivative(varName, sub)
        return exprAdd(
            expr.map{ e ->
                ExprMultiply(
                    expr.map{ ee ->
                        if(e === ee) Pair(ExprPow(ee.first, ee.second).derivative(varName, sub), eONE)
                        else ee
                    }
                )
            }
        )
    }
    override fun arithmetic(
        values: Map<String, Number>,
        sub: Map<String, AlgebraExpr>?,
        subDict: MutableMap<AlgebraExpr, Number>?
    ): Number{
        subDict?.get(this)?.let { return it }
        return expr.nProductOf { (e, exp) -> if(exp == eONE) e.arithmetic(values, sub) else e.arithmetic(values, sub).power(exp.arithmetic(values, sub)) }
            .also{ subDict?.set(this, it) }
    }
    override fun substitute(sub: Map<String, AlgebraExpr>) = ExprMultiply(expr.map{ Pair(it.first.substitute(sub), it.second.substitute(sub)) })
    override fun getOrder() = 2
    override fun getVariables(): Set<String>{
        val vars = mutableSetOf<String>()
        expr.forEach { (e, exp) ->
            vars.addAll(e.getVariables())
            vars.addAll(exp.getVariables())
        }
        return vars
    }
    override fun equals(other: Any?): Boolean {
        if(other !is ExprMultiply) return false
        if(expr.size != other.expr.size) return false
        val target = other.expr.toMutableList()
        expr.forEach { if(!target.remove(it)) return false }
        return target.isEmpty()
    }

    override fun hashCode(): Int = expr.sumOf{ it.hashCode() }
    override fun contains(b: AlgebraExpr) = this == b || expr.any{ b in it.first }
    override fun simpleSolve(a: Number): Number {
        if(simplified){
            var b = a
            if(expr.count{ it.first.getVariables().isNotEmpty() } != 1) throw Exception("No solution!")
            var e: AlgebraExpr? = null
            expr.forEach { (ba, ex) ->
                if(ba is ExprConst && ex is ExprConst){
                    b /= ba.value.power(ex.value)
                }else if(ba.getVariables().isEmpty()){
                    b /= ba.arithmetic().power(ex.arithmetic())
                }else{
                    if(e != null) throw Exception("Can not be simply solved!")
                    e = ExprPow(ba, ex).simplify()
                }
            }
            return e?.simpleSolve(b) ?: throw Exception("No solution!")
        }else{
            return simplify().simpleSolve(a)
        }
    }

    override fun expand(): AlgebraExpr {
        val i = expr.indexOfFirst { it.first is ExprAdd && it.second is ExprConst && (it.second as ExprConst).value.isInteger() && (it.second as ExprConst).value != -1 }
        if(i == -1) return this
        val m = ExprPow(expr[i].first, expr[i].second).expand()
        val rest = expr.filterIndexed { ii, _ -> i != ii }
        if(m is ExprAdd) return ExprAdd(m.expr.map { (e, sign) -> Pair((e * ExprMultiply(rest)).expand(), sign) })
        return m * ExprMultiply(rest)
    }
}