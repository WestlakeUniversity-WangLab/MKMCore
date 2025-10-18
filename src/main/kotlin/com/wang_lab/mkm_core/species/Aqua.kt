package com.wang_lab.mkm_core.species

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.switchJsonElement
import com.wang_lab.mkm_core.components.thermodynamics.ThermoCorrection

class Aqua(name: String, formula: String, composition: Map<String, Int>,
           pars: JsonObject? = null, defaultThermoMode: String? = null, config: String = "", charge: Int):
    MoleculeSpecies(name, formula, composition, pars, config, charge){
    var diffusivity: Double? = null
    init{
        if(pars != null){
            switchJsonElement(pars["diffusivity"], "diffusivity of $name",
                d = { diffusivity = it },
                v = {}
            )
        }
    }
    override val identifier = "c[$name]"
    override val thermoCorrection: ThermoCorrection
        get() = TODO("Not yet implemented")
}