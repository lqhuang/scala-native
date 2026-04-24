import java.util.concurrent._

import scala.scalanative.meta.LinktimeInfo._

object Test {

  def main(args: Array[String]): Unit = {
    println("Hello, World!")
    println(
      s"""
       Linktime Info collection:
         debugMode: ${debugMode}
         releaseMode: ${releaseMode}
         runtimeVersion: ${runtimeVersion}
         garbageCollector: ${garbageCollector}
         isWeakReferenceSupported: ${isWeakReferenceSupported}
         isMultithreadingEnabled: ${isMultithreadingEnabled}
         isContinuationsSupported: ${isContinuationsSupported}
       ForkJoinPool.commonPool() info:
         POOL_PARALLELISM: ${ForkJoinPool.commonPool().getParallelism()}
         POOL_ASYNC_MODE: ${ForkJoinPool.commonPool().getAsyncMode()}
         POOL_SIZE: ${ForkJoinPool.commonPool().getPoolSize()}
       """.stripIndent()
    )
    println("")

    val WARMUP_RUNS = 5
    val BENCHMARK_RUNS = 10
    val NPS: Long = 1000L * 1000 * 1000
    val ITEMS = 1 << 20

    println(s"-- Benchmark for SubmissionPublisherLoops2Test --")
    println("-- Warming up ...")
    for (j <- 0 until WARMUP_RUNS) {
      println(f"Run warmup step ${j + 1} ...  ")
      SubmissionPublisherLoops2Test(ITEMS).execute()
      Thread.sleep(1000L)
    }
    var times2: List[Double] = List()
    println("-- Running benchmark ...")
    for (j <- 0 until BENCHMARK_RUNS) {
      println(f"Run benchmark step ${j + 1} ...")
      val tic = System.nanoTime()
      SubmissionPublisherLoops2Test(ITEMS).execute()
      val toc = System.nanoTime()
      val timeSeconds = (toc - tic).toDouble / NPS
      println(
        f"  per step time: ${timeSeconds}%7.3f seconds"
      )
      times2 = times2.appended(timeSeconds)
      Thread.sleep(1000L)
    }
    println("-- Statistics:")
    println(f" Average time: ${average(times2)}%7.3f seconds")
    println(f" Std Dev time: ${stddev(times2)}%7.3f seconds")
    println(s"-- End of SubmissionPublisherLoops2Test session --")
    println("")

    println(s"-- Benchmark for SubmissionPublisherLoops3Test --")
    println("-- Warming up ...")
    for (j <- 0 until WARMUP_RUNS) {
      print(f"Run warmup step ${j + 1} ...  ")
      SubmissionPublisherLoops3Test(ITEMS).execute()
      Thread.sleep(1000L)
    }
    var times3: List[Double] = List()
    println("-- Running benchmark ...")
    for (j <- 0 until BENCHMARK_RUNS) {
      println(f"Run benchmark step ${j + 1} ...")
      val tic = System.nanoTime()
      SubmissionPublisherLoops3Test(ITEMS).execute()
      val toc = System.nanoTime()
      val timeSeconds = (toc - tic).toDouble / NPS
      println(
        f"  per step time: ${timeSeconds}%7.3f seconds"
      )
      times3 = times3.appended(timeSeconds)
      Thread.sleep(1000L)
    }
    println("-- Statistics:")
    println(f" Average time: ${average(times3)}%7.3f seconds")
    println(f" Std Dev time: ${stddev(times3)}%7.3f seconds")
    println(s"-- End of SubmissionPublisherLoops3Test session --")
    println("")

    // SubmissionPublisherLoops4Test
    println(s"-- Benchmark for SubmissionPublisherLoops4Test --")
    println("-- Warming up ...")
    for (j <- 0 until WARMUP_RUNS) {
      print(f"Run warmup step ${j + 1} ...  ")
      SubmissionPublisherLoops4Test(ITEMS).execute()
      Thread.sleep(1000L)
    }
    var times4: List[Double] = List()
    println("-- Running benchmark ...")
    for (j <- 0 until BENCHMARK_RUNS) {
      println(f"Run benchmark step ${j + 1} ...")
      val tic = System.nanoTime()
      SubmissionPublisherLoops4Test(ITEMS).execute()
      val toc = System.nanoTime()
      val timeSeconds = (toc - tic).toDouble / NPS
      println(f"  per step time: ${timeSeconds}%7.3f seconds")
      times4 = times4.appended(timeSeconds)
      Thread.sleep(1000L)
    }
    println("-- Statistics:")
    println(f" Average time: ${average(times4)}%7.3f seconds")
    println(f" Std Dev time: ${stddev(times4)}%7.3f seconds")
    println(s"-- End of SubmissionPublisherLoops4Test session --")
    println("")

  }

  def average(xs: List[Double]): Double =
    xs.sum / xs.size

  def stddev(xs: List[Double]): Double = {
    val mean = average(xs)
    math.sqrt(
      xs.map(x => math.pow(x - mean, 2)).sum / (xs.size - 1)
    )
  }

}

abstract class HasItemsWithExecMethod(items: Int) {
  def execute(): Unit
}

/** One publisher, many subscribers */
class SubmissionPublisherLoops1Test(items: Int)
    extends HasItemsWithExecMethod(items) {

  val ITEMS: Int = items
  /* Parameters for multiple threading (original JSR-166 setup) */
  val CONSUMERS = 64

  val CAP: Int = Flow.defaultBufferSize()
  val phaser = new Phaser(CONSUMERS + 1)

  final class Sub extends Flow.Subscriber[Boolean] {
    var sn: Flow.Subscription = null
    var count = 0

    override def onSubscribe(s: Flow.Subscription): Unit = {
      sn = s
      s.request(CAP)
    }

    override def onNext(t: Boolean): Unit = {
      if (({ count += 1; count } & (CAP - 1)) == (CAP >>> 1))
        sn.request(CAP)
    }

    override def onError(t: Throwable): Unit = {
      t.printStackTrace()
    }

    override def onComplete(): Unit = {
      if (count != ITEMS)
        System.err.println("Error: remaining " + (ITEMS - count))
      phaser.arrive()
    }
  }

  def execute(): Unit = {
    // System.out.println(
    //   "ITEMS: " + ITEMS + " CONSUMERS: " + CONSUMERS + " CAP: " + CAP
    // )
    val exec = ForkJoinPool.commonPool()
    oneRun(exec)
    if (exec ne ForkJoinPool.commonPool()) exec.shutdown()
  }

  def oneRun(exec: ExecutorService): Unit = {
    val pub = new SubmissionPublisher[Boolean](exec, CAP)
    for (i <- 0 until CONSUMERS) {
      pub.subscribe(new Sub())
    }
    for (i <- 0 until ITEMS) {
      pub.submit(true)
    }
    pub.close()
    phaser.arriveAndAwaitAdvance()
  }
}

/** One FJ publisher, many subscribers */
class SubmissionPublisherLoops2Test(items: Int)
    extends HasItemsWithExecMethod(items) {

  val ITEMS: Int = items
  /* Original JSR-166 parameters: */
  val CONSUMERS = 64

  val CAP: Int = Flow.defaultBufferSize()
  val phaser = new Phaser(CONSUMERS + 1)

  final class Sub extends Flow.Subscriber[Boolean] {
    var sn: Flow.Subscription = null
    var count = 0

    def onSubscribe(s: Flow.Subscription): Unit = {
      sn = s
      s.request(CAP)
    }

    def onNext(t: Boolean): Unit = {
      if (({ count += 1; count } & (CAP - 1)) == (CAP >>> 1))
        sn.request(CAP)
    }

    def onError(t: Throwable): Unit = {
      t.printStackTrace()
    }

    def onComplete(): Unit = {
      if (count != ITEMS)
        System.err.println("Error: remaining " + (ITEMS - count))
      phaser.arrive()
    }
  }

  final class Pub extends RecursiveAction {
    final val pub =
      new SubmissionPublisher[Boolean](ForkJoinPool.commonPool(), CAP)

    def compute(): Unit = {
      val p = pub
      for (i <- 0 until CONSUMERS) {
        p.subscribe(new Sub())
      }
      for (i <- 0 until ITEMS) {
        p.submit(true)
      }
      p.close()
    }
  }

  def execute(): Unit = {
    // System.out.println(
    //   "ITEMS: " + ITEMS + " CONSUMERS: " + CONSUMERS + " CAP: " + CAP
    // )
    oneRun()
  }

  def oneRun(): Unit = {
    new Pub().fork()
    phaser.arriveAndAwaitAdvance()
  }
}

/** Creates PRODUCERS publishers each with CONSUMERS subscribers, each sent
 *  ITEMS items, with CAP buffering; repeats REPS times
 */
class SubmissionPublisherLoops3Test(items: Int)
    extends HasItemsWithExecMethod(items) {

  val ITEMS: Int = items
  /* Original JSR-166 parameters: */
  val PRODUCERS = 32
  val CONSUMERS = 32

  val CAP: Int = Flow.defaultBufferSize()
  val phaser = new Phaser(PRODUCERS * CONSUMERS + 1)

  def execute(): Unit = {
    // System.out.println(
    //   "ITEMS: " + ITEMS + " PRODUCERS: " + PRODUCERS + " CONSUMERS: " + CONSUMERS +
    //     " CAP: " + CAP
    // )
    oneRun()
  }

  def oneRun(): Unit = {
    val nitems = ITEMS.toLong * PRODUCERS * CONSUMERS
    val startTime = System.nanoTime()
    for (i <- 0 until PRODUCERS) {
      new Pub().fork()
    }
    phaser.arriveAndAwaitAdvance()
    val elapsed = System.nanoTime() - startTime
    val secs = elapsed.toDouble / (1000L * 1000 * 1000)
    val ips = nitems / secs
    System.out.println(f"  items per sec: ${ips}%14.2f")
  }

  final class Sub extends Flow.Subscriber[Boolean] {
    var count = 0
    var subscription: Flow.Subscription = null

    def onSubscribe(s: Flow.Subscription): Unit = {
      subscription = s
      s.request(CAP)
    }

    def onNext(b: Boolean): Unit = {
      if (b && ({ count += 1; count } & ((CAP >>> 1) - 1)) == 0)
        subscription.request(CAP >>> 1)
    }

    def onComplete(): Unit = {
      if (count != ITEMS)
        System.err.println("Error: remaining " + (ITEMS - count))
      phaser.arrive()
    }

    def onError(t: Throwable): Unit = {
      t.printStackTrace()
    }
  }

  final class Pub extends RecursiveAction {
    final val pub =
      new SubmissionPublisher[Boolean](ForkJoinPool.commonPool(), CAP)

    def compute(): Unit = {
      val p = pub
      for (i <- 0 until CONSUMERS) {
        p.subscribe(new Sub())
      }
      for (i <- 0 until ITEMS) {
        p.submit(true)
      }
      p.close()
    }
  }
}

/** Creates PRODUCERS publishers each with PROCESSORS processors each with
 *  CONSUMERS subscribers, each sent ITEMS items, with max CAP buffering;
 *  repeats REPS times
 */
class SubmissionPublisherLoops4Test(items: Int)
    extends HasItemsWithExecMethod(items) {

  val ITEMS: Int = items
  /* Original JSR-166 parameters: */
  val PRODUCERS = 32
  val PROCESSORS = 32
  val CONSUMERS = 32

  val CAP: Int = Flow.defaultBufferSize()
  val SINKS: Int = PRODUCERS * PROCESSORS * CONSUMERS
  val phaser = new Phaser(SINKS + 1)

  def execute(): Unit = {
    // System.out.println(
    //   "ITEMS: " + ITEMS +
    //     " PRODUCERS: " + PRODUCERS +
    //     " PROCESSORS: " + PROCESSORS +
    //     " CONSUMERS: " + CONSUMERS +
    //     " CAP: " + CAP
    // )
    oneRun()
  }

  def oneRun(): Unit = {
    val total: Long = ITEMS.toLong * SINKS
    val startTime = System.nanoTime()
    for (i <- 0 until PRODUCERS) {
      new Pub().fork()
    }
    phaser.arriveAndAwaitAdvance()
    val elapsed = System.nanoTime() - startTime
    val secs = elapsed.toDouble / (1000L * 1000 * 1000)
    val ips = total / secs
    System.out.println(f"  items per sec: ${ips}%14.2f")
  }

  final class Sub extends Flow.Subscriber[Boolean] {
    var count = 0
    var subscription: Flow.Subscription = null

    def onSubscribe(s: Flow.Subscription): Unit = {
      subscription = s
      s.request(CAP)
    }

    def onNext(b: Boolean): Unit = {
      if (b && ({ count += 1; count } & ((CAP >>> 1) - 1)) == 0)
        subscription.request(CAP >>> 1)
    }

    def onComplete(): Unit = {
      if (count != ITEMS)
        System.err.println("Error: remaining " + (ITEMS - count))
      phaser.arrive()
    }

    def onError(t: Throwable): Unit = {
      t.printStackTrace()
    }
  }

  final class Proc(executor: Executor, maxBufferCapacity: Int)
      extends SubmissionPublisher[Boolean](executor, maxBufferCapacity)
      with Flow.Processor[Boolean, Boolean] {
    var subscription: Flow.Subscription = null
    var count = 0

    def onSubscribe(subscription: Flow.Subscription): Unit = {
      this.subscription = subscription
      subscription.request(CAP)
    }

    def onNext(item: Boolean): Unit = {
      if (({ count += 1; count } & ((CAP >>> 1) - 1)) == 0)
        subscription.request(CAP >>> 1)

      submit(item)
    }

    def onError(ex: Throwable): Unit = {
      closeExceptionally(ex)
    }

    def onComplete(): Unit = {
      close()
    }
  }

  final class Pub extends RecursiveAction {
    final val pub =
      new SubmissionPublisher[Boolean](ForkJoinPool.commonPool(), CAP)

    def compute(): Unit = {
      val p = pub
      for (j <- 0 until PROCESSORS) {
        val t = new Proc(ForkJoinPool.commonPool(), CAP)
        for (i <- 0 until CONSUMERS) {
          t.subscribe(new Sub())
        }
        p.subscribe(t)
      }
      for (i <- 0 until ITEMS) {
        p.submit(true)
      }
      p.close()
    }
  }
}
