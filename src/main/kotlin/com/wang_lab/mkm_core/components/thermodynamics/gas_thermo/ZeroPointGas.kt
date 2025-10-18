package com.wang_lab.mkm_core.components.thermodynamics.gas_thermo

import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.species.Gas

class ZeroPointGas(gas: Gas): GasThermo(gas){
    override fun correctEnthalpy(thermo: Thermo) = gas.energyInfo.zpe
    override fun getEntropy(thermo: Thermo) = 0.0
}