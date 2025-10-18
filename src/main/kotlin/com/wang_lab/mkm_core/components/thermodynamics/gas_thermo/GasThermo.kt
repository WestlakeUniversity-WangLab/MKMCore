package com.wang_lab.mkm_core.components.thermodynamics.gas_thermo

import com.wang_lab.mkm_core.species.Gas
import com.wang_lab.mkm_core.components.thermodynamics.ThermoCorrection

abstract class GasThermo(val gas: Gas): ThermoCorrection()