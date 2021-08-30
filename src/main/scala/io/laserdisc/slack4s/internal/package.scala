package io.laserdisc.slack4s

import io.laserdisc.slack4s.slashcmd.URL

import java.util.concurrent.Executors.newCachedThreadPool
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.fromExecutorService

package object internal {

  val ProjectRepo: URL = URL.unsafeFrom("https://github.com/laserdisc-io/slack4s")

  def mkCachedThreadPool(prefix: String): ExecutionContext =
    fromExecutorService(
      newCachedThreadPool(
        new ThreadFactory {
          private val count = new AtomicInteger()
          override def newThread(r: Runnable): Thread = {
            val t = new Thread(r)
            t.setName(s"$prefix-${count.incrementAndGet}")
            t.setDaemon(true)
            t
          }
        }
      )
    )

}
