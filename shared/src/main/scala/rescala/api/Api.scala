package rescala.api



import scala.language.higherKinds

trait Api {
  type Signal[+A]
  type Event[+A]
  type Var[A] <: Signal[A]
  type Evt[A] <: Event[A]
  def Evt[A](): Evt[A]
  def Var[A](v: A): Var[A]

  def observe[A](event: Event[A])(f: A => Unit): Unit
  def now[A](signal: Signal[A]): A

  def fire[A](evt: Evt[A], value: A): Unit
  def set[A](vr: Var[A], value: A): Unit


  def mapS[A, B](signal: Signal[A])(f: A => B): Signal[B]
  def mapE[A, B](event: Event[A])(f: A => B): Event[B]
  def fold[A, Acc](event: Event[A])(init: Acc)(f: (Acc, A) => Acc): Signal[Acc]
  def changed[A](signal: Signal[A]): Event[A]
}

object Api {
  object synchronApi extends Api {

    import rescala.engines.CommonEngines.synchron

    override type Signal[+A] = synchron.Signal[A]
    override type Event[+A] = synchron.Event[A]
    override type Var[A] = synchron.Var[A]
    override type Evt[A] = synchron.Evt[A]


    override def Evt[A](): Evt[A] = synchron.Evt()
    override def Var[A](v: A): Var[A] = synchron.Var(v)
    override def mapS[A, B](signal: Signal[A])(f: (A) => B): Signal[B] = signal.map(f)
    override def mapE[A, B](event: Event[A])(f: (A) => B): Event[B] = event.map(f)
    override def fold[A, Acc](event: Event[A])(init: Acc)(f: (Acc, A) => Acc): Signal[Acc] = event.fold(init)(f)
    override def changed[A](signal: Signal[A]): Event[A] = signal.changed

    override def observe[A](event: Event[A])(f: (A) => Unit): Unit = event.observe(f)
    override def now[A](signal: Signal[A]): A = signal.now
    override def fire[A](evt: Evt[A], value: A): Unit = evt.fire(value)
    override def set[A](vr: Var[A], value: A): Unit = vr.set(value)
  }
}