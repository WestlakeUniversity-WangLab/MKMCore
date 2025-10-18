package com.wang_lab.mkm_core.components.thermodynamics.gas_thermo

import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.species.Gas

class EchemThermo(gas: Gas): GasThermo(gas) {
    override fun correctEnthalpy(thermo: Thermo) = 0.0

    override fun getEntropy(thermo: Thermo) = 0.0
}