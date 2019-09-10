package dirkraft.resql

/**
 * Used by AutoDao.update.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class PrimaryKey

/**
 * Used by AutoDao.upsert.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class UniqueKey
