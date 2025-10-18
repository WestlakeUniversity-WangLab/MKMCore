package com.wang_lab.mkm_core.algebra.big_algebra

import java.math.BigDecimal
import kotlin.math.min
import com.wang_lab.mkm_core.algebra.big_decimal_math.*

/**
 * A class copied from Jama.LUDecomposition to solve BDMatrix (Matrix with BigDecimal).
 */
class BDLUDecomposition(var lu: Array<Array<BigDecimal>>, var piv: IntArray, private val rows: Int, private val columns: Int, var pivSign: Int) {
    constructor(A: BDMatrix) : this(
        lu = A.getArrayCopy(),
        rows = A.rows,
        columns = A.columns,
        pivSign = 1,
        piv = IntArray(A.rows){ it }
    ) {
        var luRowI: Array<BigDecimal>
        // Outer loop.
        for (j in 0 until columns) {
            // Make a copy of the j-th column to localize references.
            val luColumnJ = Array(rows){ lu[it][j] }
            // Apply previous transformations.
            for (i in 0 until rows) {
                luRowI = lu[i]
                // Most of the time is spent in the following dot product.
                val s = (0 until min(i, j)).sumOf { luRowI[it] * luColumnJ[it] }
                luColumnJ[i] -= s
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
                    lu[i][j] /= lu[j][j]
        }
    }

    private fun isNotSingular(): Boolean {
        for (j in 0 until columns)
            if (lu[j][j].isZero()) return false
        return true
    }
    fun solve(b: BDVector): BDVector {
        require(b.size == rows) { "Matrix row dimensions must agree." }
        require(this.isNotSingular()) { "Matrix is singular." }
        // Copy right-hand side with pivoting
        val x = b.getVector(piv)
        // Solve L*Y = B(piv,:)
        for (k in 0 until columns)
            for (i in k + 1 until columns)
                x[i] -= x[k] * lu[i][k]
        // Solve U*X = Y;
        for (k in columns - 1 downTo 0) {
            if(lu[k][k].isZero()){
                if(x[k].isZero()) x[k] = BigDecimal.ZERO
                else throw Exception("Unsolvable.")
            }
            x[k] /= lu[k][k]
            for (i in 0 until k) {
                x[i] -= x[k] * lu[i][k]
            }
        }
        return x
    }
    fun det(): BigDecimal {
        require(rows == columns) { "Matrix must be square." }
        var d = pivSign.nToBigDecimal()
        for (j in 0 until columns) {
            d *= lu[j][j]
        }
        return d
    }
    override fun toString(): String {
        return lu.joinToString("\n ", "[", "]") { r -> r.joinToString("\t", "[", "]") { "%6e".format(it) } }
    }
}