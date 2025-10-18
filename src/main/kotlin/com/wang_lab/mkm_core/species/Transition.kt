package com.wang_lab.mkm_core.species

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.jpype.PyInteraction
import com.wang_lab.mkm_core.misc.EnergyInfo
import com.wang_lab.mkm_core.reaction.Reaction

open class Transition(name: String, formula: String, composition: Map<String, Int>, site: Map<SolidSite, Int>,
                      pars: JsonObject? = null, defaultThermoMode: String? = null, config: String = "", charge: Int = 0):
    SurfaceSpecies(name, formula, composition, site, pars, defaultThermoMode, config, charge){
    var reaction: Reaction? = null
    override val identifier = "ts[$name]"

    override fun setEnergyInfo(fe: EnergyInfo, pi: PyInteraction) {
        val vibNum = atomsNum * 3 - 1
        if(fe.frequencies.size != vibNum)
            pi.warning.transfer("The energy info of ${this.name} on $name has ${fe.frequencies.size} frequencies, but expected to be $vibNum. Please check!")
        formationEnergies.add(fe)
    }
}