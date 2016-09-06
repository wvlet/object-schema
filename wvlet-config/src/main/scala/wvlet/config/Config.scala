/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wvlet.config

import java.io.{File, FileInputStream, FileNotFoundException}
import java.util.Properties

import wvlet.config.YamlReader.loadMapOf
import wvlet.log.LogSupport
import wvlet.log.io.IOUtil
import wvlet.obj._

import scala.reflect.ClassTag
import scala.reflect.runtime.{universe => ru}

case class ConfigHolder(tpe: ObjectType, value: Any)

case class ConfigPaths(configPaths: Seq[String]) extends LogSupport {
  info(s"Config file paths: [${configPaths.mkString(", ")}]")

  def findConfigFile(name: String): String = {
    configPaths
    .map(p => new File(p, name))
    .find(_.exists())
    .map(_.getPath)
    .getOrElse(throw new FileNotFoundException(s"${name} is not found"))
  }
}

object Config extends LogSupport {
  private def defaultConfigPath = Seq(
    ".", // current directory
    sys.props.getOrElse("prog.home", "") // program home for wvlet-launcher
  )

  def apply(env: String, defaultEnv: String = "default", configPaths: Seq[String] = defaultConfigPath): Config = Config(ConfigEnv(env, defaultEnv, configPaths),
    Map.empty[ObjectType, ConfigHolder])

  private def cleanupConfigPaths(paths: Seq[String]) = {
    val b = Seq.newBuilder[String]
    for (p <- paths) {
      if (!p.isEmpty) {
        b += p
      }
    }
    val result = b.result()
    if (result.isEmpty) {
      Seq(".") // current directory
    }
    else {
      result
    }
  }

  def REPORT_UNUSED_PROPERTIES : Properties => Unit = { unused:Properties =>
    warn(s"There are unused properties: ${unused}")
  }
  def REPORT_ERROR_FOR_UNUSED_PROPERTIES: Properties => Unit = { unused: Properties =>
    throw new IllegalArgumentException(s"There are unused properties: ${unused}")
  }
}

case class ConfigEnv(env: String, defaultEnv: String, configPaths: Seq[String]) {
  def withConfigPaths(paths: Seq[String]): ConfigEnv = ConfigEnv(env, defaultEnv, paths)
}


import Config._

case class Config private[config](env: ConfigEnv, holder: Map[ObjectType, ConfigHolder]) extends Iterable[ConfigHolder] with LogSupport {

  // Customization
  def withEnv(newEnv: String, defaultEnv: String = "default"): Config = {
    Config(ConfigEnv(newEnv, defaultEnv, env.configPaths), holder)
  }

  def withConfigPaths(paths: Seq[String]): Config = {
    Config(env.withConfigPaths(paths), holder)
  }

  // Accessors to configurations
  def getAll: Seq[ConfigHolder] = holder.values.toSeq
  override def iterator: Iterator[ConfigHolder] = holder.values.iterator

  private def find[A](tpe: ObjectType): Option[Any] = {
    holder.get(tpe).map(_.value)
  }

  def of[ConfigType](implicit tag: ru.TypeTag[ConfigType]): ConfigType = {
    val t = ObjectType.ofTypeTag(tag)
    find(t) match {
      case Some(x) =>
        x.asInstanceOf[ConfigType]
      case None =>
        throw new IllegalArgumentException(s"No config value for ${t} is found")
    }
  }

  def +(h: ConfigHolder): Config = Config(env, this.holder + (h.tpe -> h))
  def ++(other: Config): Config = {
    Config(env, this.holder ++ other.holder)
  }

  def register[ConfigType: ru.TypeTag](config: ConfigType): Config = {
    val tpe = ObjectType.ofTypeTag(implicitly[ru.TypeTag[ConfigType]])
    this + ConfigHolder(tpe, config)
  }

  def registerFromYaml[ConfigType: ru.TypeTag : ClassTag](yamlFile: String): Config = {
    val tpe = ObjectType.ofTypeTag(implicitly[ru.TypeTag[ConfigType]])
    val config: Option[ConfigType] = loadFromYaml[ConfigType](yamlFile, onMissing = {
      throw new FileNotFoundException(s"${yamlFile} is not found in ${env.configPaths.mkString(":")}")
    })
    config match {
      case Some(x) =>
        this + ConfigHolder(tpe, x)
      case None =>
        throw new IllegalStateException(s"No configuration for ${tpe} (${env.env} or ${env.defaultEnv}) is found")
    }
  }

  private def loadFromYaml[ConfigType: ru.TypeTag](yamlFile: String, onMissing: => Option[ConfigType]): Option[ConfigType] = {
    val tpe = ObjectType.ofTypeTag(implicitly[ru.TypeTag[ConfigType]])
    findConfigFile(yamlFile) match {
      case None =>
        onMissing
      case Some(realPath) =>
        val m = loadMapOf[ConfigType](realPath)(ClassTag(tpe.rawType))
        m.get(env.env) match {
          case Some(x) =>
            info(s"Loading ${tpe} from ${realPath}, env:${env.env}")
            Some(x)
          case None =>
            // Load default
            debug(s"Configuration for ${env.env} is not found in ${realPath}. Load ${env.defaultEnv} configuration instead")
            m.get(env.defaultEnv).map{ x =>
              info(s"Loading ${tpe} from ${realPath}, default env:${env.defaultEnv}")
              x
            }.orElse(onMissing)
        }
    }
  }

  def registerFromYamlOrElse[ConfigType: ru.TypeTag : ClassTag](yamlFile: String, defaultValue: => ConfigType): Config = {
    val tpe = ObjectType.ofTypeTag(implicitly[ru.TypeTag[ConfigType]])
    val config = loadFromYaml[ConfigType](yamlFile, onMissing = Some(defaultValue))
    this + ConfigHolder(tpe, config)
  }

  def overrideWithProperties(props: Properties, onUnusedProperties: Properties => Unit = REPORT_UNUSED_PROPERTIES): Config = {
    ConfigOverwriter.overrideWithProperties(this, props, onUnusedProperties)
  }

  def overrideWithPropertiesFile(propertiesFile: String, onUnusedProperties: Properties => Unit = REPORT_UNUSED_PROPERTIES): Config = {
    findConfigFile(propertiesFile) match {
      case None =>
        throw new FileNotFoundException(propertiesFile)
      case Some(propPath) =>
        val props = IOUtil.withResource(new FileInputStream(propPath)) { in =>
          val p = new Properties()
          p.load(in)
          p
        }
        overrideWithProperties(props, onUnusedProperties)
    }
  }

  private def findConfigFile(name: String): Option[String] = {
    env.configPaths
    .map(p => new File(p, name))
    .find(_.exists())
    .map(_.getPath)
  }
}
