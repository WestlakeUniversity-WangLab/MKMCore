package com.wang_lab.mkm_core.components.thermodynamics.adsorbate_thermo

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.species.SurfaceSpecies
import com.wang_lab.mkm_core.switchJsonElement

class FixedEnthalpyEntropyAdsorbate(solid: SurfaceSpecies, pars: JsonObject?): AdsorbateThermo(solid) {
    private val enthalpy = switchJsonElement(pars?.get("enthalpy"), "enthalpy of ${solid.name}", d = {it})
    private val entropy = switchJsonElement(pars?.get("entropy"), "entropy of ${solid.name}", d = {it})
    override fun correctEnthalpy(thermo: Thermo) = solid.meanZPE!! + enthalpy
    override fun getEntropy(thermo: Thermo) = entropy
}