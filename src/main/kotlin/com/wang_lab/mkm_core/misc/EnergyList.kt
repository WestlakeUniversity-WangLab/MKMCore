package com.wang_lab.mkm_core.misc

import Jama.Matrix
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.misc.EnergyType.*
import com.wang_lab.mkm_core.forEachZipped
import com.wang_lab.mkm_core.species.*
import com.wang_lab.mkm_core.species.Adsorbate
import com.wang_lab.mkm_core.species.Gas
import com.wang_lab.mkm_core.species.SolidSite
import com.wang_lab.mkm_core.species.Transition

class EnergyList(val model: ReactionModel): LinkedHashMap<EnergyType, Matrix>() {
    operator fun plus(b: EnergyList): EnergyList{
        if(!(model === b.model)) throw Exception("Energy list from two different reaction models can not add up!")
        val sum = EnergyList(model)
        forEach{ (et, m) -> sum[et] = m.copy() }
        b.forEach{ (et, m) ->  sum[et] = sum[et]?.plus(m) ?: m }
        return sum
    }
    operator fun get(et: EnergyType, i: Int, j: Int = 0) = this[et]?.get(i, j) ?: 0.0
    operator fun set(et: EnergyType, i: Int, j: Int = 0, value: Double){
        if(!containsKey(et)) this[et] = when(et){
            EnergyType.Site -> Matrix(model.sitesE.size, 1)
            EnergyType.Gas -> Matrix(model.gasesE.size, 1)
            EnergyType.Adsorbate -> Matrix(model.adsorbatesE.size, 1)
            EnergyType.Transition -> Matrix(model.transitionsE.size, 1)
            EnergyType.AdsorbateInteraction -> Matrix(model.adsorbates.size, model.adsorbates.size)
            EnergyType.TransitionInteraction -> Matrix(model.transitions.size, model.adsorbates.size)
        }
        this[et]!![i, j] = value
    }
    fun createSites(){
        this[EnergyType.Site] = Matrix(model.sitesE.size, 1)
    }
    operator fun get(sp: Species): Double{
        return when(sp){
            is SolidSite -> this[EnergyType.Site]?.get(model.sitesE.indexOf(sp), 0)
            is Gas -> this[EnergyType.Gas]?.get(model.gasesE.indexOf(sp), 0)
            is Adsorbate -> this[EnergyType.Adsorbate]?.get(model.adsorbatesE.indexOf(sp), 0)
            is Transition -> this[EnergyType.Transition]?.get(model.transitionsE.indexOf(sp), 0)
            else -> 0.0
        } ?: throw Exception("Energy of $sp is not found!")
    }
    operator fun set(sp: Species, energy: Double){
        when(sp){
            is SolidSite -> this[EnergyType.Site]!![model.sitesE.indexOf(sp), 0] = energy
            is Gas -> this[EnergyType.Gas]!![model.gasesE.indexOf(sp), 0] = energy
            is Adsorbate -> this[EnergyType.Adsorbate]!![model.adsorbatesE.indexOf(sp), 0] = energy
            is Transition -> this[EnergyType.Transition]!![model.transitionsE.indexOf(sp), 0] = energy
        }
    }
    fun copy(): EnergyList{
        val el = EnergyList(model)
        forEach { sp, m -> el[sp] = m.copy() }
        return el
    }
    fun interactionRow(ads: SurfaceSpecies): List<Double>{
        val nAds = model.adsorbates.size
        val ir = MutableList(model.solids.size){ 0.0 }
        if(ads is Adsorbate){
            val i = model.adsorbates.indexOf(ads)
            for(j in 0 until nAds)
                ir[j] = this[AdsorbateInteraction]!![i, j]
            for(j in 0 until model.transitions.size)
                ir[nAds+j] = this[TransitionInteraction]!![j, i]
        }else if(ads is Transition){
            val i = model.transitions.indexOf(ads)
            for(j in 0 until nAds)
                ir[j] = this[TransitionInteraction]!![i, j]
        }
        return ir
    }

    override fun toString(): String {
        val sb = StringBuilder()
        fun add(et: EnergyType, list: List<Species>){
            sb.append("$et:\n")
            forEachZipped(this[et]!!.array.map{it[0]}, list){ e, sp ->
                sb.append("${sp.name}\t${"%.3f".format(e)} eV\n")
            }
        }
        add(EnergyType.Gas, model.gasesE)
        add(EnergyType.Adsorbate, model.adsorbatesE)
        add(EnergyType.Transition, model.transitionsE)
        return sb.toString()
    }
}

enum class EnergyType(val id: Int){
    Site(0), Gas(1), Adsorbate(2), Transition(3), AdsorbateInteraction(4), TransitionInteraction(5)
}
