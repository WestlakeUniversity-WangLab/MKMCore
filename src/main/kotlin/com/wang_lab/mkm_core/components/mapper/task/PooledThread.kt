package com.wang_lab.mkm_core.components.mapper.task

import java.lang.Thread.sleep
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A class that can manage a thread in a thread pool. The thread will get tasks from a dynamic task queue.
 * Once the task queue is emptied and all the other threads are waiting,
 * then it will inform other threads to stop.
 *
 * You need to manually start its thread.
 *
 * You can create a multi-thread task with the following code.
 *
 *     val heap = ConcurrentLinkedQueue<T>()
 *     val threads = mutableListOf<PooledThread<T>>()
 *     threads.addAll(List(threadNumber){
 *         PooledThread(
 *             { t ->
 *                 //Todo with task information t.
 *             },
 *             threads,
 *             heap
 *         )
 *     }
 *     MultiThreadTask(
 *         threadTasks = threadTasks,
 *         threads = threads.map{ it.thread },
 *         finalAction = {
 *             //Todo after all threads stop.
 *         }
 *     ).start()
 * @param R The type of task information.
 * @param action What to do with the task information.
 * @param threadPool A list of threads. The range it checks running states from and informs to stop to.
 * You need to manually add this PooledThread to the list.
 * @param taskPool A ConcurrentLinkedQueue that your thread get task information from.
 *
 * @see MultiThreadTask
 */
class PooledThread<R>(
    action: (R) -> Unit,
    threadPool: List<PooledThread<*>>,
    taskPool: ConcurrentLinkedQueue<R>
){
    var waiting = false
    var stop = false
    val thread = Thread {
        while (!stop) {
            val task = taskPool.poll()
            if (task != null) {
                waiting = false
                action(task)
            }else{
                waiting = true
                if (threadPool.all { it.waiting }) {
                    threadPool.forEach { it.stop = true }
                    break
                }
                sleep(50)
            }
        }
    }
}