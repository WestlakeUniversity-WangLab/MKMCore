package com.wang_lab.mkm_core.species

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.components.thermodynamics.adsorbate_thermo.FrozenAdsorbate

class EChemTS(barrier: String, site: Map<SolidSite, Int>, composition: Map<String, Int>, index: Int,
              pars: JsonObject? = null, defaultThermoMode: String? = null):
    Transition("eChemTS-$index-${barrier}_$site", "", composition, site, pars, defaultThermoMode){
    val barrier = barrier.toDouble()
    init {
        thermo = FrozenAdsorbate(this)
    }
}