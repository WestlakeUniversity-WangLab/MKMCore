package com.wang_lab.mkm_core

import Jama.Matrix
import com.wang_lab.mkm_core.algebra.big_algebra.BDVector
import com.wang_lab.mkm_core.misc.EnergyList
import com.wang_lab.mkm_core.misc.EnergyType
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.lang.reflect.Field
import java.math.BigDecimal
import java.math.BigInteger

fun DataOutputStream.writeIntArray(obj: IntArray){
    writeInt(obj.size)
    obj.forEach { i -> this.writeInt(i) }
}
fun DataOutputStream.writeDoubleArray(obj: DoubleArray){
    writeInt(obj.size)
    obj.forEach { i -> this.writeDouble(i) }
}
fun DataInputStream.readIntArray(): IntArray = IntArray(readInt()){ readInt() }
fun DataInputStream.readDoubleArray(): DoubleArray{
    return DoubleArray(readInt()){ readDouble() }
}

fun DataOutputStream.writeByteArray(obj: ByteArray){
    writeInt(obj.size)
    write(obj)
}
fun DataInputStream.readByteArray() = ByteArray(readInt()){ readByte() }

fun DataOutputStream.writeBigInteger(obj: BigInteger){
    writeByte(obj.signum())
    if(obj.signum() == 0) return
    val ba = obj.toByteArray()
    writeByteArray(ba)
}
fun DataInputStream.readBigInteger(): BigInteger{
    val sigNum = readByte().toInt()
    return if(sigNum == 0) BigInteger.ZERO
    else BigInteger(sigNum, readByteArray())
}

fun DataOutputStream.writeBigDecimal(obj: BigDecimal){
    writeBigInteger(obj.unscaledValue())
    writeInt(obj.scale())
}
fun DataInputStream.readBigDecimal() = BigDecimal(readBigInteger(), readInt())


fun DataOutputStream.writeMatrix(obj: Matrix){
    writeInt(obj.rowDimension)
    writeInt(obj.columnDimension)
    obj.array.forEach { doubles -> doubles.forEach { writeDouble(it) } }
}
fun DataInputStream.readMatrix(): Matrix{
    val m = Matrix(readInt(), readInt())
    for(i in 0 until m.rowDimension)
        for(j in 0 until m.columnDimension)
            m[i, j] = readDouble()
    return m
}

fun DataOutputStream.writeBDVector(obj: BDVector){
    writeInt(obj.size)
    obj.forEach{ writeBigDecimal(it) }
}
fun DataInputStream.readBDVector(): BDVector{
    return BDVector(readInt()){ readBigDecimal() }
}
