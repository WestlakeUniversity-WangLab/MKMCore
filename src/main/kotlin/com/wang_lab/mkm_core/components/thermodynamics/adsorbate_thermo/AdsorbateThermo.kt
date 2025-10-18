package com.wang_lab.mkm_core.components.thermodynamics.adsorbate_thermo

import com.wang_lab.mkm_core.species.SurfaceSpecies
import com.wang_lab.mkm_core.components.thermodynamics.ThermoCorrection

abstract class AdsorbateThermo(val solid: SurfaceSpecies): ThermoCorrection()