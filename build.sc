import mill._, scalalib._

object eio extends SbtModule {
  def scalaVersion = "3.2.2"

  def ivyDeps = Agg(
    ivy"org.typelevel::cats-effect::3.5.0",
    ivy"org.typelevel::cats-mtl::1.3.0"
  )
}