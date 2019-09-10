package dirkraft.resql

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation

/**
 * Warning!: This is horribly insecure because of all kinds of SQL building/generation
 * and therefore enables all kinds of SQL injection attacks! Only use for private purposes
 * where all executed logic is trusted i.e. do not use in applications with users.
 *
 * For a data class, provides SQL shortcuts. Infers table and column names via snake_case.
 * Some methods may be sensitive to special annotations.
 *
 * This is defined as an interface with default implementations so usage has some flexibility.
 * However, the intended pattern is to extend the data class's companion object with AutoDao.
 *
 * ```
 * data class MyType(
 *   val myTypeId: Long? = null,
 *   /* other properties */
 * ) {
 *   companion object : AutoDao<MyType> by AutoDao.self()
 * }
 * ```
 *
 * Callers might behave like so.
 *
 * ```
 * MyType.getWhere("id = ?", 123)
 * // or with the @Id annotation
 * MyType.get(123)
 * ```
 */
interface AutoDao<T : Any> {
  val type: KClass<T>

  /**
   * Find the first match to any arbitrary WHERE clause, e.g. "column = 123"
   */
  fun findWhere(where: String, vararg args: Any?): T? {
    val sql = "SELECT * FROM ${inferTable()} WHERE $where"
    return Resql.find(type, sql, args.toList())
  }

  /**
   * @see Companion.insert
   */
  fun insert(row: T): T = Companion.insert(row)

  /**
   * @see Companion.update
   */
  fun update(row: T): T = Companion.update(row)

  /**
   * @see Companion.upsert
   */
  fun upsert(row: T): T = Companion.upsert(row)

  private fun inferTable(): String = Companion.inferTable(type)

  companion object {

    /**
     * Create an AutoDao on data class T, typically as a mixin into another dao class.
     *
     * ```
     * class MyDao : AutoDao<MyType> by AutoDao.self()
     * ```
     */
    inline fun <reified T : Any> self(): AutoDao<T> = object : AutoDao<T> {
      override val type = T::class
    }

    /**
     * INSERT this row reflectively. Null values are excluded from the SQL.
     *
     * @return the inserted row
     */
    fun <T : Any> insert(row: T): T {
      val (colNames: List<String>, colValues: List<Any>) = reflectToCols(row)
      val sql = """
        INSERT INTO ${inferTable(row::class)}(${colNames.joinToString()}) 
        VALUES (${placeholders(colNames.size)}) 
        RETURNING *"""
      return Resql.get(row::class, sql, colValues)
    }

    /**
     * UPDATE this row reflectively. Null values are excluded from the SQL.
     * The @PrimaryKey must be annotated on the data class.
     *
     * @return the updated row
     */
    fun <T : Any> update(row: T): T {
      val (pkCol: String, pkVal: Any?) = getAnnotatedProperty<PrimaryKey>(row)
      require(pkVal != null) { "@PrimaryKey cannot be null." }

      val (colNames: List<String>, colValues: List<Any>) = reflectToCols(row, excludeCol = pkCol)
      val setClauses = colNames.joinToString { colName -> "$colName = ?" }

      val sql = """
        UPDATE ${inferTable(row::class)} 
        SET $setClauses
        WHERE $pkCol = ?
        RETURNING *
      """
      return Resql.get(row::class, sql, colValues + pkVal)
    }

    /**
     * Upsert this row reflectively by way of INSERT ON CONFLICT DO UPDATE.
     * Null values are excluded from the SQL.
     *
     * Requires one property/column to be annotated @UniqueKey.
     *
     * @return the upserted row
     */
    fun <T : Any> upsert(row: T): T {
      val (uniqCol, _) = getAnnotatedProperty<UniqueKey>(row)
      val (colNames: List<String>, colValues: List<Any>) = reflectToCols(row)
      val setClauses = colNames.joinToString { colName -> "$colName = EXCLUDED.$colName" }

      val sql = """
        INSERT INTO ${inferTable(row::class)}(${colNames.joinToString()})
        VALUES (${placeholders(colNames.size)})
        ON CONFLICT ($uniqCol)
        DO UPDATE SET $setClauses
        RETURNING *
      """
      return Resql.get(row::class, sql, colValues)
    }

    /**
     * snake_case the data class's simple name as the corresponding table name
     */
    private fun inferTable(type: KClass<out Any>): String {
      return ResqlStrings.camel2Snake(type.simpleName!!)
    }

    /**
     * @return (col name, col value)
     */
    private inline fun <reified T : Annotation> getAnnotatedProperty(row: Any): Pair<String, Any?> {
      // No composite PKs supported for now.
      val pkProp: KProperty1<out Any, Any?> = row::class.declaredMemberProperties.find { prop ->
        prop.findAnnotation<T>() != null
      }
        ?: throw ResqlException(500, "Failed to find @${T::class.simpleName} on ${row::class.simpleName}")

      val column: String = ResqlStrings.camel2Snake(pkProp.name)
      val value: Any? = pkProp.getter.call(row)
      return Pair(column, value)
    }

    /**
     * Filters out null values.
     *
     * @return (col names, col values)
     */
    private fun reflectToCols(row: Any, excludeCol: String? = null): Pair<List<String>, List<Any>> {
      val colNames = mutableListOf<String>()
      val colValues = mutableListOf<Any>()
      row::class.declaredMemberProperties.forEach { prop ->
        val colName = ResqlStrings.camel2Snake(prop.name)
        val colVal = prop.getter.call(row)
        if (colVal != null && colName != excludeCol) {
          colNames += colName
          colValues += colVal
        }
      }
      return Pair(colNames, colValues)
    }

    /**
     * @return "?, ?, ..., ?"
     */
    private fun placeholders(num: Int): String {
      return "?, ".repeat(num - 1) + "?"
    }
  }

}
