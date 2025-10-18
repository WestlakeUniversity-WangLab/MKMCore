package com.wang_lab.mkm_core.components.solver

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.*
import com.wang_lab.mkm_core.algebra.expr.*
import com.wang_lab.mkm_core.algebra.big_algebra.BDMatrix
import com.wang_lab.mkm_core.algebra.big_algebra.BDErrorQueue
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.algebra.big_decimal_math.*
import com.wang_lab.mkm_core.algebra.expr.ExprAdd.Companion.exprAdd
import com.wang_lab.mkm_core.algebra.expr.ExprConst.Companion.eONE
import com.wang_lab.mkm_core.algebra.number_math.compareTo
import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.misc.*
import com.wang_lab.mkm_core.point.PointInfo
import com.wang_lab.mkm_core.components.solver.iterative_solver.NewtonRoot
import com.wang_lab.mkm_core.exception.MKMRunTimeException
import java.math.BigDecimal

/**
 * Steady state solver for reaction model
 * @param model reaction model
 * The simplest solver, which solves the steady state equations of the reaction model,
 * using Gradient descent method to find the solution.
 */

open class SteadyStateSolver(model: ReactionModel, par: JsonObject): CoverageSolver(model, par) {
    val dthetaToDts: List<AlgebraExpr>
    val rates: List<AlgebraExpr>
    val jacobi: List<MutableList<AlgebraExpr>>
    var current: AlgebraExpr? = null
    var adjust: (BDVector, BDVector) -> Unit = { _, _ -> }
    init{
        rates =  model.reactions.mapIndexed{ i, r -> ExprAdd(
            reactantsProduct(listOf("kf[${i}]"), r.initialState, 1),
            reactantsProduct(listOf("kr[${i}]"), r.finalState, -1)) }
        expressionDictionary["ele"] = eONE
        rates.forEachIndexed { i, u -> expressionDictionary["r[$i]"] = u }
        derivable.forEach{ (sp, expr) -> expressionDictionary[sp.identifier] = expr }
        dthetaToDts = variableList.map { ads ->
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
        forEachZipped(dthetaToDts, variableList){ dt, ads -> expressionDictionary["d${ads.identifier}_dt"] = dt }
        model.gases.forEach { gas ->
            val list = mutableListOf<AlgebraExpr>()
            model.reactions.forEachIndexed { i, r ->
                r.initialState.forEach{ p ->
                    if(p.first === gas)
                        list.add(ExprMultiply(ExprConst(-p.second), ExprVar("r[$i]")))
                }
                r.finalState.forEach{ p ->
                    if(p.first === gas)
                        list.add(ExprMultiply(ExprConst(p.second), ExprVar("r[$i]")))
                }
            }
            expressionDictionary["TOF[$gas]"] = exprAdd(list).simplify()
        }
        model.aqueous.forEach { aq ->
            val list = mutableListOf<AlgebraExpr>()
            model.reactions.forEachIndexed { i, r ->
                r.initialState.forEach{ p ->
                    if(p.first === aq)
                        list.add(ExprMultiply(ExprConst(-p.second), ExprVar("r[$i]")))
                }
                r.finalState.forEach{ p ->
                    if(p.first === aq)
                        list.add(ExprMultiply(ExprConst(p.second), ExprVar("r[$i]")))
                }
            }
            expressionDictionary["TOF[$aq]"] = exprAdd(list).simplify()
        }
        if(model.reactions.any{ it.electron != 0 }){
            val items = mutableListOf<AlgebraExpr>()
            model.reactions.forEachIndexed{ i, r ->
                if(r.electron != 0) items.add(ExprConst(r.electron) *  ExprVar("r[$i]"))
            }
            current = exprAdd(items)
        }
        jacobi = List(variableList.size) { i ->
            variableList.map{ ads_j ->
                dthetaToDts[i].derivative(ads_j.identifier, expressionDictionary).simplify()
            }.toMutableList()
        }
    }
    override fun solveWithInitialValue(p: PointInfo, initialValue: BDVector, source: PointInfo?){
        val timerInit = ThreadTimer()
        val timerIt = ThreadTimer()
        val timerSolve = ThreadTimer()
        val timerUpdate = ThreadTimer()
        val timerFx = ThreadTimer()
        val timerMod = ThreadTimer()

        fun updateVariables(c: BDVector, values: MutableMap<String, BigDecimal>, freeEnergyMap: EnergyList, thermo: Thermo, dict: MutableMap<AlgebraExpr, Number>){
            timerUpdate.start()
            forEachZipped(c, variableList) { cvg, ads -> values[ads.identifier] = cvg }
            derivable.forEach { (si, expr) -> values[si.identifier] = expr.arithmetic(values, expressionDictionary, dict).nToBigDecimal() }
            rates.forEachIndexed { k, r -> values["r[$k]"] = r.arithmetic(values, expressionDictionary, dict).nToBigDecimal() }
            timerUpdate.pause()
        }
        // coverage, variables, energy, thermo
        val f: (BDVector, MutableMap<String, BigDecimal>, EnergyList, Thermo) -> BDVector = { c, vars, freeEnergyMap, thermo ->
            val dict = mutableMapOf<AlgebraExpr, Number>()
            updateVariables(c, vars, freeEnergyMap, thermo, dict)
            timerFx.start()
            val r = dthetaToDts.mapBDV{ dt -> dt.arithmetic(vars, expressionDictionary, dict).nToBigDecimal() }
            timerFx.pause()
            r
        }
        val gradient: (BDVector, BDVector, MutableMap<String, BigDecimal>, EnergyList, Thermo) -> BDVector = { c, fx, vars, freeEnergyMap, thermo ->
            val dict = mutableMapOf<AlgebraExpr, Number>()
            updateVariables(c, vars, freeEnergyMap, thermo, dict)
            timerSolve.start()
            val size = jacobi.size
            val j = BDMatrix(size, size){ i, j -> jacobi[i][j].arithmetic(vars, expressionDictionary, dict).nToBigDecimal() }
            val r = j.solve(-fx)
            timerSolve.pause()
            r
        }
        timerInit.start()
        val initialCoverage0 = inputCoverage(initialValue)
        val thermo = p.thermo
        val freeEnergyMap = p.energyList
        val (kf, kr) = p.rateConstants
        adjust(kf, kr)
        adjust = { _, _ -> }
        val vars = mutableMapOf<String, BigDecimal>("ele" to BigDecimal.ONE)
        thermo.values.forEach { (k, v) ->
            vars[k] = v.nToBigDecimal()
        }
        model.moleculeSpecies.forEach { sp ->
            try{
                sp.concentration?.arithmetic(vars)?.nToBigDecimal()?.let{ vars[sp.identifier] = it }
            }catch (e: NullPointerException){
                vars[sp.identifier] = BigDecimal.ZERO
                expressionDictionary[sp.identifier] = sp.concentration!!
            }
        }
        kf.addToVars(vars){ "kf[$it]" }
        kr.addToVars(vars){ "kr[$it]" }

        val c0 = constraintCoverage(initialCoverage0)

        val nF: (BDVector) -> BDVector = { c -> f(c, vars, freeEnergyMap, thermo) }
        val nGradient: (BDVector, BDVector) -> BDVector = { c, fx -> gradient(c, fx, vars, freeEnergyMap, thermo) }
        val nrSolver = NewtonRoot(nF, norm, nGradient, { constraintCoverage(it) }, c0 )
        val oldError = BDErrorQueue(8)
        var coverages: BDVector? = null
        var errorInfo = "Null result!"
        var i = 0
        val ims = model.modifiers.mapNotNull { it.onSolverStart(p, initialValue, vars, source) }
        timerInit.pause()
        timerIt.start()
        try{
            nrSolver.iterate{ x, e ->
                var error = e
                if(p.coverage != null){
                    logger?.finer("Solver cancelling because $p has been solved.")
                    return@iterate true
                }
                timerMod.start()
                ims.forEach{ it.onIteration(vars, x, error, oldError)?.let { newError -> error = newError } }
                timerMod.pause()
                i += 1
                if(error < tolerance){
                    x.forEachIndexed { i, c ->
                        if(c < minValidValue){
                            errorInfo = "Wrong solution! (c[$i] = ${"%.6e".format(c)})"
                            return@iterate true
                        }
                    }
                    coverages = x
                    return@iterate true
                }
                //else if(!oldError.checkThreshold(errorThreshold, error)){
                //    throw MKMRunTimeException("Divergent results!")
                //}
                oldError.add(error)
                if(i >= maxIterations){
                    errorInfo = "Over max root finding iterations!"
                    return@iterate true
                }
                false
            }
        }catch (e: Throwable){
            e.printStackTrace()
            errorInfo = e.message ?: ""
        }
        timerIt.pause()
        if(coverages != null){
            p.coverage = outputCoverage(coverages!!)
            p.tof = DoubleArray(model.gases.size){ expressionDictionary["TOF[${model.gases[it]}]"]!!.arithmetic(vars).toDouble() }
            current?.let { p.current = it.arithmetic(vars).toDouble() }
        }else{
            throw MKMRunTimeException(errorInfo)
        }
    }
}
