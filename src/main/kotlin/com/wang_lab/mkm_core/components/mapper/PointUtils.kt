package com.wang_lab.mkm_core.components.mapper

import Jama.Matrix
import com.wang_lab.mkm_core.indexedSumOf
import com.wang_lab.mkm_core.point.MapPoint

operator fun IntArray.plus(b: IntArray): IntArray {
    if(size != b.size) throw Exception()
    return IntArray(size){ get(it) + b[it] }
}
operator fun DoubleArray.plus(b: DoubleArray): DoubleArray {
    if(size != b.size) throw Exception()
    return DoubleArray(size){ get(it) + b[it] }
}
operator fun DoubleArray.div(b: Double): DoubleArray{
    return DoubleArray(size){ get(it) / b }
}
fun DoubleArray.scale(b: DoubleArray): Double{
    if(size == b.size + 1) return b.indexedSumOf{ i, it -> get(i) * it } + get(size - 1)
    if(size == b.size - 1) return this.indexedSumOf{ i, it -> b[i] * it } + b[size]
    throw Exception("Cannot scale! The length of two double array is $size and ${b.size}.")
}
fun DoubleArray.scale(b: MapPoint): Double{
    if(size == b.size + 1) return b.indexedSumOf{ i, it -> get(i) * it } + get(size - 1)
    if(size == b.size - 1) return this.indexedSumOf{ i, it -> b[i] * it } + b[size]
    throw Exception("Cannot scale! The length of two double array is $size and ${b.size}.")
}
val DoubleArray.vector: Matrix
    get() = Matrix(DoubleArray(size + 1){ if(it == size) 1.0 else get(it) }, size + 1)

fun IntArray.content() = joinToString(", ", "(", ")") { it.toString() }
fun DoubleArray.content() = joinToString(", ", "(", ")") { "%.3f".format(it) }