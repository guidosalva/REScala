package examples.demo

import examples.demo.LFullyModularBall.BouncingBall
import examples.demo.MPlayingFieldBall.PlayingField
import examples.demo.ORacketMultiBall.Racket
import examples.demo.ui._
import rescala._

import scala.swing.{Dimension, MainFrame, SimpleSwingApplication}

object PSplitscreenRacketBall extends Main {
  class Opponent(panelSize: Signal[Dimension], shapes: Signal[List[Shape]]) extends SimpleSwingApplication {
    val panel2 = new ShapesPanel(shapes)
    override lazy val top = new MainFrame{
      title = "Player 2"
      contents = panel2
      resizable = false
    }
    panelSize.observe { d =>
      panel2.preferredSize = d
      top.pack()
    }
  }

  val shapes = Var[List[Shape]](List.empty)
  val panel = new ShapesPanel(shapes)

  val playingField = new PlayingField(panel.width.map(_ - 25), panel.height.map(_ - 25))
  val racket = new Racket(playingField.width, true, playingField.height, panel.Mouse.y)
  shapes.transform(playingField.shape :: racket.shape :: _)

  val opponent = new Opponent(panel.sigSize, shapes)
  opponent.main(Array())
  val racket2 = new Racket(playingField.width, false, playingField.height, opponent.panel2.Mouse.y)
  shapes.transform(racket2.shape :: _)

  def makeBall(initVx: Double, initVy: Double) = {
    val bouncingBall = new BouncingBall(initVx, initVy, Var(50), panel.Mouse.middleButton.pressed)
    shapes.transform(bouncingBall.shape :: _)

    val fieldCollisions = playingField.colliders(bouncingBall.shape)
    bouncingBall.horizontalBounceSources.transform(fieldCollisions.left :: fieldCollisions.right :: _)
    bouncingBall.verticalBounceSources.transform(fieldCollisions.top :: fieldCollisions.bottom :: _)

    val racketCollision = racket.collisionWith(bouncingBall.shape) || racket2.collisionWith(bouncingBall.shape)
    bouncingBall.horizontalBounceSources.transform(racketCollision :: _)
  }
  makeBall(200d, 150d)
  makeBall(-200d, 100d)
}
