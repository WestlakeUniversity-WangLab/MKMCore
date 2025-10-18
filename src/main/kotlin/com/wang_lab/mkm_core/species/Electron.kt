package com.wang_lab.mkm_core.species

import com.wang_lab.mkm_core.algebra.expr.AlgebraExpr
import com.wang_lab.mkm_core.algebra.expr.ExprConst
import com.wang_lab.mkm_core.components.thermodynamics.ThermoCorrection
import com.wang_lab.mkm_core.components.thermodynamics.site_thermo.SiteThermo

object Electron: Species("ele", -1) {
    override val identifier: String = "ele"
    override val thermoCorrection: ThermoCorrection = SiteThermo()
    override val exprVar: AlgebraExpr = ExprConst.eONE
}