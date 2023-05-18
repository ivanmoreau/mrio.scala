ThisBuild / tlBaseVersion := "0.0"

ThisBuild / licenses := Seq(
  "MPL-2.0" -> url("https://www.mozilla.org/media/MPL/2.0/index.f75d2927d3c1.txt")
)
ThisBuild / developers := List(
  tlGitHubDev("ivanmoreau", "Iván Molina Rebolledo")
)

wartremoverErrors ++= Warts.unsafe

val Scala3Version = "3.2.2"
val Scala2_13Version = "2.13.10"

ThisBuild / crossScalaVersions := Seq(Scala3Version, Scala2_13Version)
ThisBuild / scalaVersion := Scala3Version

lazy val root = tlCrossRootProject.aggregate(eio, mrio)

lazy val eio = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("eio"))
  .settings(
    name := "eio",
    scalacOptions := Seq(
      "-Ykind-projector:underscores"
    ),
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % "3.5.0",
      "org.typelevel" %%% "cats-mtl" % "1.3.0"
    )
  )

lazy val mrio = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("mrio"))
  .dependsOn(eio)
  .settings(
    name := "mrio",
    scalacOptions := Seq(
      "-Ykind-projector:underscores"
    ),
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % "3.5.0"
    )
  )
