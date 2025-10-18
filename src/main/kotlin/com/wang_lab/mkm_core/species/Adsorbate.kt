package com.wang_lab.mkm_core.species

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import com.wang_lab.mkm_core.jpype.PyInteraction
import com.wang_lab.mkm_core.misc.EnergyInfo

class Adsorbate(name: String, formula: String, composition: Map<String, Int>, site: Map<SolidSite, Int>,
                pars: JsonObject? = null, defaultThermoMode: String? = null, config: String = "", charge: Int = 0):
    SurfaceSpecies(name, formula, composition, site, pars, defaultThermoMode, config, charge){
    var maxCoverage: Double? = null
    init{
        if(pars != null && pars.containsKey("max_coverage"))
            maxCoverage = pars["max_coverage"]!!.jsonPrimitive.double
    }
    override val identifier = "θ[$name]"

    override fun setEnergyInfo(fe: EnergyInfo, pi: PyInteraction) {
        val vibNum = atomsNum * 3
        if(fe.frequencies.size != vibNum){
            pi.warning.transfer("The energy info of ${this.name} on $name has ${fe.frequencies.size} frequencies, but expected to be $vibNum. Please check!")
        }
        formationEnergies.add(fe)
    }
}