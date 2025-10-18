package com.wang_lab.mkm_core.components.thermodynamics.gas_thermo

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.species.Gas

class FixedEntropyGas(gas: Gas, pars: JsonObject?): FrozenFixedEntropyGas(gas, pars){
    override fun correctEnthalpy(thermo: Thermo) = gas.energyInfo!!.zpe
}