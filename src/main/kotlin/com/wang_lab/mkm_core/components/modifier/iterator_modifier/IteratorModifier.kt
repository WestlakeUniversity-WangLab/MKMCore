package com.wang_lab.mkm_core.components.modifier.iterator_modifier

import com.wang_lab.mkm_core.algebra.big_algebra.BDErrorQueue
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import java.math.BigDecimal

abstract class IteratorModifier {
    open fun onIteration(vars: MutableMap<String, BigDecimal>, x: BDVector, error: BigDecimal, oldError: BDErrorQueue): BigDecimal? { return null }
}