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

import java.util.Properties

import wvlet.log.LogSupport
import wvlet.obj.ObjectBuilder.CanonicalNameFormatter
import wvlet.obj.{ObjectBuilder, ObjectSchema, ObjectType, TaggedObjectType}

import scala.util.{Failure, Success, Try}

/**
  * Allow overwriting config objects with Java Properties
  */
object ConfigOverwriter extends LogSupport {

  private[config] case class Prefix(prefix:String, tag: Option[String]) {
    override def toString = tag match {
      case Some(t) => s"${prefix}@${t}"
      case None => prefix
    }
  }
  private[config] case class ConfigKey(prefix: Prefix, param: String) {
    override def toString = s"${prefix}.${param}"
  }
  private[config] case class ConfigProperty(key: ConfigKey, v: Any)

  private[config] def extractPrefix(t: ObjectType): Prefix = {
    def canonicalize(s: String): String = {
      val name = s.replaceAll("Config$", "")
      CanonicalNameFormatter.format(name)
    }
    t match {
      case TaggedObjectType(raw, base, taggedType) =>
        Prefix(canonicalize(base.name), Some(CanonicalNameFormatter.format(taggedType.name)))
      case _ =>
        Prefix(canonicalize(t.name), None)
    }
  }

  private[config] def configKeyOf(propKey: String): ConfigKey = {
    val c = propKey.split("\\.")
    c.length match {
      case l if l >= 2 =>
        val prefixSplit = c(0).split("@+")
        if(prefixSplit.length > 1) {
          val param = CanonicalNameFormatter.format(c(1).mkString)
          ConfigKey(Prefix(prefixSplit(0), Some(prefixSplit(1))), param)
        }
        else {
          val prefix = c(0)
          val param = CanonicalNameFormatter.format(c(1))
          ConfigKey(Prefix(prefix, None), param)
        }
      case other =>
        throw new IllegalArgumentException(s"${propKey} should have [prefix](@[tag])?.[param] format")
    }
  }

  private[config] def configToProps(configHolder: ConfigHolder): Seq[ConfigProperty] = {
    val prefix = extractPrefix(configHolder.tpe)
    val schema = ObjectSchema.of(configHolder.tpe)
    val b = Seq.newBuilder[ConfigProperty]
    for (p <- schema.parameters) yield {
      val key = ConfigKey(prefix, CanonicalNameFormatter.format(p.name))
      Try(p.get(configHolder.value)) match {
        case Success(v) => b += ConfigProperty(key, v)
        case Failure(e) =>
          warn(s"Failed to read parameter ${p} from ${configHolder}")
      }
    }
    b.result()
  }

  def overrideWithProperties(config:Config, props: Properties, onUnusedProperties: Properties => Unit): Config = {
    val overrides = {
      import scala.collection.JavaConversions._
      val b = Seq.newBuilder[ConfigProperty]
      for ((k, v) <- props) yield {
        val key = configKeyOf(k)
        val p = ConfigProperty(key, v)
        b += p
      }
      b.result
    }

    val unusedProperties = Seq.newBuilder[ConfigProperty]

    val newConfigs = for (ConfigHolder(tpe, value) <- config) yield {
      val configBuilder = ObjectBuilder.fromObject(value)
      val prefix = extractPrefix(tpe)
      val schema = ObjectSchema.of(tpe)

      val (overrideParams, unused) = overrides.filter(_.key.prefix == prefix).partition(p => schema.containsParameter(p.key.param))
      unusedProperties ++= unused
      for (p <- overrideParams) {
        trace(s"override: ${p}")
        configBuilder.set(p.key.param, p.v)
      }
      tpe -> ConfigHolder(tpe, configBuilder.build)
    }

    val unused = unusedProperties.result
    if(unused.size > 0) {
      val unusedProps = new Properties
      unused.map(p => unusedProps.put(p.key.toString, p.v.asInstanceOf[AnyRef]))
      onUnusedProperties(unusedProps)
    }

    Config(config.env, newConfigs.toMap)
  }

}
