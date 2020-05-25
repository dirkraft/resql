package dirkraft.resql

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.HikariCP
import java.io.BufferedReader
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

class AutoMigrator(private val prompting: Boolean = true) {

  private val dataSource = HikariCP.dataSource()
  private val existingTables: Map<String, Table> = dataSource.connection.use { conn ->
    val tableMeta = conn.metaData.getTables(null, null, null, arrayOf("TABLE"))
    val tableNames = mutableListOf<String>()
    while (tableMeta.next()) {
      tableNames.add(tableMeta.getString("table_name"))
    }
    tableNames.map { tableName ->
      val pkMeta = conn.metaData.getPrimaryKeys(null, null, tableName)
      val pks = mutableListOf<String>()
      while (pkMeta.next()) {
        pks.add(pkMeta.getString("column_name"))
      }

      val colMeta = conn.metaData.getColumns(null, null, tableName, null)
      val cols = mutableListOf<ColSql>()
      while (colMeta.next()) {
        val colName = colMeta.getString("column_name")
        cols.add(ColSql(
          name = colName,
          type = when (val typeName = colMeta.getString("type_name")) {
            "varchar" -> "varchar(${colMeta.getInt("column_size")})"
            "int8" -> "bigint"
            else -> typeName
          },
          nullable = !pks.contains(colName) && colMeta.getBoolean("nullable"),
          pk = pks.contains(colName)
        ))
      }

      tableName to Table(
        name = tableName,
        cols = cols
      )
    }
  }.toMap()

  inline fun <reified T : Any> sync() = sync(T::class)

  fun <T : Any> sync(klass: KClass<T>) {
    val table = inferTable(klass, quoted = false)
    val cols = sortColsByConstructor(klass, reflectToCols(klass)).map(::toSql)
    val sql = diff(existingTables[table], Table(table, cols))
    if (sql.isNotEmpty()) {
      println(sql)
      if (prompting) {
        print("Migrate? ")
        val isr = BufferedReader(System.`in`.reader())
        if (isr.readLine().trim() != "y") {
          return
        }
      }

      dataSource.connection.use { conn ->
        val st = conn.createStatement()
        st.execute(sql)
      }
      println("Migration applied.")
    }
  }

  private fun toSql(col: Col): ColSql {
    val prop = col.prop!!
    val pkAnnot: PrimaryKey? = prop.findAnnotation()

    val type = when (val classish: KClassifier = prop.returnType.classifier!!) {
      String::class -> col.prop.findAnnotation<Varchar>()
        ?.let { "varchar(${it.limit})" }
        ?: "text"
      Char::class -> "char"
      Long::class -> pkAnnot?.let { "bigserial" } ?: "bigint"
      Int::class -> pkAnnot?.let { "serial" } ?: "int"
      Boolean::class -> "bool"
      Instant::class -> TODO()
      Duration::class -> TODO()
      MutableList::class -> TODO()
      List::class -> TODO()
      MutableSet::class -> TODO()
      Set::class -> TODO()
      JsonNode::class -> "jsonb"
      else -> throw ResqlException(500, "Don't know how to map into param type $classish")
    }
    return ColSql(
      name = col.name,
      type = type,
      nullable = pkAnnot == null && prop.returnType.isMarkedNullable,
      pk = pkAnnot != null
    )
  }

  private fun sortColsByConstructor(klass: KClass<out Any>, cols: List<Col>): List<Col> {
    val byName: Map<String, Col> = cols.map { it.name to it }.toMap()
    return klass.primaryConstructor!!.parameters.map { param ->
      val colName = ResqlStrings.camel2Snake(param.name!!)
      byName.getValue(colName)
    }
  }

  /** Generate SQL to bring DB in line with klass */
  fun diff(existingTable: Table?, wantedTable: Table): String {
    require(existingTable == null || existingTable.name == wantedTable.name)

    return if (existingTable == null) {
      val colSql = wantedTable.cols.joinToString(",\n  ")
      """create table ${wantedTable.quotedName}(
        |  $colSql
        |);""".trimMargin()

    } else {
      val statements = mutableListOf<String>()
      val existingCols: Map<String, ColSql> = existingTable.cols.map { it.name to it }.toMap()
      val wantedCols: Map<String, ColSql> = wantedTable.cols.map { it.name to it }.toMap()

      // removed (but auto-mig will never do it)
      val removedCols: Set<String> = existingCols.keys - wantedCols.keys
      removedCols.mapTo(statements) { "-- alter table ${existingTable.quotedName} drop ${it};" }

      val addedCols: Collection<ColSql> =
        wantedCols.filterKeys { !existingCols.containsKey(it) }.values
      addedCols.mapTo(statements) { col -> "alter table ${existingTable.quotedName} add $col;" }

      val inBoth = existingCols.keys.intersect(wantedCols.keys).map { k ->
        existingCols.getValue(k) to wantedCols.getValue(k)
      }
      inBoth.filter { (l, r) -> l.type != r.type }.mapTo(statements) { (_, r) ->
        "alter table ${existingTable.quotedName} alter ${r.name} type ${r.type};"
      }
      inBoth.filter { (l, r) -> l.nullable != r.nullable }.mapTo(statements) { (_, r) ->
        val nullability = if (r.nullable) "null" else "not null"
        "alter table ${existingTable.quotedName} alter ${r.name} set $nullability;"
      }
      inBoth.filter { (l, r) -> l.pk != r.pk }.mapTo(statements) { (l, r) ->
        "-- ${existingTable.quotedName}.${r.name} primary key from ${l.pk} to ${r.pk};"
      }
      // renames not supported

      statements.joinToString("\n")
    }
  }

  data class Table(
    val name: String,
    val cols: List<ColSql>
  ) {
    val quotedName get() = '"' + name + '"'
  }
}