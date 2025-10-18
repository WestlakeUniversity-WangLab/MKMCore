package com.wang_lab.mkm_core.molecule

import Jama.Matrix
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.acos
import kotlin.math.sqrt

data class Vector3d(var x: Double, var y: Double, var z: Double) {
    constructor(transform: (Int) -> Double): this(transform(0), transform(1), transform(2))
    constructor(j: JsonArray): this({ i -> j[i].jsonPrimitive.double })
    operator fun get(i: Int) = when(i){
        0 -> x
        1 -> y
        2 -> z
        else -> throw IndexOutOfBoundsException()
    }
    operator fun unaryMinus() = Vector3d{ i -> -this[i] }
    operator fun plus(b: Vector3d) = Vector3d{ i -> this[i] + b[i] }
    operator fun minus(b: Vector3d) = Vector3d{ i -> this[i] - b[i] }
    operator fun times(b: Number) = Vector3d{ i -> this[i] * b.toDouble() }
    operator fun div(b: Number) = Vector3d{ i -> this[i] / b.toDouble() }

    fun toMatrixColumn(): Matrix = Matrix(arrayOf(doubleArrayOf(this[0]), doubleArrayOf(this[1]), doubleArrayOf(this[2])))
    fun toMatrixRow(): Matrix = Matrix(arrayOf(doubleArrayOf(this[0], this[1], this[2])))
    fun vectorProduct(b: Vector3d)
            = Vector3d(
        y * b.z - z * b.y,
        z * b.x - x * b.z,
        x * b.y - y * b.x
    )
    fun product(b: Vector3d) = x * b.x +  y * b.y + z * b.z
    fun squareLength() = x * x + y * y + z * z
    fun length() = sqrt(squareLength())
    fun normal() = this / this.length()

    override fun toString() = "(${"%.3f".format(x)}, ${"%.3f".format(y)}, ${"%.3f".format(z)})"

    fun operate(op: Operator) = op.operate(this)
    fun angleWith(b: Vector3d) = acos(this.product(b) / length() / b.length())
}

inline fun <T> Collection<T>.sumOfCoordinate(selector: (T) -> Vector3d): Vector3d {
    var sum = Vector3d(0.0, 0.0, 0.0)
    for (element in this) sum += selector(element)
    return sum
}