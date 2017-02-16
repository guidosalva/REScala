package benchmarks.simple

import java.util.concurrent.TimeUnit

import benchmarks.{EngineParam, Workload}
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.BenchmarkParams
import rescala.engine.Engine
import rescala.propagation.Turn
import rescala.reactives._

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
class Creation[S <: rescala.graph.Struct] {

  implicit var engine: Engine[S, Turn[S]] = _

  @Setup
  def setup(params: BenchmarkParams, work: Workload, engineParam: EngineParam[S]) = {
    engine = engineParam.engine
  }

  @Benchmark
  def `var`(): Var[String, S] = {
    engine.Var("")
  }

  @Benchmark
  def `evt`(): Evt[String, S] = {
    engine.Evt[String]
  }

  @Benchmark
  def `derived signal`(): Signal[String, S] = {
    engine.Var("").map(identity)
  }

  @Benchmark
  def `derived event`(): Event[String, S] = {
    engine.Evt[String].map(identity)
  }

}
