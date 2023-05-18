ThisBuild / libraryDependencySchemes += "org.scala-native" % "sbt-scala-native" % "always"

addSbtPlugin("org.typelevel" % "sbt-typelevel" % "0.4.21")
// addSbtPlugin("org.typelevel" % "sbt-typelevel-site" % "0.4.21")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.2.0")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.2.0")

addSbtPlugin("org.scala-js"     % "sbt-scalajs"      % "1.13.1")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.12")
// addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.3.7")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "3.1.2")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.7")