package com.github.j5ik2o.dockerController.mysql

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.DockerControllerImpl
import com.github.j5ik2o.dockerController.mysql.MySQLController._

import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.util.matching.Regex

object MySQLController {
  final val ImageName: String           = "mysql"
  final val ImageTag: Option[String]    = Some("5.7")
  final val DefaultContainerPort: Int   = 3306
  final val RegexOfWaitPredicate: Regex = s"""Port.*$DefaultContainerPort.*""".r

  def apply(dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
      hostPort: Int,
      rootPassword: String,
      userNameAndPassword: Option[MySQLUserNameAndPassword] = None,
      databaseName: Option[String] = None
  ): MySQLController =
    new MySQLController(dockerClient, outputFrameInterval)(hostPort, rootPassword, userNameAndPassword, databaseName)
}

case class MySQLUserNameAndPassword(name: String, password: String)

class MySQLController(dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
    hostPort: Int,
    rootPassword: String,
    userNameAndPassword: Option[MySQLUserNameAndPassword] = None,
    databaseName: Option[String] = None
) extends DockerControllerImpl(dockerClient, outputFrameInterval)(ImageName, ImageTag) {

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val containerPort = ExposedPort.tcp(DefaultContainerPort)
    val portBinding   = new Ports()
    portBinding.bind(containerPort, Ports.Binding.bindPort(hostPort))
    val result = super
      .newCreateContainerCmd()
      .withEnv(s"MYSQL_ROOT_PASSWORD=$rootPassword")
      .withExposedPorts(containerPort)
      .withHostConfig(newHostConfig().withPortBindings(portBinding))
    val result2 = userNameAndPassword.fold(result) {
      case MySQLUserNameAndPassword(u, p) =>
        val op = Map("MYSQL_USER" -> u, "MYSQL_PASSWORD" -> p).map { case (k, v) => s"$k=$v" }.toArray
        result.withEnv(result.getEnv ++ op: _*)
    }
    val result3 = databaseName.fold(result2) { name => result2.withEnv(result2.getEnv :+ s"MYSQL_DATABASE=$name": _*) }
    result3
  }
}
