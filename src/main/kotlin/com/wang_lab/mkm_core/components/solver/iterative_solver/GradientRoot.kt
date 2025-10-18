package com.wang_lab.mkm_core.components.solver.iterative_solver

import com.wang_lab.mkm_core.*
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.algebra.big_decimal_math.div
import com.wang_lab.mkm_core.algebra.big_decimal_math.minus
import java.math.BigDecimal

val gStep = BigDecimal(0.38)

class GradientRoot(
    val f: (BDVector) -> BDVector,
    val norm: (BDVector, BDVector) -> BigDecimal,
    val c0: BDVector
): IterativeSolver(){
    override fun iterate(action: (BDVector, BigDecimal) -> Boolean){
        //var x0 = constraint(c0)
        var x0 = c0
        x0 = BDVector(c0.size)
        var fx = f(x0)
        while(true){
            var maxStep = mapZipped(x0, fx){ t, dt ->
                try{
                    if(dt.signum() > 0) BigDecimal.ONE.minus(t).div(dt)
                    else -t.div(dt)
                }catch (_: Exception){
                    BigDecimal.ONE
                }
            }.min()
            val totalCvg = x0.sum()
            val totalRate = fx.sum()
            val totalMinStep = if(totalRate.signum() > 0) BigDecimal.ONE.minus(totalCvg).div(totalRate)
            else -totalCvg.div(totalRate)
            maxStep = maxStep.min(totalMinStep)

            val s = fx * maxStep
            x0 = x0 + s * gStep
            fx = f(x0)
            if(action(x0, norm(x0, fx))) return
        }
    }
}
