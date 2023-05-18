ThisBuild / tlBaseVersion := "0.0" // your current series x.y

ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("ivanmoreau", "Iván Molina Rebolledo")
)

wartremoverErrors ++= Warts.unsafe

val Scala3Version = "3.2.2"
val Scala2_13Version = "2.13.10"

ThisBuild / crossScalaVersions := Seq(Scala3Version, Scala2_13Version)
ThisBuild / scalaVersion := Scala3Version

lazy val root = tlCrossRootProject.aggregate(eio)

lazy val eio = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("eio"))
  .settings(
    name := "eio",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % "3.5.0",
      "org.typelevel" %%% "cats-mtl" % "1.3.0"
    )
  )
