package com.wang_lab.mkm_core.components.solver.algorithm

import com.wang_lab.mkm_core.algebra.big_decimal_math.div
import com.wang_lab.mkm_core.algebra.big_decimal_math.plus
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.algebra.big_decimal_math.powerBD
import com.wang_lab.mkm_core.forEachZipped
import com.wang_lab.mkm_core.mapZipped
import com.wang_lab.mkm_core.sum
import java.lang.Integer.max
import java.math.BigDecimal

enum class NormalFunctions(val function: (BDVector, BDVector) -> BigDecimal){
    AbsoluteMaximum({ _, v -> v.maxOf { a -> a.abs() } }),
    AbsoluteAverage({ _, v -> v.sumOf { a -> a.abs() }.div(v.size.toDouble()) }),
    AbsoluteDegreeMaximum({ c, v ->
        val a = mapZipped(c, v){ c0, dt -> dt.div(c0).abs() }
        a.max()
    }),
    DegreeRootMeanSquare({ c, v ->
        var s = BigDecimal.ZERO
        forEachZipped(c, v){ c0, dt ->
            s = s.plus((dt.div(c0)).pow(2))
        }
        val b = s.powerBD(BigDecimal.valueOf(0.5))
        b
    }),
    AbsoluteAverageAndMax({ _, v -> v.maxOf { it.abs() } / BigDecimal(2) + v.sumOf { it.abs() } / BigDecimal(v.size * 2) }),
    AbsoluteMaxPercent10({ _, v ->
        v.map{ it.abs() }.sorted().reversed().subList(0, max(v.size / 10, 1)).sum()
    })
}
// Normal function. The two parameters are coverage and dtheta/dt.
/*
val AbsoluteMaximum: (BDVector, BDVector) -> BigDecimal = { _, v -> v.maxOf { it.abs() } }
val AbsoluteSum: (BDVector, BDVector) -> BigDecimal = { _, v -> v.sumOf { it.abs() } }
val AbsoluteDegreeMaximum: (BDVector, BDVector) -> BigDecimal = { c, v ->
    val a = mapZipped(c, v){ c0, dt -> dt.div(c0).abs() }
    a.max()
}
val DegreeRootMeanSquare: (BDVector, BDVector) -> BigDecimal = { c, v ->
    var s = BigDecimal.ZERO
    forEachZipped(c, v){ c0, dt ->
        s = s.plus((dt.div(c0)).pow(2))
    }
    val b = s.powerBD(BigDecimal.valueOf(0.5))
    b
}
val AbsoluteAverageAndMax: (BDVector) -> BigDecimal = { v -> v.maxOf { it.abs() } / BigDecimal(2) + v.sumOf { it.abs() } / BigDecimal(v.size * 2) }
val AbsoluteMaxPercent10: (BDVector, BDVector) -> BigDecimal = { _, v ->
    v.map{ it.abs() }.sorted().reversed().subList(0, max(v.size / 10, 1)).sum()
}

 */