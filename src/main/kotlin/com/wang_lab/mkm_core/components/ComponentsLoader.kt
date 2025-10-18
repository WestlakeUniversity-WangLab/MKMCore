package com.wang_lab.mkm_core.components

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel
import com.wang_lab.mkm_core.switchJsonElement
import java.io.File
import java.net.URLClassLoader

public abstract class ComponentsLoader {
    abstract fun registerModule()
    fun <T> registerClass(clazz: Class<T>){
        components.add(Components(clazz))
    }
    fun <T> registerComponent(clazz: Class<T>){
        components.forEach{ it.add(clazz) }
    }
    @Suppress("UNCHECKED_CAST")
    companion object{
        private val components = object : ArrayList<Components<*>>(){
            operator fun <T> get(key: Class<T>): Components<T>?{
                return firstOrNull { it.base == key } as Components<T>?
            }
        }
        fun initializeComponents(){
            BuiltInComponentsLoader.registerModule()
            val addonsFolder = File("addons")
            if (!addonsFolder.exists() || !addonsFolder.isDirectory) {
                println("No addons directory found.")
                return
            }
            val jarFiles = addonsFolder.listFiles { file -> file.extension == "jar" } ?: return
            jarFiles.forEach { jarFile ->
                val jarUrl = jarFile.toURI().toURL()
                val classLoader = URLClassLoader(arrayOf(jarUrl), this::class.java.classLoader)
                val propertiesFile = classLoader.getResourceAsStream("loader.properties")
                val properties = java.util.Properties()
                properties.load(propertiesFile)
                val loaderClassName = properties.getProperty("loader.class")
                val loaderClass = classLoader.loadClass(loaderClassName)
                loaderClass?.let {
                    val loaderInstance = it.getDeclaredConstructor().newInstance() as ComponentsLoader
                    loaderInstance.registerModule()
                }
            }
        }
        fun getClass(name: String): Class<*>? {
            return components.firstOrNull{ it.base.name == name }?.base
        }
        fun <T> getComponentList(type: Class<T>): MutableMap<String, Class<T>>
                = (components.firstOrNull { it.base == type } as? Components<T>)?.list
            ?: throw Exception("No ${type.name} found in components. Usable: ${components.joinToString(", ") { it.base.name }}")
        fun <T> Map<String, Class<T>>.getComponent(className: String): Class<T>?
            = this[className] ?: values.firstOrNull{ it.name == className }
        inline fun <reified T> getComponentInstance(type: Class<T>, par: JsonObject, model: ReactionModel): T{
            val name = switchJsonElement(
                par["class"],
                "Class of ${type.name}",
                s = { it }
            )
            return getComponentList(type).getComponent(name)?.let { getInstance(it, model, par) }
                ?: throw Exception("${type.name} has not been registered!")
        }
        inline fun <reified T> getNormalInstance(type: Class<T>, name: String, par: JsonObject? = null): T?
                = getComponentList(type).getComponent(name)?.let { getInstance(it, par) }

        inline fun <reified T> getInstance(clazz: Class<T>, model: ReactionModel, par: JsonObject?): T{
            val error = mutableListOf<Throwable>()
            clazz.constructors.forEach {
                try{
                    val instance =it.newInstance(model, par)
                    return instance as T
                }catch (_: IllegalArgumentException){
                }catch (e: Throwable){
                    error.add(e)
                }
                try{
                    val instance =it.newInstance(model)
                    return instance as T
                }catch (_: IllegalArgumentException){
                }catch (e: Throwable){
                    error.add(e)
                }
            }
            if(error.isEmpty()){
                throw Exception("No constructor of $clazz matches input parameters!")
            }else{
                val e = Exception("$clazz cannot be initialized with the model!")
                error.forEach{
                    it.printStackTrace()
                }
                e.initCause(error.first())
                throw e
            }
        }

        inline fun <reified T> getInstance(clazz: Class<T>, par: JsonObject?): T{
            val error = mutableListOf<Throwable>()
            clazz.constructors.forEach {
                try{
                    val instance =it.newInstance(par)
                    return instance as T
                }catch (_: IllegalArgumentException){
                    return@forEach
                }catch (e: Throwable){
                    error.add(e)
                }
                try{
                    val instance =it.newInstance()
                    return instance as T
                }catch (_: IllegalArgumentException){
                    return@forEach
                }catch (e: Throwable){
                    error.add(e)
                }
            }
            if(error.isEmpty()){
                throw Exception("No constructor of $clazz matches input parameters!")
            }else{
                error.forEach{ it.printStackTrace() }
                throw Exception("$clazz cannot be initialized with the model!")
            }
        }
    }
}