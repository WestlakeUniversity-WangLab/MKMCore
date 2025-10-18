package com.wang_lab.mkm_core.misc

class Constraint(
    var value: Double? = null,
    var min: Double? = null,
    var max: Double? = null
) {
    init{
        if(min != null && max != null && min!! > max!!) throw Exception("Min($min) is larger than max($max)!")
        if(min != null && max != null && min!! == max!!) value = min
    }
    fun clip(d: Double): Double{
        if(value != null) return value!!
        if(min != null && d < min!!) return min!!
        if(max != null && d > max!!) return max!!
        return d
    }
    val isConst = value != null
    companion object{
        fun stringToConstraint(s: String?) =
            when(s){
                null -> Constraint()
                "+" -> Constraint(min = 0.0)
                "-" -> Constraint(max = 0.0)
                else ->{
                    if(':' in s){
                        val pars = s.split(':')
                        if(pars.size == 2) Constraint(min = pars[0].toDouble(), max = pars[1].toDouble())
                        else if(pars.size == 1)
                            if(s.startsWith(":")) Constraint(max = pars[0].toDouble())
                            else Constraint(min = pars[0].toDouble())
                        else throw Exception("$s is not a valid format of constraint.")
                    }else{
                        Constraint(value = s.toDouble())
                    }
                }
            }
    }
}