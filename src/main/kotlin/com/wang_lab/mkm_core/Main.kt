package com.wang_lab.mkm_core

import com.wang_lab.mkm_core.components.ComponentsLoader.Companion.initializeComponents
import java.io.File
import java.lang.Long.max
import kotlin.system.exitProcess

val shortPar = mapOf(
    'h' to "help",
    'v' to "version",
    'f' to "function",
    't' to "threads",
    's' to "save",
    'i' to "interval",
    'l' to "load",
    'z' to "zip",
    'x' to "exit-on-save",
    'n' to "no-run"
)
object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val parameters = mutableMapOf<String, String>()
        var file: String? = null
        var key: String? = null
        fun setKey(k: String){
            key = k
            parameters[k] = ""
        }
        args.forEach {
            if(it.startsWith("--")){
                setKey(it.substring(2))
            }else if(it.startsWith('-')){
                it.substring(1).forEach { c ->
                    val k = try{
                        shortPar[c]!!
                    }catch (_: Exception){
                        throw Exception("\"$c\" is not a short parameter. Use \"-h\" or \"--help\" to view help.")
                    }
                    setKey(k)
                }
            }else if(key != null){
                parameters[key!!] = it
            }else{
                file = it
            }
        }
        if("help" in parameters){
            println("-h\t--help\t\tShow help.")
            println("-v\t--version\t\tShow program version.")
            println("-f\t--function\tstr\tSpecify the name of map function.")
            println("-t\t--threads\tint\tSet the number of threads for solving.")
            println("-s\t--save\t\tPeriodically save the current results (recommended).")
            println("-i\t--interval\tint\tSet the interval for periodically saving in second. Default value is 300.")
            println("-l\t--load\t\tLoad data before solving.")
            println("-z\t--zip\t\tSave in zipped format (recommended).")
            println("-x\t--exit-on-save\t\tSave when interrupted by Ctrl+C (recommended).")
            println("-n\t--no-run\t\tDo not run (just load data and write csv).")
            return
        }
        if("version" in parameters){
            println(version)
            return
        }
        initializeComponents()
        val rm = ReactionModel(file2JsonObject(File(file!!)), file!!)
        if("zip" in parameters) rm.zip = true
        val function = parameters["function"] ?: "map_in_turn"
        if("load" in parameters) rm.loadData()
        if("no-run" !in parameters){
            var finished = false
            if("save" in parameters){
                val interval = max(parameters["interval"]?.toDoubleOrNull()?.toLong() ?: 300L, 5L)
                logger?.info("Automatically save every $interval seconds.")
                Thread {
                    save@while(true) {
                        var time = 0
                        while(time < interval){
                            Thread.sleep(1000)
                            if(finished) break@save
                            time ++
                        }
                        if(finished) break@save
                        rm.saveData()
                    }
                }.apply { isDaemon = true }.start()
            }
            val threads = parameters["threads"]?.toIntOrNull() ?: Runtime.getRuntime().availableProcessors()
            if("exit-on-save" in parameters)
                Runtime.getRuntime().addShutdownHook(Thread {
                    logger?.info("Auto saving...")
                    rm.saveData()
                    logger?.info("Auto saving complete!")
                })
            rm.mapper.map(function, threads)
            finished = true
            rm.saveData()
        }
        for(it in rm.writers){
            it.output()
            logger?.info("${it.javaClass.simpleName} output complete.")
        }
        exitProcess(0)
    }
}