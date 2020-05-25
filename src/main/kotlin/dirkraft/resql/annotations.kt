package dirkraft.resql

import kotlin.reflect.KClass

/**
 * Used by AutoDao.update.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class PrimaryKey

/**
 * Used by AutoDao.upsert.
 *
 * When placed on class, composite key cols must be given.
 * When placed on property, col inferred (do not use composite).
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class UniqueKey(vararg val columns: String)

/**
 * Used by Resql.readCollection.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class InnerEnum(val type: KClass<out Enum<*>>)

/**
 * Ignore a property in all reflection.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class NotAColumn

/**
 * Auto-Migrator, defines varchar size.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Varchar(val limit: Int)
