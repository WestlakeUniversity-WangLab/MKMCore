package com.wang_lab.mkm_core.misc

class Item(private val singular: String, private val plural: String) {
    fun n(n: Int) = "$n ${if(n > 1) plural else singular}"
    override fun toString() = singular
    companion object{
        fun withS(name: String) = Item(name, name+"s")
        fun withES(name: String) = Item(name, name+"es")
        fun same(name: String) = Item(name, name)
    }
}