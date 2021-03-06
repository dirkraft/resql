package dirkraft.resql

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.*
import org.intellij.lang.annotations.Language
import org.postgresql.util.PGInterval
import org.postgresql.util.PGobject
import java.lang.reflect.Proxy
import java.sql.Connection
import java.time.Duration
import java.time.Instant
import javax.sql.DataSource
import kotlin.math.floor
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

/**
 * Using reflection and naming conventions turns result sets into data class instances.
 * Generally usage is to define your data class, and give it an AutoDao.
 *
 * Some method name conventions
 * - "get" a single row or throw an exception
 * - "find" a single row or return null
 * - "list" 0 to n records
 *
 * @see AutoDao
 */
open class Resql {
  // note to self: don't put annotation processing in here.
  // This part just does the reflection. The SQL builders/helpers are part of the AutoDao.

  inline fun <reified T : Any> get(@Language("SQL") sql: String, vararg args: Any?): T {
    return get(sql, args.toList())
  }

  inline fun <reified T : Any> get(@Language("SQL") sql: String, args: List<Any?>): T {
    return get(T::class, sql, args)
  }

  fun <T : Any> get(type: KClass<T>, @Language("SQL") sql: String, vararg args: Any?): T {
    return get(type, sql, args.toList())
  }

  fun <T : Any> get(type: KClass<T>, @Language("SQL") sql: String, args: List<Any?>): T {
    return find(type, sql, args) ?: throw ResqlException(404, "No record returned. $sql $args")
  }

  fun <T> get(@Language("SQL") sql: String, vararg args: Any?, mapper: (Row) -> T): T {
    return get(sql, args.toList(), mapper)
  }

  fun <T> get(@Language("SQL") sql: String, args: List<Any?>, mapper: (Row) -> T): T {
    return find(sql, args, mapper = mapper)
      ?: throw ResqlException(404, "Nothing was returned.D id you mean to call .single? $sql $args")
  }

  inline fun <reified T : Any> find(@Language("SQL") sql: String, vararg args: Any?): T? {
    return find(sql, args.toList())
  }

  inline fun <reified T : Any> find(@Language("SQL") sql: String, args: List<Any?>): T? {
    return find(T::class, sql, args)
  }

  fun <T : Any> find(type: KClass<T>, @Language("SQL") sql: String, vararg args: Any?): T? {
    return find(type, sql, args.toList())
  }

  fun <T : Any> find(type: KClass<T>, @Language("SQL") sql: String, args: List<Any?>): T? {
    return find(sql, args) { reflectivelyMap(it, type) }
  }

  fun <T> find(@Language("SQL") sql: String, vararg args: Any?, mapper: (Row) -> T): T? {
    return find(sql, args.toList(), mapper)
  }

  fun <T> find(@Language("SQL") sql: String, args: List<Any?>, mapper: (Row) -> T): T? {
    return using(sessionOf(dataSource)) { sess: Session ->
      sess.run(mapQuery(sql, args, sess).map { mapper(it) }.asSingle)
    }
  }

  inline fun <reified T : Any> list(@Language("SQL") sql: String, vararg args: Any?): List<T> {
    return list(sql, args.toList())
  }

  inline fun <reified T : Any> list(@Language("SQL") sql: String, args: List<Any?>): List<T> {
    return list(T::class, sql, args)
  }

  fun <T : Any> list(type: KClass<T>, @Language("SQL") sql: String, vararg args: Any?): List<T> {
    return list(type, sql, args.toList())
  }

  fun <T : Any> list(type: KClass<T>, @Language("SQL") sql: String, args: List<Any?>): List<T> {
    return list(sql, args) { reflectivelyMap(it, type) }
  }

  fun <T> list(@Language("SQL") sql: String, vararg args: Any?, mapper: (Row) -> T): List<T> {
    return list(sql, args.toList(), mapper)
  }

  fun <T> list(@Language("SQL") sql: String, args: List<Any?>, mapper: (Row) -> T): List<T> {
    return using(sessionOf(dataSource)) { sess: Session ->
      sess.run(mapQuery(sql, args, sess).map { mapper(it) }.asList)
    }
  }

  fun exec(@Language("SQL") sql: String, vararg args: Any?) {
    exec(sql, args.toList())
  }

  fun exec(@Language("SQL") sql: String, args: List<Any?>) {
    using(sessionOf(dataSource)) { sess: Session ->
      sess.run(mapQuery(sql, args, sess).asExecute)
    }
  }

  open fun mapQuery(sql: String, args: List<Any?>, sess: Session): Query {
    val transformed = args.map { arg ->
      when (arg) {
        is Duration -> PGobject().apply {
          type = "INTERVAL"
          value = arg.toString()
        }
        is JsonNode -> PGobject().apply {
          type = "JSON"
          value = mapper.writeValueAsString(arg)
        }
        is Enum<*> -> arg.name
        // Kotliquery wants List<Any> but should be List<Any?>
        is Collection<*> -> sess.connection.underlying.createArrayOf("TEXT", arg.toTypedArray())
        is Iterable<*> -> sess.connection.underlying.createArrayOf("TEXT", arg.toList().toTypedArray())
        else -> arg
      }
    }
    return Query(sql, transformed)
  }

  open fun <T : Any> reflectivelyMap(row: Row, type: KClass<T>): T {
    val cons: KFunction<T> = type.primaryConstructor!!

    val consArgs: Array<Any?> = cons.parameters.map { consParam: KParameter ->
      val colName = ResqlStrings.camel2Snake(consParam.name!!)

      @Suppress("IMPLICIT_CAST_TO_ANY")
      when (val classish: KClassifier = consParam.type.classifier!!) {
        String::class -> row.stringOrNull(colName)
        Char::class -> row.stringOrNull(colName)?.toCharArray()?.single()
        Long::class -> row.longOrNull(colName)
        Int::class -> row.intOrNull(colName)
        Boolean::class -> row.boolean(colName) // no booleanOrNull variant?
        Instant::class -> row.instantOrNull(colName)
        Duration::class -> {
          val ival = (row.any(colName) as PGInterval)
          val seconds = floor(ival.seconds)
          Duration.ZERO
            .plusDays(ival.days.toLong())
            .plusHours(ival.hours.toLong())
            .plusMinutes(ival.minutes.toLong())
            .plusSeconds(seconds.toLong())
            .plusNanos(((ival.seconds - seconds) * 10e9).toLong())
        }
        MutableList::class -> readCollection(consParam, row.arrayOrNull(colName))?.toMutableList()
        List::class -> readCollection(consParam, row.arrayOrNull(colName))?.toList()
        MutableSet::class -> readCollection(consParam, row.arrayOrNull(colName))?.toMutableSet()
        Set::class -> readCollection(consParam, row.arrayOrNull(colName))?.toSet()
        JsonNode::class -> row.stringOrNull(colName)?.let(mapper::readTree)
        else -> {
          val javaClass = (classish as KClass<*>).java

          if (javaClass.isEnum) {
            row.stringOrNull(colName)?.let { dbVal ->
              @Suppress("UNCHECKED_CAST")
              (javaClass.enumConstants as Array<Enum<*>>).find { enum ->
                enum.name == dbVal
              } ?: throw ResqlException(500, "Failed to parse $dbVal into $javaClass")
            }

          } else {
            throw ResqlException(500, "Don't know how to map into param type $classish")
          }
        }
      }
    }.toTypedArray()

    return cons.call(*consArgs)
  }
  fun <T : kotlin.Enum<T>> safeValueOf(type: String, klass: Class<T>): T? {
    return java.lang.Enum.valueOf(klass, type)
  }
  open fun readCollection(consParam: KParameter, arr: Array<Any?>?): Iterable<Any?>? {
    val enumType = consParam.findAnnotation<InnerEnum>()?.type
    return if (enumType == null || arr == null) {
      arr?.asIterable()
    } else {
      arr.asIterable().map { el: Any? ->
        el?.let {
          enumType.java.enumConstants.find { it.name == el }
        }
      }
    }
  }

  // So HikariCP default data source needs to be set before constructing the companion object.
  companion object : Resql() {

    private val tlResql = ThreadLocal<DataSource>()

    internal val dataSource: DataSource get() = tlResql.get() ?: HikariCP.dataSource()

    /**
     * Prepare a thread-local Resql bound to a single connection for transactional sequences.
     */
    fun <T> trx(thunk: () -> T): T {
      val classLoader = Resql::class.java.classLoader

      val dataSource = HikariCP.dataSource()
      val conn = dataSource.connection
      conn.autoCommit = false

      val noCloseConn: Connection = Proxy.newProxyInstance(
        classLoader,
        arrayOf(Connection::class.java)
      ) { _, method, args ->
        if (method.name != "close") {
          method.invoke(conn, *args)
        } else {
          null
        }
      } as Connection

      val oneConnDataSource: DataSource = Proxy.newProxyInstance(
        classLoader,
        arrayOf(DataSource::class.java)
      ) { _, method, args ->
        if (method.name == "getConnection") {
          noCloseConn
        } else {
          method.invoke(dataSource, *args)
        }
      } as DataSource

      tlResql.set(oneConnDataSource)
      try {
        val result = thunk()
        conn.commit()
        return result
      } finally {
        conn.rollback()
        tlResql.remove()
      }
    }
  }
}

/**
 * Runtime exceptions due to missing or broken support in translating between
 * local usage and corresponding transforms or effects on the database.
 *
 * Simple caller errors do not result in this exception and are usually implemented
 * with `require` which throws IllegalArgumentException.
 *
 * @param status http equivalent status, because they are a familiar set of kinds of errors
 */
open class ResqlException(val status: Int, message: String) : Exception(message)
