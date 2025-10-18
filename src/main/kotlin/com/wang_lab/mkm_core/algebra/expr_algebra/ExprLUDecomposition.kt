package com.wang_lab.mkm_core.algebra.expr_algebra

import com.wang_lab.mkm_core.algebra.expr.AlgebraExpr
import com.wang_lab.mkm_core.algebra.expr.ExprConst
import com.wang_lab.mkm_core.algebra.expr.ExprConst.Companion.eZERO
import com.wang_lab.mkm_core.algebra.number_math.compareTo
import kotlin.math.min

class ExprLUDecomposition(var lu: Array<Array<AlgebraExpr>>, var piv: IntArray, private val rows: Int, private val columns: Int, var pivSign: Int) {
    constructor(A: ExprMatrix) : this(
        lu = A.getArrayCopy(),
        rows = A.rows,
        columns = A.columns,
        pivSign = 1,
        piv = IntArray(A.rows){ it }
    ) {
        var luRowI: Array<AlgebraExpr>
        // Outer loop.
        for (j in 0 until columns) {
            // Make a copy of the j-th column to localize references.
            val luColumnJ = Array(rows){ lu[it][j] }
            // Apply previous transformations.
            for (i in 0 until rows) {
                luRowI = lu[i]
                // Most of the time is spent in the following dot product.
                val s = (0 until min(i, j)).sumOf { luRowI[it] * luColumnJ[it] }.simplify()
                luColumnJ[i] = (luColumnJ[i] - s).simplify()
                luRowI[j] = luColumnJ[i]
            }
            // Find pivot and exchange if necessary.
            var p: Int = j
            for (i in j + 1 until rows) {
                if (luColumnJ[i].abs() > luColumnJ[p].abs()) {
                    p = i
                }
            }
            if (p != j) {
                for (k in 0 until columns) {
                    val t = lu[p][k]
                    lu[p][k] = lu[j][k]
                    lu[j][k] = t
                }
                val k = piv[p]
                piv[p] = piv[j]
                piv[j] = k
                pivSign = -pivSign
            }
            // Compute multipliers.
            if ((j < rows) and !lu[j][j].isZero())
                for (i in j + 1 until rows)
                    lu[i][j] = (lu[i][j] / lu[j][j]).simplify()
        }
    }

    private fun isNotSingular(): Boolean {
        for (j in 0 until columns)
            if (lu[j][j].isZero())
                return false
        return true
    }
    fun softSolve(b: List<AlgebraExpr>): List<AlgebraExpr> {
        require(b.size == rows) { "Matrix row dimensions must agree." }
        // Copy right-hand side with pivoting
        val x: MutableList<AlgebraExpr> = b.filterIndexed{ i, _ -> i in piv }.toMutableList()
        // Solve L*Y = B(piv,:)
        for (k in 0 until columns)
            for (i in k + 1 until columns)
                x[i] -= x[k] * lu[i][k]
        // Solve U*X = Y;
        for (k in columns - 1 downTo 0) {
            if(lu[k][k].isZero()){
                if(x[k].isZero()) x[k] = eZERO
                else throw Exception("Unsolvable.")
            }
            x[k] /= lu[k][k]
            for (i in 0 until k) {
                x[i] -= x[k] * lu[i][k]
            }
        }
        return x.map{ it.simplify() }
    }
    fun solve(b: List<AlgebraExpr>): List<AlgebraExpr> {
        require(b.size == rows) { "Matrix row dimensions must agree." }
        //require(this.isNotSingular()) { "Matrix is singular." }
        // Copy right-hand side with pivoting
        val x: MutableList<AlgebraExpr> = b.filterIndexed{ i, _ -> i in piv }.toMutableList()
        // Solve L*Y = B(piv,:)
        for (k in 0 until columns)
            for (i in k + 1 until columns)
                x[i] = (x[i] - x[k] * lu[i][k]).simplify()
        // Solve U*X = Y;
        for (k in columns - 1 downTo 0) {
            if(lu[k][k].isZero()){
                if(x[k].isZero()) x[k] = eZERO
                else throw Exception("Unsolvable.")
            }
            x[k] = (x[k] / lu[k][k]).simplify()
            for (i in 0 until k) {
                x[i] = (x[i] - x[k] * lu[i][k]).simplify()
            }
        }
        x.indices.forEach { x[it] = x[it].simplify() }
        return x
    }
    fun det(): AlgebraExpr {
        require(rows == columns) { "Matrix must be square." }
        var d: AlgebraExpr = ExprConst(pivSign)
        for (j in 0 until columns) {
            d *= lu[j][j]
        }
        return d
    }
}

private fun <T> Iterable<T>.sumOf(function: (T) -> AlgebraExpr): AlgebraExpr {
    var e: AlgebraExpr? = null
    forEach{ e = if(e == null) function(it) else e!! + function(it) }
    return e ?: eZERO
}
