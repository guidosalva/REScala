package benchmarks.simple

import java.util.concurrent.TimeUnit

import benchmarks.{EngineParam, Size, Step, Workload}
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.{BenchmarkParams, ThreadParams}
import rescala.Engines
import rescala.engines.Engine
import rescala.propagation.Turn
import rescala.reactives.{Signal, Signals}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(4)
@State(Scope.Benchmark)
class ReverseFan[S <: rescala.graph.Struct] {

  implicit var engine: Engine[S, Turn[S]] = _

  var sources: Array[rescala.reactives.Var[Int, S]] = _
  var result: Signal[Int, S] = _
  var isManual: Boolean = false

  @Setup
  def setup(params: BenchmarkParams, size: Size, step: Step, engineParam: EngineParam[S], work: Workload) = {
    engine = engineParam.engine
    val localEngine = engine
    import localEngine._
    val threads = params.getThreads
    sources = Array.fill(threads)(Var(step.get()))
    val intermediate = sources.map(_.map { v => {work.consume(); v + 1} })
    result = Signals.lift(intermediate) { values => work.consumeSecondary(); values.sum }
    if (engine == Engines.unmanaged) isManual = true

  }

  @Benchmark
  def run(step: Step, params: ThreadParams): Unit =
    if (isManual) synchronized {sources(params.getThreadIndex).set(step.run())}
    else sources(params.getThreadIndex).set(step.run())
}
