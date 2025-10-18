package com.wang_lab.mkm_core.species

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import com.wang_lab.mkm_core.components.thermodynamics.adsorbate_thermo.AdsorbateThermo
import com.wang_lab.mkm_core.components.thermodynamics.adsorbate_thermo.FixedEnthalpyEntropyAdsorbate
import com.wang_lab.mkm_core.components.thermodynamics.adsorbate_thermo.FrozenAdsorbate
import com.wang_lab.mkm_core.components.thermodynamics.adsorbate_thermo.HarmonicAdsorbate
import com.wang_lab.mkm_core.logger
import com.wang_lab.mkm_core.switchJsonElement

abstract class SurfaceSpecies(name: String, formula: String, composition: Map<String, Int>, val site: Map<SolidSite, Int>,
                              pars: JsonObject? = null, defaultThermoMode: String? = null, config: String = "", charge: Int = 0):
    MoleculeSpecies(name, formula, composition, pars, config, charge){
    //var contentType: ContentType = ContentType.None
    //Adsorbate-adsorbate interaction
    var selfInteraction: List<Double>? = null
    var meanFrequencies: List<Double>? = null
    var meanZPE: Double? = null
    var thermo: AdsorbateThermo? = null
    var kH: Double? = null
    val atomsNum: Int
    init{
        if(pars != null){
            if(pars.containsKey("kH")) kH = pars["kH"]!!.jsonPrimitive.double
        }
        if(pars != null && pars.containsKey("thermo_mode")) setThermo(pars["thermo_mode"]!!.jsonPrimitive.content, pars)
        else if(defaultThermoMode != null) setThermo(defaultThermoMode, pars)
        atomsNum = composition.entries.sumOf { (ele, n) -> if(ele[0] in 'A' .. 'Z') n else 0 }
    }
    override val thermoCorrection = thermo!!
    override val shortName: String
        get() = "$formula*"
    fun calculateMeanFrequencies(){
        val sum = mutableListOf<Double>()
        formationEnergies.forEach{ e ->
            e.frequencies.forEachIndexed{ i, f ->
                if(sum.size == i) sum.add(f) else sum[i] += f
            }
        }
        meanFrequencies = sum.map{ s -> s / formationEnergies.size }
        meanZPE = meanFrequencies!!.sum() / 2
    }

    fun setThermo(mode: String, pars: JsonObject?){
        thermo = when(mode){
            "harmonic_adsorbate" -> HarmonicAdsorbate(this)
            "fixed_enthalpy_entropy_gas" -> FixedEnthalpyEntropyAdsorbate(this, pars)
            "frozen_adsorbate" -> FrozenAdsorbate(this)
            else -> {
                logger?.warning("Unknown adsorbate thermo mode $mode.")
                null
            }
        }
    }

}