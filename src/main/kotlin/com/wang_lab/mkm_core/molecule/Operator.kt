package com.wang_lab.mkm_core.molecule

import Jama.Matrix
import com.wang_lab.mkm_core.molecule.Operator.OperatorType.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Operator(val type: OperatorType, orientation: Vector3d, val angle: Double = 0.0) {
    val o = orientation.normal()
    val op: Matrix

    init{
        op = when(type){
            Rotation -> {
                if(angle == 0.0) throw Exception("Rotation angle must not be zero in a rotation operator!")
                val a = 1.0 - cos(angle)
                val c = cos(angle)
                val s = sin(angle)
                Matrix(arrayOf(
                    doubleArrayOf(a*o.x*o.x+c, a*o.x*o.y-s*o.z, a*o.x*o.z+s*o.y),
                    doubleArrayOf(a*o.y*o.x+s*o.z, a*o.y*o.y+c, a*o.y*o.z-s*o.x),
                    doubleArrayOf(a*o.z*o.x-s*o.y, a*o.z*o.y+s*o.x, a*o.z*o.z+c)
                ))
            }
            Mirror -> Matrix(arrayOf(
                doubleArrayOf(1-2*o.x*o.x, -2*o.x*o.y, -2*o.x*o.z),
                doubleArrayOf(-2*o.y*o.x, 1-2*o.y*o.y, -2*o.y*o.z),
                doubleArrayOf(-2*o.z*o.x, -2*o.z*o.y, 1-2*o.z*o.z)
            ))
            Reflection -> Matrix(arrayOf(
                doubleArrayOf(-1.0, 0.0, 0.0),
                doubleArrayOf(0.0, -1.0, 0.0),
                doubleArrayOf(0.0, 0.0, -1.0)
            ))
            Inversion -> rotation(o, angle).op * reflection.op
            Unknown -> throw Exception("Unknown operator!")
        }
    }

    fun operate(v: Vector3d): Vector3d{
        val c = v.toMatrixColumn()
        val m = op * c
        return Vector3d(m[0,0], m[1,0], m[2,0])
    }
    fun operateMolecule(m: Molecule): Molecule{
        return Molecule(
            m.name,
            m.coordinates.map{ c -> Pair(c.first, operate(c.second)) },
            m.symbols,
            m.pointGroup,
            m.geometry,
            m.symmetry,
            m.spin
        )
    }

    companion object{
        val reflection = Operator(Reflection, Vector3d(0.0,0.0,0.0))
        fun rotation(orientation: Vector3d, angle: Double) = Operator(Rotation, orientation, angle)
        fun rotation(orientation: Vector3d, fold: Int) = rotation(orientation, 2 * PI / fold)
        fun mirror(orientation: Vector3d) = Operator(Mirror, orientation)
        fun inversion(orientation: Vector3d, angle: Double) = Operator(Inversion, orientation, angle)
        fun inversion(orientation: Vector3d, fold: Int) = inversion(orientation, 2 * PI / fold)
    }

    enum class OperatorType{
        Rotation, Mirror, Reflection, Inversion, Unknown
    }
}