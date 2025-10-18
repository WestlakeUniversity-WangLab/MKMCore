package com.wang_lab.mkm_core.components.guesser

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.algebra.big_algebra.BDErrorQueue
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.algebra.big_decimal_math.nToBigDecimal
import com.wang_lab.mkm_core.algebra.expr.*
import com.wang_lab.mkm_core.algebra.expr.ExprAdd.Companion.exprAdd
import com.wang_lab.mkm_core.forEachZipped
import com.wang_lab.mkm_core.mapBDV
import com.wang_lab.mkm_core.misc.EnergyList
import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.point.PointInfo
import com.wang_lab.mkm_core.components.solver.CoverageSolver
import com.wang_lab.mkm_core.components.solver.Solver
import com.wang_lab.mkm_core.components.solver.algorithm.NormalFunctions
import com.wang_lab.mkm_core.components.solver.iterative_solver.GradientRoot
import com.wang_lab.mkm_core.switchJsonElement
import java.math.BigDecimal

class ODEGuesser(model: ReactionModel, par: JsonObject): Guesser(model, par)  {
    private var roughTolerance = 1e-3
    private var veryRoughTolerance = 1e3
    var norm = switchJsonElement(par["norm"], "Normal function", s = { NormalFunctions.values().first { n -> n.name == it } }, v = { NormalFunctions.AbsoluteMaximum }).function
    val maxIterations = switchJsonElement(par["max_iterations"], "Max root-finding iterations", i = { it }, v = {100})

    val dthetaToDts: List<AlgebraExpr> = model.adsorbates.map { ads ->
        val list = mutableListOf<AlgebraExpr>()
        model.reactions.forEachIndexed { i, r ->
            r.initialState.forEach{ p ->
                if(p.first === ads)
                    list.add(ExprMultiply(ExprConst(-p.second), ExprVar("r[$i]")))
            }
            r.finalState.forEach{ p ->
                if(p.first === ads)
                    list.add(ExprMultiply(ExprConst(p.second), ExprVar("r[$i]")))
            }
        }
        exprAdd(list).simplify()
    }
    val rates =  model.reactions.mapIndexed{ i, r -> ExprAdd(
        Solver.reactantsProduct(listOf("kf[${i}]"), r.initialState, 1),
        Solver.reactantsProduct(listOf("kr[${i}]"), r.finalState, -1)) }
    private fun updateVariables(c: BDVector, values: MutableMap<String, BigDecimal>, freeEnergyMap: EnergyList, thermo: Thermo){
        forEachZipped(c, (model.solver as CoverageSolver).variableList) { cvg, ads -> values[ads.identifier] = cvg }
        model.solver.derivable.forEach { (si, expr) -> values[si.identifier] = expr.arithmetic(values).nToBigDecimal() }
        rates.forEachIndexed { k, r -> values["r[$k]"] = r.arithmetic(values).nToBigDecimal() }
    }
    override fun forEachInitialGuess(point: PointInfo, action: (String, BDVector) -> Boolean) {
        val o = model.solver.getValue(point)
        if(o != null) if(action("original data", o)) return
        if(action("zero coverage", zeroCoverage)) return
        if(action("average coverage", averageCoverage)) return
        val initialCoverage0 = (model.solver as CoverageSolver).inputCoverage(BDVector(model.adsorbates.size))
        val freeEnergyMap = point.energyList
        val thermo = point.thermo
        val (kf, kr) = point.rateConstants
        val vars = mutableMapOf<String, BigDecimal>("ele" to BigDecimal.ONE, "l[H2O_l]" to BigDecimal.ONE)
        thermo.values.forEach { (k, v) ->
            vars[k] = v.nToBigDecimal()
        }
        model.moleculeSpecies.forEach { sp -> sp.concentration?.arithmetic(vars)?.nToBigDecimal()?.let{ vars[sp.identifier] = it } }
        model.gases.forEach { g -> g.concentration.arithmetic(vars).nToBigDecimal().let{ vars[g.identifier] = it } }
        kf.addToVars(vars){  "kf[$it]" }
        kr.addToVars(vars){  "kr[$it]" }
        val f: (BDVector, MutableMap<String, BigDecimal>) -> BDVector = { c, values ->
            updateVariables(c, values, freeEnergyMap, thermo)
            val r = dthetaToDts.mapBDV{ dt -> dt.arithmetic(vars).nToBigDecimal() }
            r
        }
        val nF: (BDVector) -> BDVector = { c -> f(c, vars) }
        val trSolver = GradientRoot(nF, norm, initialCoverage0)
        val oldError = BDErrorQueue(3)
        var i = 0
        var cd = 0
        trSolver.iterate{ x, error ->
            i += 1
            if(error.toDouble() < roughTolerance){
                if(action("integrated result after $i steps", model.solver.outputCoverage(x))) return@iterate true
            }
            oldError.add(error)
            if(i > maxIterations * 10) return@iterate true
            cd ++
            if(cd < maxIterations) return@iterate false
            val valid = x.all{ it >= model.solver.minValidValue }
            if(valid && error.toDouble() < veryRoughTolerance) {
                if(action("integrated result after $i steps", model.solver.outputCoverage(x))) return@iterate true
                cd = 0
            }
            false
        }
    }
}