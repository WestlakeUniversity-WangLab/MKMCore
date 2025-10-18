package com.wang_lab.mkm_core.components.solver

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.algebra.addAllR
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.algebra.big_decimal_math.*
import com.wang_lab.mkm_core.algebra.expr.*
import com.wang_lab.mkm_core.algebra.expr.ExprAdd.Companion.exprAdd
import com.wang_lab.mkm_core.forEachZipped
import com.wang_lab.mkm_core.logger
import com.wang_lab.mkm_core.point.PointInfo
import com.wang_lab.mkm_core.species.*
import java.math.BigDecimal
import kotlin.div
import kotlin.math.pow

/**
 * This class is used for single system. Here only the coverages of adsorbates are variables. Pressure of gases are constants.
 */

abstract class CoverageSolver(model: ReactionModel, par: JsonObject): Solver(model, par) {
    val derivable: List<Pair<Species, AlgebraExpr>> = model.sites.mapNotNull { s ->
        val list = mutableListOf<AlgebraExpr>(ExprConst(s.total))
        val related = model.species.values.mapNotNull{ sp ->
            if(sp.notVirtual && sp is MoleculeSpecies && sp is Adsorbate && s in sp.site)
                Pair(sp, ExprMultiply(ExprConst(-sp.site[s]!!), ExprVar(sp.identifier)))
            else
                null
        }.toMutableList()
        if(s.notVirtual){
            if(related.isEmpty()){
                logger?.warning("There is no species on site ${s.name}.")
                return@mapNotNull null
            }
            Pair(s, exprAdd(list.addAllR(related.map{ it.second })).simplify())
        }else{
            if(related.size < 2){
                logger?.warning("There is only one species on site ${s.name}, which cannot be empty.")
                return@mapNotNull null
            }
            val sp = related.minBy { it.first.composition.values.sum() }.first
            Pair(sp, exprAdd(list.addAllR(related.mapNotNull{ if(it.first != sp) it.second else null })).simplify())
        }
    }
    val variableList: List<Adsorbate> = model.adsorbates.filter { ads ->
        derivable.forEach{ (sp, _) ->
            if(ads == sp) return@filter false
        }
        true
    }
    protected val variableMap: IntArray = IntArray(variableList.size){ model.adsorbates.indexOf(variableList[it]) }
    open fun inputCoverage(coverage: BDVector): BDVector{
        //if(coverage.size == variableList.size) return coverage
        if(coverage.size == model.adsorbates.size) return BDVector(variableList.size){ coverage[variableMap[it]] }
        throw Exception("The size of coverage input is invalid(${coverage.size}), but is expected to convert from ${model.adsorbates.size} to ${variableList.size}.")
    }
    open fun outputCoverage(coverage: BDVector): BDVector{
        //if(coverage.size == model.adsorbates.size) return coverage
        if(coverage.size == variableList.size){
            val out = BDVector(model.adsorbates.size)
            coverage.forEachIndexed{ i, c -> out[variableMap[i]] = c }
            val values = mutableMapOf<String, BigDecimal>()
            forEachZipped(variableList, coverage){ ads, cvg -> values[ads.identifier] = cvg }
            derivable.forEach{ (sd, expr) ->
                if(sd is Adsorbate)
                    out[model.adsorbates.indexOf(sd)] = expr.arithmetic(values).nToBigDecimal()
            }
            return out
        }
        throw Exception("The size of coverage output is invalid(${coverage.size}).")
    }
    open fun constraintCoverage(x: BDVector): BDVector{
        val c = x.transformIndexed { i, it -> it.clamp(minValue, BigDecimal.valueOf(variableList[i].maxCoverage ?: 1.0)) }
        val sums = model.sites.map{ s ->
            var sum = BigDecimal.ZERO
            forEachZipped(variableList, c){ ads, c0 ->
                if(s in ads.site){
                    sum = sum.plus(c0.times(ads.site[s]!!))
                }
            }
            sum
        }
        val sum = if(sums.isEmpty()) BigDecimal.ZERO else sums.max()
        return if(sum > BigDecimal.ONE) c.transform { it.div(sum) } else c
    }

    override fun getValue(p: PointInfo) = p.coverage
    override fun setValue(p: PointInfo, value: BDVector?){
        p.coverage = value
    }
    override fun validPointValue(p: PointInfo): Boolean {
        if(p.tof == null) return false
        if(p.coverage == null) return false
        for(i in model.adsorbates.indices){
            val cvg = p.coverage!![i].toDouble()
            if(cvg <= 0.0) return false
            if(cvg <= 10.0.pow(-decimalPrecision)) return false
        }
        return true
    }
    override fun plotTypes(): List<String>{
        val list = mutableListOf("coverage", "TOF")
        if(model.selectivityAtom.isNotEmpty()) list.add("selectivity")
        if(model.reactions.any{ it.electron != 0 }){
            list.add("current")
            if(model.adsorbates.any { (it.site.keys.first() as? SolidSite)?.density != null }) list.add("current density")
        }
        return list
    }

}