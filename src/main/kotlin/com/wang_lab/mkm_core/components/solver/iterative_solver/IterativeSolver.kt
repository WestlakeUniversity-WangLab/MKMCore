package com.wang_lab.mkm_core.components.solver.iterative_solver
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import java.math.BigDecimal

abstract class IterativeSolver{
    abstract fun iterate(action: (BDVector, BigDecimal) -> Boolean)
}