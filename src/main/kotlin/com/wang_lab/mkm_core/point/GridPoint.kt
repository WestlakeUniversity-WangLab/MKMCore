package com.wang_lab.mkm_core.point

import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * A class to express a grid point on map, with any dimension.
 *
 * It is actually an encapsulated IntArray that can be used as the key in HashMap.
 *
 * To get a value with a GridPoint key from a HashMap is just 10%~20% slower than directly get by index from a List.
 *
 * DO NOT change content or hash after being initialized.
 */
class GridPoint(val size: Int, init: (Int) -> Int): Iterable<Int>, Comparable<GridPoint>{
    private val content = IntArray(size, init)
    private var hash = getHashValue()
    constructor(vararg ints: Int): this(ints.size, { ints[it] })
    constructor(dis: DataInputStream): this(dis.readInt(), { dis.readInt() })
    operator fun get(i: Int) = content[i]
    override fun equals(other: Any?): Boolean {
        if(other !is GridPoint) return false
        if(hash != other.hash) return false
        return content.contentEquals(other.content)
    }
    operator fun plus(b: GridPoint): GridPoint{
        if(size != b.size) throw Exception("Different size!")
        return GridPoint(size){ this[it] + b[it] }
    }
    operator fun minus(b: GridPoint): GridPoint{
        if(size != b.size) throw Exception("Different size!")
        return GridPoint(size){ this[it] - b[it] }
    }
    operator fun times(b: GridPoint): GridPoint{
        if(size != b.size) throw Exception("Different size!")
        return GridPoint(size){ this[it] * b[it] }
    }
    private fun getHashValue(): Int{
        var h = 0
        content.forEach {
            h *= 31
            h += it
        }
        return h
    }
    override fun toString(): String = '[' + content.joinToString(", ") + ']'
    override fun hashCode() = hash
    override fun iterator(): Iterator<Int> = content.iterator()
    override fun compareTo(other: GridPoint): Int {
        if(size != other.size) return size.compareTo(other.size)
        for(i in 0 until size) if(this[i] != other[i]) return this[i].compareTo(other[i])
        return 0
    }
    fun write(dos: DataOutputStream){
        dos.writeInt(content.size)
        content.forEach { i -> dos.writeInt(i) }
    }
}