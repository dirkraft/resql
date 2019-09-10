package dirkraft.resql

import kotliquery.*
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
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
abstract class Resql {
  // note to self: don't put annotation processing in here.
  // This part just does the reflection. The SQL builders/helpers are part of the AutoDao.

  open val dataSource get() = HikariCP.dataSource()

  inline fun <reified T : Any> get(sql: String, vararg args: Any?): T {
    return get(sql, args.toList())
  }

  inline fun <reified T : Any> get(sql: String, args: List<Any?>): T {
    return get(T::class, sql, args)
  }

  fun <T : Any> get(type: KClass<T>, sql: String, vararg args: Any?): T {
    return get(type, sql, args.toList())
  }

  fun <T : Any> get(type: KClass<T>, sql: String, args: List<Any?>): T {
    return find(type, sql, args) ?: throw ResqlException(404, "No record returned. $sql $args")
  }

  fun <T> get(sql: String, vararg args: Any?, mapper: (Row) -> T): T {
    return get(sql, args.toList(), mapper)
  }

  fun <T> get(sql: String, args: List<Any?>, mapper: (Row) -> T): T {
    return find(sql, args, mapper = mapper)
      ?: throw ResqlException(404, "Nothing was returned.D id you mean to call .single? $sql $args")
  }

  inline fun <reified T : Any> find(sql: String, vararg args: Any?): T? {
    return find(sql, args.toList())
  }

  inline fun <reified T : Any> find(sql: String, args: List<Any?>): T? {
    return find(T::class, sql, args)
  }

  fun <T : Any> find(type: KClass<T>, sql: String, vararg args: Any?): T? {
    return find(type, sql, args.toList())
  }

  fun <T : Any> find(type: KClass<T>, sql: String, args: List<Any?>): T? {
    return find(sql, args) { reflectivelyMap(it, type) }
  }

  fun <T> find(sql: String, vararg args: Any?, mapper: (Row) -> T): T? {
    return find(sql, args.toList(), mapper)
  }

  fun <T> find(sql: String, args: List<Any?>, mapper: (Row) -> T): T? {
    return using(sessionOf(dataSource)) { sess: Session ->
      sess.run(Query(sql, args).map { mapper(it) }.asSingle)
    }
  }

  inline fun <reified T : Any> list(sql: String, vararg args: Any?): List<T> {
    return list(sql, args.toList())
  }

  inline fun <reified T : Any> list(sql: String, args: List<Any?>): List<T> {
    return list(T::class, sql, args)
  }

  fun <T : Any> list(type: KClass<T>, sql: String, vararg args: Any?): List<T> {
    return list(type, sql, args.toList())
  }

  fun <T : Any> list(type: KClass<T>, sql: String, args: List<Any?>): List<T> {
    return list(sql, args) { reflectivelyMap(it, type) }
  }

  fun <T> list(sql: String, vararg args: Any?, mapper: (Row) -> T): List<T> {
    return list(sql, args.toList(), mapper)
  }

  fun <T> list(sql: String, args: List<Any?>, mapper: (Row) -> T): List<T> {
    return using(sessionOf(dataSource)) { sess: Session ->
      sess.run(Query(sql, args).map { mapper(it) }.asList)
    }
  }

  fun exec(sql: String, vararg args: Any?) {
    exec(sql, args.toList())
  }

  fun exec(sql: String, args: List<Any?>) {
    using(sessionOf(dataSource)) { sess: Session ->
      sess.run(Query(sql, args).asExecute)
    }
  }

  open fun <T : Any> reflectivelyMap(row: Row, type: KClass<T>): T {
    val cons: KFunction<T> = type.primaryConstructor!!

    val consArgs: Array<Any?> = cons.parameters.map { consParam: KParameter ->
      val colName = ResqlStrings.camel2Snake(consParam.name!!)

      @Suppress("IMPLICIT_CAST_TO_ANY")
      when (val classish: KClassifier = consParam.type.classifier!!) {
        Boolean::class -> row.boolean(colName) // no booleanOrNull variant?
        Int::class -> row.intOrNull(colName)
        Long::class -> row.longOrNull(colName)
        String::class -> row.stringOrNull(colName)
        Instant::class -> row.instantOrNull(colName)
        else -> throw ResqlException(500, "Don't know how to map into param type $classish")
      }
    }.toTypedArray()

    return cons.call(*consArgs)
  }

  companion object : Resql()
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
