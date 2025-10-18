package com.wang_lab.mkm_core.point

/**
 * A class to express a map point on map, with any dimension.
 *
 * It is actually an encapsulated DoubleArray that can be used as the key in HashMap.
 *
 * DO NOT change content or hash after being initialized.
 */
class MapPoint(val size: Int, init: (Int) -> Double): Iterable<Double>, Comparable<MapPoint>{
    private val content = DoubleArray(size, init)
    private val hash = getHashValue()
    constructor(vararg ds: Double): this(ds.size, { ds[it] })
    val indices = content.indices
    operator fun get(i: Int) = content[i]
    override fun equals(other: Any?): Boolean {
        if(other !is MapPoint) return false
        if(hash != other.hash) return false
        return content.contentEquals(other.content)
    }
    private fun getHashValue(): Int{
        var h = 0
        content.forEach {
            h *= 31
            h += it.hashCode()
        }
        return h
    }
    override fun toString(): String = '(' + content.joinToString(", ") + ')'
    override fun hashCode() = hash
    override fun iterator(): Iterator<Double> = content.iterator()
    override fun compareTo(other: MapPoint): Int {
        if(size != other.size) return size.compareTo(other.size)
        for(i in 0 until size) if(this[i] != other[i]) return this[i].compareTo(other[i])
        return 0
    }
    fun midPoint(b: MapPoint): MapPoint{
        if(size != b.size) throw Exception("Middle point can only be created by two point with same number of dimensions.")
        return MapPoint(size) { (this[it] + b[it]) / 2.0 }
    }
}