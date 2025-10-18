package com.wang_lab.mkm_core.components.thermodynamics.gas_thermo

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.species.Gas
import com.wang_lab.mkm_core.switchJsonElement

class FixedEnthalpyEntropyGas(gas: Gas, pars: JsonObject?): FrozenFixedEntropyGas(gas, pars){
    private val enthalpy = switchJsonElement(pars?.get("enthalpy"), "enthalpy of ${gas.name}", d = {it})
    override fun correctEnthalpy(thermo: Thermo) = gas.energyInfo!!.zpe + enthalpy
}