package com.wang_lab.mkm_core.components.scaler

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.exception.MKMSetupException
import com.wang_lab.mkm_core.misc.*
import com.wang_lab.mkm_core.point.PointInfo
import com.wang_lab.mkm_core.species.*
import com.wang_lab.mkm_core.switchJsonElement

open class Scaler(val model: ReactionModel, par: JsonObject) {
    val surfaceNames = switchJsonElement(par["surface_names"], "Surface names", s = { listOf(it) }, a = { it.map{ s -> s.jsonPrimitive.content } }, v = { listOf() })
    fun buildFreeEnergyList(p: PointInfo) {
        val ele = getElectronicEnergyList(p)
        val elt = getThermoEnergyList(p.thermo, ele)
        ele.forEach{ (et, m) -> p.energyList[et] = m.copy() }
        elt.forEach{ (et, m) ->  p.energyList[et] = p.energyList[et]?.plus(m) ?: m }
    }
    fun getFE(sp: SurfaceSpecies, thermoVar: Map<String, Double>): Double =
        if(surfaceNames.isEmpty()) sp.formationEnergies.firstOrNull()?.formationEnergy?.arithmetic(thermoVar)?.toDouble()
            ?: throw MKMSetupException("formation_energy", "${sp.name} has no formation energy on surface \"${surfaceNames[0]}\".")
        else
            sp.formationEnergies.firstOrNull{ it.attributes["surface_name"] == surfaceNames[0] }?.formationEnergy?.arithmetic(thermoVar)?.toDouble()
                ?: throw MKMSetupException("formation_energy", "${sp.name} has no formation energy on surface \"${surfaceNames[0]}\".")

    open fun getElectronicEnergy(sp:Species, p: PointInfo): Double {
        val thermoVar = p.thermo.values
        return when(sp){
            is SolidSite -> 0.0
            is SurfaceSpecies -> getFE(sp, thermoVar)
            is Gas -> sp.energyInfo.formationEnergy.arithmetic(thermoVar).toDouble()
            else -> throw MKMSetupException("scaling_species_type", "ThermodynamicScaler does not support ${sp.name} as ${sp.javaClass}.")
        }
    }
    open fun getElectronicEnergyList(p: PointInfo): EnergyList{
        val el = EnergyList(model)
        el.createSites()
        val thermoVar = p.thermo.values
        model.gasesE.forEachIndexed { i, gas -> el[EnergyType.Gas, i] = gas.energyInfo.formationEnergy.arithmetic(thermoVar).toDouble() }
        model.adsorbatesE.forEachIndexed { i, ads -> el[EnergyType.Adsorbate, i] = getFE(ads, thermoVar)}
        model.transitionsE.forEachIndexed { i, ts -> el[EnergyType.Transition, i] =  if(ts is EChemTS) 0.0 else getFE(ts, thermoVar) }
        return el
    }
    private fun getThermoEnergyList(thermo: Thermo, electronicEnergy: EnergyList): EnergyList{
        val el = EnergyList(model)
        el.createSites()
        model.sitesE.forEachIndexed { i, site -> el[EnergyType.Site, i] = site.thermoCorrection.correctFreeEnergy(thermo) }
        model.gasesE.forEachIndexed { i, gas -> el[EnergyType.Gas, i] = gas.thermoCorrection.correctFreeEnergy(thermo) }
        model.adsorbatesE.forEachIndexed { i, ads -> el[EnergyType.Adsorbate, i] = ads.thermoCorrection.correctFreeEnergy(thermo) }
        model.transitionsE.forEachIndexed { i, ts ->
            if(ts.meanFrequencies?.isNotEmpty() == true) {
                el[EnergyType.Transition, i] = ts.thermoCorrection.correctFreeEnergy(thermo)
            }else if(ts.reaction != null){
                val eIS = ts.reaction!!.initialState.sumOf { (sp, n) -> if(sp is Adsorbate) el[sp] * n else 0.0 }
                val eFS = ts.reaction!!.finalState.sumOf { (sp, n) -> if(sp is Adsorbate) el[sp] * n else 0.0 }
                el[EnergyType.Transition, i] = (eIS + eFS) / 2.0
            }
        }
        return el
    }
}