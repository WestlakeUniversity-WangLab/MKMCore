package com.wang_lab.mkm_core.components.thermodynamics.site_thermo

import com.wang_lab.mkm_core.misc.Thermo
import com.wang_lab.mkm_core.components.thermodynamics.ThermoCorrection

open class SiteThermo: ThermoCorrection() {
    override fun correctEnthalpy(thermo: Thermo) = 0.0

    override fun getEntropy(thermo: Thermo) = 0.0
}