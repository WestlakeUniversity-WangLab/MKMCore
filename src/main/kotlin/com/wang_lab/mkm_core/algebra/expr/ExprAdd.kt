package com.wang_lab.mkm_core.algebra.expr

import com.wang_lab.mkm_core.algebra.expr.ExprConst.Companion.eONE
import com.wang_lab.mkm_core.algebra.expr.ExprConst.Companion.eZERO
import com.wang_lab.mkm_core.algebra.number_math.*

class ExprAdd(_expr: Iterable<Pair<AlgebraExpr, Boolean>>): AlgebraExpr() {
    val expr = _expr.toList()
    constructor(vararg _expr: AlgebraExpr): this(_expr.map{ Pair(it, true) })

    override fun toString(): String{
        val sb = StringBuilder()
        expr.forEachIndexed { i, (ex, sign) ->
            if(sign && i > 0) sb.append(" + ") else if(!sign && i > 0) sb.append(" - ") else if(!sign) sb.append("- ")
            sb.append(bracket(ex,3))
        }
        return sb.toString()
    }
    override fun simplify(): AlgebraExpr {
        if(simplified) return this
        val map = mutableMapOf<AlgebraExpr, Number>()
        fun addItems(e: AlgebraExpr, sign: Boolean){
            when(e){
                is ExprConst -> {
                    val v = if(sign) e.value else -e.value
                    map[eONE] = map[eONE]?.plus(v) ?: v
                }
                is ExprMultiply -> {
                    val (fe, fex) = e.expr[0]
                    var c: Number
                    val ex: AlgebraExpr
                    if(fe is ExprConst && fex == eONE){
                        c = fe.value
                        ex = ExprMultiply(e.expr.subList(1, e.expr.size)).simplify()
                    }else{
                        c = 1
                        ex = e
                    }
                    if(!sign) c = -c
                    map[ex] = map[ex]?.plus(c) ?: c
                }
                is ExprAdd -> {
                    e.expr.forEach { (e1, sign1) -> addItems(e1.simplify(), if(sign) sign1 else !sign1) }
                }
                else -> {
                    val c = if(sign) 1 else -1
                    map[e] = map[e]?.plus(c) ?: c
                }
            }
        }
        expr.forEach { (e, sign) ->
            val e1 = e.simplify()
            addItems(e1, sign)
        }
        val newExpr = map.mapNotNull{ (e, c) ->
            if(c == 0) null
            else if(c == 1) Pair(e, true)
            else if(c == -1) Pair(e, false)
            else if(c > 0) Pair((ExprConst(c) * e).simplify(), true)
            else Pair((ExprConst(-c) * e).simplify(), false)
        }
        if(newExpr.isEmpty()) return eZERO
        if(newExpr.size == 1) return if(newExpr[0].second) newExpr[0].first.apply { setSimplified() } else (-newExpr[0].first).simplify()
        return ExprAdd(newExpr).apply { setSimplified() }
    }
    override fun derivative(varName: String, sub: Map<String, AlgebraExpr>) = ExprAdd(expr.map{ (e, sign) -> Pair(e.derivative(varName, sub), sign) })
    override fun arithmetic(
        values: Map<String, Number>,
        sub: Map<String, AlgebraExpr>?,
        subDict: MutableMap<AlgebraExpr, Number>?
    ): Number{
        subDict?.get(this)?.let { return it }
        return expr.nSumOf { (e, sign) ->
            val v = e.arithmetic(values, sub, subDict)
            if(sign) v else -v
        }.also{ subDict?.set(this, it) }
    }
    override fun substitute(sub: Map<String, AlgebraExpr>) = ExprAdd(expr.map{ (e, sign) -> Pair(e.substitute(sub), sign) })
    override fun getOrder() = 3
    override fun getVariables(): Set<String>{
        val vars = mutableSetOf<String>()
        expr.forEach { (it, _) -> vars.addAll(it.getVariables()) }
        return vars
    }

    override fun equals(other: Any?): Boolean {
        if(other !is ExprAdd) return false
        if(expr.size != other.expr.size) return false
        val target = other.expr.toMutableList()
        expr.forEach { if(!target.remove(it)) return false }
        return target.isEmpty()
    }

    override fun hashCode(): Int = expr.sumOf{ it.first.hashCode() * (if(it.second) 15 else 31) }
    fun <R> mapExprSigned(transfer: (AlgebraExpr) -> R): List<R>{
        return expr.map{ (ae, sign) -> if(sign) transfer(ae) else transfer(-ae) }
    }
    override fun contains(b: AlgebraExpr) = this == b || expr.any{ b in it.first }
    override fun simpleSolve(a: Number): Number {
        if(simplified){
            when(expr.size){
                0 -> throw Exception("No solution!")
                1 -> return if(expr[0].second) expr[0].first.simpleSolve(a) else expr[0].first.simpleSolve(-a)
                else -> {
                    var b = a
                    if(expr.count{ it.first.getVariables().isNotEmpty() } != 1) throw Exception("No solution!")
                    var e: AlgebraExpr? = null
                    expr.forEach { (ee, flag) ->
                        if(ee is ExprConst){
                            if(flag) b -= ee.value else b += ee.value
                        }else if(ee.getVariables().isEmpty()){
                            if(flag) b -= ee.arithmetic() else b += ee.arithmetic()
                        }else{
                            if(e != null) throw Exception("Can not be simply solved!")
                            e = if(flag) ee else -ee
                        }
                    }
                    return e?.simpleSolve(b) ?: throw Exception("No solution!")
                }
            }
        }else{
            return simplify().simpleSolve(a)
        }
    }
    companion object{
        fun exprAdd(_expr: List<AlgebraExpr>) = ExprAdd(_expr.map{ Pair(it, true) })
    }
}