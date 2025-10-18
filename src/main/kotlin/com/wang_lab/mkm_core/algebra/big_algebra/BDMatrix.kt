package com.wang_lab.mkm_core.algebra.big_algebra

import com.wang_lab.mkm_core.algebra.big_decimal_math.*
import com.wang_lab.mkm_core.mapBDV
import java.math.BigDecimal

fun Iterable<Iterable<Number>>.toBDMatrix(): BDMatrix {
    val array = this.map { row ->
        row.map{
            it.nToBigDecimal()
        }.toTypedArray()
    }.toTypedArray()
    return BDMatrix(array)
}

/**
 * A class copied from Jama.Matrix using BigDecimal as its values.
 */
class BDMatrix(var array: Array<Array<BigDecimal>>, val rows: Int, val columns: Int) : Cloneable, Iterable<Array<BigDecimal>> {
    constructor(m: Int, n: Int, transform: (Int, Int) -> BigDecimal): this(Array(m){ i -> Array(n){ j -> transform(i, j) } }, m, n)
    constructor(m: Int, n: Int): this(Array(m){ Array(n){ BigDecimal.ZERO } }, m, n)
    constructor(v: BDVector): this(Array(v.size){ i -> arrayOf(v[i]) }, v.size, 1)
    constructor(data: Array<Array<BigDecimal>>): this(data, data.size, data[0].size)
    private val lu: BDLUDecomposition by lazy{ BDLUDecomposition(this) }

    operator fun get(i: Int, j: Int) = array[i][j]
    operator fun set(i: Int, j: Int, value: BigDecimal) {
        array[i][j] = value
    }
    operator fun get(i: Int) = array[i]
    operator fun unaryMinus() = BDMatrix(rows, columns){ i, j -> -array[i][j] }
    operator fun times(b: Number) = BDMatrix(rows, columns){ i, j -> array[i][j] * b.nToBigDecimal() }
    //operator fun times(b: BDMatrix) =
    //    if(this.columns != b.rows) throw Exception()
    //    else BDMatrix(this.rows, b.columns){ i, j -> this[i].indexedSumOf { k, d -> d.times(b[k, j]) } }

    operator fun times(b: BDMatrix): BDMatrix{
        require(this.columns == b.rows) { "The column number of the first matrix must equal to the row number of the second matrix." }
        return BDMatrix(this.rows, b.columns){ i, j -> (0 until this.columns).sumOf { k -> this[i, k] * b [k, j] } }
    }
    operator fun plus(b: BDMatrix) = BDMatrix(rows, columns){ i, j -> array[i][j].plus(b[i][j]) }
    operator fun minus(b: BDMatrix) = BDMatrix(rows, columns){ i, j -> array[i][j].minus(b[i][j]) }

    override fun toString(): String {
        return array.joinToString("\n ", "[", "]") { r -> r.joinToString("\t", "[", "]") { "%6e".format(it) } }
    }
    override fun equals(other: Any?): Boolean {
        if(other is BDMatrix){
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
    fun getArrayCopy() = Array(rows){ i -> Array(columns){ j -> array[i][j] } }
    override fun clone() = BDMatrix(getArrayCopy(), rows, columns)
    fun det() = lu.det()

    fun solve(B: BDVector): BDVector {
        require(rows == columns){ "Matrix must be square." }
        return lu.solve(B)
    }
    fun eliminationSolve(B: BDVector): BDVector{
        if(rows != B.size) throw Exception("The length of B does not equal to the row number.")
        val equations = MutableList(rows){ Pair(BDVector(array[it]), B[it]) }
        val base = mutableListOf<Pair<BDVector, BigDecimal>>()
        val validEquations = mutableListOf<Pair<BDVector, BigDecimal>>()
        fun orthogonalization(a: Pair<BDVector, BigDecimal>, b: Pair<BDVector, BigDecimal>): Pair<BDVector, BigDecimal>{
            val (aa, ab) = a
            val (ba, bb) = b
            val pba = ba * aa
            val pbb = ba * ba
            val na = aa - ba * (pba / pbb)
            val nb = ab - bb * pba / pbb
            if(na.isZero() && !nb.isZero()) throw Exception("No solution!")
            return Pair(na, nb)
        }
        fun addToBase(vector: Pair<BDVector, BigDecimal>): Boolean{
            var v = vector
            base.forEach{ v = orthogonalization(v, it) }
            if(!v.first.isZero()) {
                base.add(v)
                return true
            }
            return false
        }
        equations.forEach{ e ->
            if(addToBase(e)) validEquations.add(e)
        }
        if(validEquations.size < columns) throw Exception("Can't solve because there is not enough valid equations.")
        if(validEquations.size > columns) throw Exception("No solution!")
        val na = validEquations.map{ it.first }.toBDMatrix()
        val nb = validEquations.mapBDV{ it.second }
        return na.solve(nb)
    }
    fun getMatrix(r: IntArray, j0: Int, j1: Int) = BDMatrix(r.size, j1 - j0 + 1){ i, j -> array[r[i]][j0 + j] }

    override fun hashCode(): Int {
        var result = array.contentDeepHashCode()
        result = 31 * result + rows
        result = 31 * result + columns
        return result
    }
    inner class BDMatrixIterator: Iterator<Array<BigDecimal>>{
        var index = 0
        override fun hasNext(): Boolean = index < rows
        override fun next(): Array<BigDecimal> {
            index += 1
            return array[index-1]
        }
    }

    override fun iterator(): Iterator<Array<BigDecimal>> = BDMatrixIterator()

    fun forEach(action: (BigDecimal) -> Unit){
        array.forEach { row -> row.forEach { action(it) } }
    }
    fun toList() = List(rows * columns){ this[it/columns][it%columns] }
    fun join(b: BDMatrix): BDMatrix{
        if(b.columns != columns) throw Exception("Matrix to join must have same column numbers!")
        return BDMatrix(
            Array(rows + b.rows){
                if(it < rows) array[it].clone() else b.array[it - rows].clone()
            },
            rows + b.rows,
            columns
        )
    }
}
