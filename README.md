# object-schema  [![Gitter Chat][gitter-badge]][gitter-link] [![CircleCI](https://circleci.com/gh/wvlet/object-schema.svg?style=svg)](https://circleci.com/gh/wvlet/object-schema) [![Coverage Status][coverall-badge]][coverall-link]

[gitter-badge]: https://badges.gitter.im/Join%20Chat.svg
[gitter-link]: https://gitter.im/wvlet/wvlet?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge
[coverall-badge]: https://coveralls.io/repos/github/wvlet/object-schema/badge.svg?branch=master
[coverall-link]: https://coveralls.io/github/wvlet/object-schema?branch=master

A library for inspecting Object schema (e.g., parameter names and its types, constructor, etc.)

## Usage

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.wvlet/object-schema_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.wvlet/object-schema_2.11/)

```scala
libraryDependencies += "org.wvlet" %% "object-schema" % "(version)"
```


```scala
import wvlet.obj.ObjectSchema

case class Person(id:Int, name:String)
val scheam = ObjectSchema.of[Person]
```

The retrieved `ObjectSchema` has detailed information of your class, including:
* Constructor and its argument types and names.
* case class parameters
* Methods

Extracting parameter names is impossible in Java programs, since JVM drops parameters names used in the source code at compile-time. While Scaal retains parameter names within the byte code of the class as ScalaSig. wvlet-obj reads ScalaSig and extract method parameter names and
detailed types (e.g., type parameters used in Generics: e.g., `String` in `List[String]`).


## Applications

### Dependency injection
wvlet-obj is highly utilized in [Airframe](https://github.com/wvlet/airframe), [wvlet-jmx](https://github.com/wvlet/jmx) to build programs by inspecting the shape of objects.

For example, in the following code wvlet-inject finds the constructor of `MyApp` class, then list dependencies (`Module1`, `Module2`, ...) that are necessary to build an `MyApp` instance:
```scala
class MyApp(m1:Module1, m2:Module2)

trait AppMain {
  val p = inject[MyApp]
}
```

### Runtime code generation

In wvlet-jmx, `@JMX` annotation is used to register JMX parameter to JMX registry server so that we can monitor the behavior of the application outside JVM.
You only need to aad `@JMX` annotation to start collecting JMX metrics:

```scala
case class Metrics(@JMX loadAvg:Double, @JMX memoryUsage:Double)

class MonitoringApp {
  @JMX def reportMetrics : Metrics = ...
}
```

wvlet-jmx checks the object schema of `Metrics` and registers its parameters to JMX registry.

## Object mapping

[wvlet-config](../wvlet-config) reads Yaml files and binds the data into objects with wvlet-obj.

**config.yml**
```
default:
   timeout-millis: 100
   max-connection: 250

```

You can read this yaml file as follows:
```
case class ConnectionConfig(timeoutMillis:Long, maxConnection:Int)
val config = YamlReader.load[ConnectionConfig]("config.yml", "default")
```

Internally wvlet-obj inspects ConnectionConfig parameterss, and builds the config object by mapping property values written in the Yaml file.
