package wvlet.obj

import wvlet.core._
import wvlet.core.rx.Flow
import wvlet.core.tablet.{Column, Schema, Tablet, TabletWriter}
import xerial.core.log.Logger
import xerial.lens.{ObjectSchema, Primitive, TextType, TypeConverter}

import scala.reflect.ClassTag

object ObjectWriter {

  def createScheamOf[A: ClassTag](name: String): Schema = {
    val schema = ObjectSchema.of[A]
    val tabletColumnTypes: Seq[Column] = for (p <- schema.parameters) yield {
      val vt = p.valueType
      val columnType: Tablet.Type = vt match {
        case Primitive.Byte => Tablet.INTEGER
        case Primitive.Short => Tablet.INTEGER
        case Primitive.Int => Tablet.INTEGER
        case Primitive.Long => Tablet.INTEGER
        case Primitive.Float => Tablet.FLOAT
        case Primitive.Double => Tablet.FLOAT
        case Primitive.Char => Tablet.STRING
        case Primitive.Boolean => Tablet.BOOLEAN
        case TextType.String => Tablet.STRING
        case TextType.File => Tablet.STRING
        case TextType.Date => Tablet.STRING
        case _ =>
          // TODO support Option, Array, Map, the other types etc.
          Tablet.STRING
      }
      Column(p.name, columnType)
    }
    Schema(name, tabletColumnTypes)
  }

}

/**
  *
  */
class ObjectInput(cls:Class[_]) extends Input with Logger {
  val objSchema = ObjectSchema(cls)

  // TODO Create data conversion operator using Tablet
  //val tablet = createSchemaOf[A](name)

  def write(record: Any, output: TabletWriter, flow:Flow[String]) {
    output.writeRecord(flow) {
      for (p <- objSchema.parameters) {
        val v = p.get(record)
        if (v == null) {
          output.writeNull
        }
        else {
          info(v)
          p.valueType match {
            case Primitive.Byte | Primitive.Short | Primitive.Int | Primitive.Long =>
              TypeConverter.convertToPrimitive(v, Primitive.Long) match {
                case Some(l) =>
                  output.writeLong(l)
                case None =>
                  output.writeNull
              }
            case Primitive.Float | Primitive.Double =>
              TypeConverter.convertToPrimitive(v, Primitive.Double) match {
                case Some(d) => output.writeDouble(d)
                case None => output.writeNull
              }
            case Primitive.Boolean =>
              TypeConverter.convertToPrimitive(v, Primitive.Boolean) match {
                case Some(b) => output.writeBoolean(b)
                case None => output.writeNull
              }
            case Primitive.Char | TextType.String | TextType.File | TextType.Date =>
              output.writeString(v.toString)
            case other =>
              // TODO support Array, Map, etc.
              output.writeString(other.toString())
          }
        }
      }
    }
  }
}


