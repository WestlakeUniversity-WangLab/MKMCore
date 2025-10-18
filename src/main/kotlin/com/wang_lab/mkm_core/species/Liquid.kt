package com.wang_lab.mkm_core.species

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.components.thermodynamics.ThermoCorrection

class Liquid(name: String, formula: String, composition: Map<String, Int>, pars: JsonObject? = null,
             defaultThermoMode: String? = null, config: String = "", charge: Int = 0):
    MoleculeSpecies(name, formula, composition, pars, config, charge){
    override val identifier = "l[$name]"
    override val thermoCorrection: ThermoCorrection
        get() = TODO("Not yet implemented")
}