package com.wang_lab.mkm_core.components.thermodynamics.gas_thermo

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.species.Gas
import com.wang_lab.mkm_core.switchJsonElement

open class FrozenFixedEntropyGas(gas: Gas, pars: JsonObject?): GasThermo(gas){
    private val entropy = switchJsonElement(pars?.get("entropy"), "entropy of ${gas.name}", d = {it})
    override fun correctEnthalpy(thermo: Thermo) = 0.0
    override fun getEntropy(thermo: Thermo) = entropy
}