package com.wang_lab.mkm_core.algebra.expr_algebra

import com.wang_lab.mkm_core.algebra.expr.AlgebraExpr
import com.wang_lab.mkm_core.algebra.expr.ExprAdd.Companion.exprAdd
import com.wang_lab.mkm_core.algebra.expr.ExprConst.Companion.eZERO

/**
 * A class copied from Jama.Matrix using AlgebraExpr as its values.
 */
class ExprMatrix(var array: Array<Array<AlgebraExpr>>, val rows: Int, val columns: Int) : Cloneable, Iterable<Array<AlgebraExpr>> {
    constructor(m: Int, n: Int, transform: (Int, Int) -> AlgebraExpr): this(Array(m){ i -> Array(n){ j -> transform(i, j) } }, m, n)
    constructor(m: Int, n: Int): this(Array(m){ Array(n){ eZERO } }, m, n)
    constructor(data: Array<Array<AlgebraExpr>>): this(data, data.size, data[0].size)

    operator fun get(i: Int, j: Int) = array[i][j]
    operator fun set(i: Int, j: Int, value: AlgebraExpr) {
        array[i][j] = value.simplify()
    }
    operator fun get(i: Int) = array[i]
    operator fun unaryMinus() = ExprMatrix(rows, columns){ i, j -> -array[i][j] }
    operator fun times(b: Number) = ExprMatrix(rows, columns){ i, j -> array[i][j] * b }
    //operator fun times(b: ExprMatrix) =
    //    if(this.columns != b.rows) throw Exception()
    //    else ExprMatrix(this.rows, b.columns){ i, j -> this[i].indexedSumOf { k, d -> d.times(b[k, j]) } }

    operator fun times(b: ExprMatrix): ExprMatrix{
        require(this.columns == b.rows) { "The column number of the first matrix must equal to the row number of the second matrix." }
        return ExprMatrix(this.rows, b.columns){ i, j -> exprAdd((0 until this.columns).map { k -> this[i, k] * b [k, j] }.toMutableList()) }
    }
    operator fun plus(b: ExprMatrix) = ExprMatrix(rows, columns){ i, j -> array[i][j].plus(b[i][j]) }
    operator fun minus(b: ExprMatrix) = ExprMatrix(rows, columns){ i, j -> array[i][j].minus(b[i][j]) }
    override fun toString(): String {
        return joinToString("\n") { array -> array.joinToString("\t") { it.toString() } }
    }
    override fun equals(other: Any?): Boolean {
        if(other is ExprMatrix){
            if(rows != other.rows) return false
            if(columns != other.columns) return false
            array.forEachIndexed{ i, r ->
                r.forEachIndexed { j, c ->
                    if(c != other[i][j]) return false
                }
            }
            return true
        }
        return false
    }
    private val lu: ExprLUDecomposition by lazy { ExprLUDecomposition(this) }
    fun getArrayCopy() = Array(rows){ i -> Array(columns){ j -> array[i][j] } }
    override fun clone() = ExprMatrix(getArrayCopy(), rows, columns)
    fun det(): AlgebraExpr{
        require(rows == columns){ "Matrix must be square." }
        if(rows == 1) return array[0][0]
        val s: MutableList<AlgebraExpr> = (0 until rows).map{ i ->
            (if(i % 2 == 1) array[0][i] else -array[0][i]) * cofactor(0, i).det()
        }.toMutableList()
        return exprAdd(s).simplify()
    }
    fun getMatrix(r: IntArray, j0: Int, j1: Int) = ExprMatrix(r.size, j1 - j0 + 1){ i, j -> array[r[i]][j0 + j] }
    fun cofactor(a: Int, b : Int) = ExprMatrix(rows-1, columns-1){ i, j -> array[if(i < a) i else i+1][if(j < b) j else j+1] }
    override fun hashCode(): Int {
        var result = array.contentDeepHashCode()
        result = 31 * result + rows
        result = 31 * result + columns
        return result
    }
    inner class ExprMatrixIterator: Iterator<Array<AlgebraExpr>>{
        var index = 0
        override fun hasNext(): Boolean = index < rows
        override fun next(): Array<AlgebraExpr> {
            index += 1
            return array[index-1]
        }
    }
    fun solve(B: List<AlgebraExpr>): List<AlgebraExpr> {
        require(rows == columns){ "Matrix must be square." }
        return lu.solve(B)
    }
    override fun iterator(): Iterator<Array<AlgebraExpr>> = ExprMatrixIterator()

    fun forEach(action: (AlgebraExpr) -> Unit){
        array.forEach { row -> row.forEach { action(it) } }
    }
    fun toList() = List(rows * columns){ this[it/columns][it%columns] }
    fun join(b: ExprMatrix): ExprMatrix{
        if(b.columns != columns) throw Exception("Matrix to join must have same column numbers!")
        return ExprMatrix(
            Array(rows + b.rows){
                if(it < rows) array[it].clone() else b.array[it - rows].clone()
            },
            rows + b.rows,
            columns
        )
    }
}
