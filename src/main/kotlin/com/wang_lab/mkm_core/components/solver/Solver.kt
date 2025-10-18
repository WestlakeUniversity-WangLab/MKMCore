package com.wang_lab.mkm_core.components.solver

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.algebra.addAllR
import com.wang_lab.mkm_core.algebra.addR
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.algebra.big_decimal_math.decimalPrecision
import com.wang_lab.mkm_core.algebra.expr.*
import com.wang_lab.mkm_core.algebra.mmap
import com.wang_lab.mkm_core.point.PointInfo
import com.wang_lab.mkm_core.components.solver.algorithm.NormalFunctions
import com.wang_lab.mkm_core.species.*
import com.wang_lab.mkm_core.switchJsonElement
import java.math.BigDecimal
import java.math.BigInteger

abstract class Solver(val model: ReactionModel, par: JsonObject) {

    val minValue = BigDecimal(BigInteger.ONE, decimalPrecision)
    val minValidValue = minValue + minValue
    var errorThreshold = switchJsonElement(par["error_threshold"], "Error threshold", s = { BigDecimal(it) }, v = { BigDecimal(0.9) })
    var tolerance = switchJsonElement(par["tolerance"], "Tolerance", d = { it }, v = { 1e-30 })
    var norm = switchJsonElement(par["norm"], "Normal function", s = { NormalFunctions.values().first { n -> n.name == it } }, v = { NormalFunctions.AbsoluteMaximum }).function
    val maxIterations = switchJsonElement(par["max_iterations"], "Max root-finding iterations", i = { it }, v = { 100 })
    val expressionDictionary = mutableMapOf<String, AlgebraExpr>()
    abstract fun getValue(p: PointInfo): BDVector?
    abstract fun setValue(p: PointInfo, value: BDVector?)
    abstract fun validPointValue(p: PointInfo): Boolean
    abstract fun solveWithInitialValue(p: PointInfo, initialValue: BDVector, source: PointInfo? = null)
    abstract fun plotTypes(): List<String>
    open fun solveWithInitialGuess(p: PointInfo){
        model.guesser.tryInitialGuesses(p){
            solveWithInitialValue(p, it)
        }
    }
    companion object{
        fun reactantsProduct(prefactors: List<String>, reactants: List<Pair<Species, Int>>, co: Int = 1) = ExprMultiply(
            prefactors.mmap<String, AlgebraExpr>{ ExprVar(it) }.addR(ExprConst(co)).addAllR(reactants.mmap{ r ->
                ExprVar(r.first.identifier, r.second)
            })).simplify()
    }
}