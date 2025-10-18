package com.wang_lab.mkm_core.components.solver.iterative_solver

import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import java.math.BigDecimal


val stepList = listOf(
    BigDecimal.ONE,
    BigDecimal.valueOf(0.5),
    BigDecimal.valueOf(0.25),
    BigDecimal.valueOf(0.125),
    BigDecimal.valueOf(0.0625),
    BigDecimal.valueOf(0.03125),
    BigDecimal.valueOf(0.015625),
    BigDecimal.valueOf(0.0078125),
    BigDecimal.valueOf(0.00390625),
    BigDecimal.valueOf(0.001953125),
    BigDecimal.valueOf(-1.0),
    BigDecimal.valueOf(-0.5),
    BigDecimal.valueOf(-0.25),
    BigDecimal.valueOf(-0.125),
    BigDecimal.valueOf(-0.0625),
    BigDecimal.valueOf(-0.03125),
    BigDecimal.valueOf(-0.015625),
    BigDecimal.valueOf(-0.0078125),
    BigDecimal.valueOf(-0.00390625),
    BigDecimal.valueOf(-0.001953125),
)
class NewtonRoot(
    val f: (BDVector) -> BDVector,
    val norm: (BDVector, BDVector) -> BigDecimal,
    val gradient: (BDVector, BDVector) -> BDVector,
    val constraint: (BDVector) -> BDVector,
    val c0: BDVector
): IterativeSolver(){
    override fun iterate(action: (BDVector, BigDecimal) -> Boolean){
        var x0 = constraint(c0)
        var fx = f(x0)
        var fxNorm = norm(x0, fx)
        if(action(x0, fxNorm)) return
        while(true){
            val s = gradient(x0, fx)
            //println(s)
            var success = false
            var minX = BDVector(0)
            var minNorm = BigDecimal(1e99)
            for(l in stepList){
                var x1 = x0 + s * l
                x1 = constraint(x1)
                fx = f(x1)
                val newNorm = norm(x1, fx)
                if(newNorm < minNorm){
                    minNorm = newNorm
                    minX = x1
                }
                if(newNorm <= fxNorm){
                    fxNorm = newNorm
                    x0 = x1
                    success = true
                    break
                }
            }
            if(!success){
                x0 = minX
                fxNorm = minNorm
            }
            if(action(x0, fxNorm)) return
        }
    }
}