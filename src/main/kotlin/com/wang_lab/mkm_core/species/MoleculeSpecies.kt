package com.wang_lab.mkm_core.species

import kotlinx.serialization.json.*
import com.wang_lab.mkm_core.algebra.expr.AlgebraExpr
import com.wang_lab.mkm_core.algebra.expr.ExprConst
import com.wang_lab.mkm_core.algebra.expr.parseExpression
import com.wang_lab.mkm_core.switchJsonElement

abstract class MoleculeSpecies(name: String, val formula: String, var composition: Map<String, Int>,
                               pars: JsonObject? = null, val config: String = "", charge: Int = 0):
    Species(name, charge) {
    open val concentration: AlgebraExpr? = switchJsonElement(
        pars?.get("concentration"),
        "concentration of $name",
        d = { ExprConst(it) },
        s = { parseExpression(it) },
        v = { null }
    )
}