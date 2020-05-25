package dirkraft.resql

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation

/**
 * snake_case the data class's simple name as the corresponding table name and "quote" it
 */
internal fun inferTable(type: KClass<out Any>, quoted: Boolean = true): String {
  val snakeName = ResqlStrings.camel2Snake(type.simpleName!!)
  return if (quoted) '"' + snakeName + '"' else snakeName
}

internal fun reflectToColVals(row: Any, includeNulls: Boolean, excludeCol: String? = null): List<Col> {
  return reflectToCols(row::class, excludeCol).mapNotNull { col ->
    val colVal = col.prop!!.getter.call(row)
    if (includeNulls || colVal != null) {
      col.copy(value = colVal)
    } else {
      null
    }
  }
}

internal fun reflectToCols(klass: KClass<out Any>, excludeCol: String? = null): List<Col> {
  return klass.declaredMemberProperties
    .filter { it.findAnnotation<NotAColumn>() == null }
    .mapNotNull { prop ->
      val colName = ResqlStrings.camel2Snake(prop.name)
      if (colName != excludeCol) {
        Col(colName, prop)
      } else {
        null
      }
    }
}

data class Col(
  val name: String,
  val prop: KProperty1<out Any, *>? = null,
  val value: Any? = null,
  val sql: ColSql? = null
)

data class ColSql(
  var name: String,
  var type: String,
  var nullable: Boolean,
  var pk: Boolean
) {
  override fun toString(): String = "$name $type" +
    (if (pk) " primary key" else "") +
    (if (!pk && nullable) "" else " not null ")
}
