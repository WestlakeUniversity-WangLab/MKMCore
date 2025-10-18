package com.wang_lab.mkm_core.components.solver

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.algebra.addAllR
import com.wang_lab.mkm_core.algebra.expr.*
import com.wang_lab.mkm_core.algebra.expr.ExprAdd.Companion.exprAdd
import com.wang_lab.mkm_core.algebra.nAddAllR
import com.wang_lab.mkm_core.forEachZipped
import com.wang_lab.mkm_core.logger
import com.wang_lab.mkm_core.species.*
import java.math.BigDecimal

/**
 * A more general solver, regarding all species as variables. This is used for systems that contain complex constraints.
 */
abstract class ConstraintSolver(model: ReactionModel, par: JsonObject): Solver(model, par) {
    val fluxibleSpecies = model.species.values.filterIsInstance<MoleculeSpecies>().filter{ it is Aqua || it is Gas }
    val variableFluxibleSpecies = fluxibleSpecies.filter{ true }
    val variableSpecies = fluxibleSpecies.nAddAllR(model.adsorbates)
    val sitesLimited: List<Species>
    val adsList: List<Adsorbate>
    val adsMap: IntArray
    val sites: List<AlgebraExpr>
    val rates: List<AlgebraExpr>
    val tofs: List<AlgebraExpr>

    val surfaceReactions = model.reactions.filter {
        for(sp in it.initialState) if(sp.first is SurfaceSpecies || sp.first is SolidSite) return@filter true
        for(sp in it.finalState) if(sp.first is SurfaceSpecies || sp.first is SolidSite) return@filter true
        false
    }
    val nonSurfaceReactions = model.reactions.filter{ it !in surfaceReactions}
    var gasScale = BigDecimal.ONE
    init{
        val tSites = mutableListOf<AlgebraExpr>()
        model.sites.forEach { s ->
            val list = mutableListOf<AlgebraExpr>(ExprConst(s.total))
            val related = model.species.values.filterIsInstance<MoleculeSpecies>().mapNotNull{ sp ->
                if(sp.notVirtual && sp is Adsorbate && s in sp.site) {
                    Pair(sp, ExprMultiply(ExprConst(-sp.site[s]!!), ExprVar(sp.identifier)))
                }else{
                    null
                }
            }.toMutableList()
            if(s.notVirtual){
                if(related.isEmpty()) return@forEach
                tSites.add(exprAdd(list.addAllR(related.map{ it.second })).simplify())
            }else{
                if(related.size < 2) return@forEach
                val sp = related.minBy { it.first.composition.values.sum() }.first
                tSites.add(exprAdd(list.addAllR(related.mapNotNull{ if(it.first != sp) it.second else null })).simplify())
            }
        }
        sites = tSites.toList()

        val tSitesLimited = mutableListOf<Species>()
        model.sites.forEach { s ->
            val related = model.species.values.filterIsInstance<MoleculeSpecies>().filter{ sp ->
                sp.notVirtual && sp is Adsorbate && s in sp.site
            }
            if(s.notVirtual){
                if(related.isEmpty()){
                    logger?.warning("There is no species on site ${s.name}.")
                    return@forEach
                }
                tSitesLimited.add(s)
            }else{
                if(related.size < 2){
                    logger?.warning("There is only one species on site ${s.name}, which cannot be empty.")
                    return@forEach
                }
                val sp = related.minBy { it.composition.values.sum() }
                tSitesLimited.add(sp)
            }
        }
        sitesLimited = tSitesLimited.toList()
        adsList = model.adsorbates.filter { ads -> ads !in sitesLimited }
        adsMap = IntArray(adsList.size){ model.adsorbates.indexOf(adsList[it]) }
        rates =  model.reactions.mapIndexed{ i, r -> ExprAdd(
            reactantsProduct(listOf("kf[$i]"), r.initialState, 1),
            reactantsProduct(listOf("kr[$i]"), r.finalState, -1))
        }
        forEachZipped(sitesLimited, sites){
                s, ex -> expressionDictionary[s.identifier] = ex
        }
        rates.forEachIndexed{ i, ex -> expressionDictionary["r[$i]"] = ex }
        tofs = model.gases.map { gas ->
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
            exprAdd(list).simplify()
        }
    }
}