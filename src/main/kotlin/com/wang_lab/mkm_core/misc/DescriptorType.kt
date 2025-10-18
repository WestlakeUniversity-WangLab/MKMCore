package com.wang_lab.mkm_core.misc

import com.wang_lab.mkm_core.species.SurfaceSpecies

interface DescriptorType {
    val descriptor: String
}
enum class ThermoDescriptor(val symbol: String, val unit: String): DescriptorType {
    Temperature("T", "K"){
        override val descriptor = "temperature"
    },
    Pressure("p", "bar"){
        override val descriptor = "pressure"
    },
    Voltage("U", "V"){
        override val descriptor = "voltage"
    }
}
class CustomDescriptor(val name: String): DescriptorType{
    override val descriptor = name
}
class ScalingSpeciesDescriptor(val species: SurfaceSpecies): DescriptorType{
    override val descriptor = species.name
}
