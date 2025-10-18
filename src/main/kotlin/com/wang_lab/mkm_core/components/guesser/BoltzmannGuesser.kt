package com.wang_lab.mkm_core.components.guesser

import Jama.Matrix
import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.algebra.big_decimal_math.div
import com.wang_lab.mkm_core.algebra.big_decimal_math.nToBigDecimal
import com.wang_lab.mkm_core.constants.kB_e
import com.wang_lab.mkm_core.indexedSumOf
import com.wang_lab.mkm_core.logger
import com.wang_lab.mkm_core.misc.EnergyList
import com.wang_lab.mkm_core.misc.EnergyType
import com.wang_lab.mkm_core.misc.ThermoDescriptor
import com.wang_lab.mkm_core.point.PointInfo
import com.wang_lab.mkm_core.species.Gas
import com.wang_lab.mkm_core.sum
import java.math.BigDecimal
import kotlin.math.exp

open class BoltzmannGuesser(model: ReactionModel, par: JsonObject): Guesser(model, par) {
    private val atomicReservoirList = mutableListOf<Map<String, Gas>>()
    private val atomsList = mutableListOf<String>()
    private var preferredReservoir: Map<String, Gas>? = null
    init{
        val atomsSet = mutableSetOf<String>()
        val reservoirSetList = mutableListOf<Set<Gas>>()
        if(model.adsorbatesE.containsAll(model.adsorbates)) try {
            val cart = mutableListOf<List<Gas>>()
            model.gases.forEach { sp -> atomsSet.addAll(sp.composition.keys) }
            atomsSet.forEach{ a ->
                val possibles = ArrayList<Gas>()
                model.gases.forEach{ sp -> if(a in sp.composition.keys) possibles.add(sp)}
                cart.add(possibles)
            }
            val position = Array(atomsSet.size){ 0 }
            atomsList .addAll(atomsSet.toList())
            val testEnergyMap = EnergyList(model)
            testEnergyMap[EnergyType.Gas] = Matrix(model.gases.size, 1)
            if(atomsSet.isNotEmpty()) r@while(true){
                val arl = mutableMapOf<String, Gas>()
                for(i in position.indices){
                    val j = position[i]
                    val sp = cart[i][j]
                    if(sp in arl.values) break else arl[atomsList[i]] = sp
                }
                if(arl.size == atomsList.size) try{
                    val set = arl.values.toSet()
                    if(set !in reservoirSetList) {
                        convertFormationEnergies(testEnergyMap, arl)
                        reservoirSetList.add(set)
                        atomicReservoirList.add(arl)
                    }
                }catch (_: Exception){}
                position[position.size-1] ++
                var p = position.size - 1
                while(position[p] == cart[p].size){
                    position[p] = 0
                    p --
                    if(p == -1) break@r
                    position[p] ++
                }
            }
        }catch (_: Exception){
            logger?.severe("Error in generating atom reservoir list! No Boltzmann coverage will not be used as the initial coverage!")
        }
    }
    private fun convertFormationEnergies(freeEnergyMap: EnergyList, arl: Map<String, Gas>): EnergyList{
        val coordinates = Matrix(arl.values.map{ sp ->
            atomsList.map{ if(sp.composition.containsKey(it)) sp.composition[it]!!.toDouble() else 0.0 }.toDoubleArray()
        }.toTypedArray())
        val energies = Matrix(arl.values.map{ sp -> doubleArrayOf(freeEnergyMap[sp]) }.toTypedArray())
        val atomEnergies = coordinates.solve(energies)!!
        val el = freeEnergyMap.copy()
        model.adsorbates.forEachIndexed{ i, ads ->
            val c = ads.composition
            el[EnergyType.Adsorbate, i] -= atomsList.indexedSumOf { j, a -> if(c.containsKey(a)) c[a]!!.times(atomEnergies[j, 0]) else 0.0 }
        }
        return el
    }
    protected fun arlToInitCvg(arl: Map<String, Gas>, point: PointInfo): BDVector {
        val relatedEnergyMap = convertFormationEnergies(point.energyList, arl)
        val kT = kB_e * point.thermo.t!!
        val boltzmannIndex = relatedEnergyMap[EnergyType.Adsorbate]!!.columnPackedCopy.map { exp(-it / kT).nToBigDecimal() }
        val k = boltzmannIndex.sum().plus(BigDecimal.ONE)
        return BDVector(boltzmannIndex.size) { boltzmannIndex[it].div(k) }
    }

    override fun forEachInitialGuess(point: PointInfo, action: (String, BDVector) -> Boolean) {
        val o = model.solver.getValue(point)
        if(o != null) if(action("original data", o)) return
        val pr = preferredReservoir
        if(pr != null) if(action("preferred reservoir $pr", arlToInitCvg(pr, point))) return
        for(arl in atomicReservoirList){
            if(action("reservoir $pr", arlToInitCvg(arl, point))){
                preferredReservoir = arl
                return
            }
        }
        if(action("zero coverage", zeroCoverage)) return
        if(action("average coverage", averageCoverage)) return
    }
}