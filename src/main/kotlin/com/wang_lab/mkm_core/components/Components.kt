package com.wang_lab.mkm_core.components

class Components<T>(val base: Class<T>){
    private val classes = ArrayList<Pair<String, Class<T>>>()
    val list: MutableMap<String, Class<T>>
        get(){
            val map = mutableMapOf<String, Class<T>>()
            val duplicate = mutableListOf<String>()
            classes.forEach { (key, clazz) ->
                when (key) {
                    in map -> {
                        duplicate.add(key)
                        val t = map[key]!!
                        map.remove(key)
                        map[t.name] = t
                        map[clazz.name] = clazz
                    }
                    in duplicate -> map[clazz.name] = clazz
                    else -> map[key] = clazz
                }
            }
            return map
        }
    fun add(element: Class<*>){
        if(!base.isAssignableFrom(element)) return
        @Suppress("UNCHECKED_CAST")
        if(classes.all{ it.second != element }) classes.add(Pair(element.name.substringAfterLast('.'), element as Class<T>))
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Components<${base.name}>:\n${classes.joinToString("\n") { "- ${it.second.name}" }}")
        return sb.toString()
    }
}