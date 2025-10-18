@file:Suppress("NOTHING_TO_INLINE")
package com.wang_lab.mkm_core.algebra

import com.wang_lab.mkm_core.algebra.number_math.equalTo
import com.wang_lab.mkm_core.algebra.number_math.plus
import java.math.BigDecimal

fun String.join(list: Iterable<String>): String{
    val sb = StringBuilder()
    for(str in list){
        if(sb.isNotEmpty()) sb.append(this)
        sb.append(str)
    }
    return sb.toString()
}

inline fun <T, R> Collection<T>.mmap(transform: (T) -> R): MutableList<R> = mapTo(ArrayList(this.size), transform)
inline fun <T, R> Collection<T>.mmapIndexed(transform: (Int, T) -> R): MutableList<R> = mapIndexedTo(ArrayList(this.size), transform)

fun <E> MutableList<E>.addR(element: E):MutableList<E>  {
    this.add(element)
    return this
}
fun <E> MutableList<E>.addAllR(elements: Collection<E>):MutableList<E>  {
    this.addAll(elements)
    return this
}

fun <E> Collection<E>.nAddR(element: E):MutableList<E>  {
    val ml = this.toMutableList()
    ml.add(element)
    return ml
}
fun <E> Collection<E>.nAddAllR(elements: Collection<E>):MutableList<E>  {
    val ml = this.toMutableList()
    ml.addAll(elements)
    return ml
}
fun MutableMap<Map<String, Number>, Number>.merge(m: Map<String, Number>, n: Number){
    //this.forEach { (t, u) ->
    //    if(mapEqual(t, m)){
    //        this[t] = u + n
    //        return
    //    }
    //}
    this[m] = (this[m] ?: 0) + n
}
fun mapEqual(m1:Map<String, Number>, m2:Map<String, Number>): Boolean{
    if(m1.size != m2.size) return false
    m1.forEach{ (t1, u1) ->
        if(!m2.containsKey(t1) || m2[t1]!!.equalTo(u1)) return false
    }
    return true
}

fun IntArray.product(): Int {
    var product = 1
    for (element in this) product *= element
    return product
}
