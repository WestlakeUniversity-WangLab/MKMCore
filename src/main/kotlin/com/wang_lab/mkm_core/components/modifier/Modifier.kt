package com.wang_lab.mkm_core.components.modifier

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.components.modifier.iterator_modifier.IteratorModifier
import com.wang_lab.mkm_core.point.PointInfo
import java.math.BigDecimal

abstract class Modifier(val model: ReactionModel) {
    open fun initialize(initPars: JsonObject){}
    open fun onSolverStart(p: PointInfo, initialValue: BDVector, vars: MutableMap<String, BigDecimal>, source: PointInfo? = null): IteratorModifier?{ return null }
    open fun onSolverFinish(){}
}