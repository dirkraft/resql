package dirkraft.resql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import javax.sql.DataSource

class ResqlTest : TestInstance() {
  @Test
  fun javaTimeDuration() {
    Resql.exec("CREATE TABLE java_time_duration (ts TIMESTAMPTZ)")

    Resql.exec(
      "INSERT INTO java_time_duration VALUES (now() - ?)",
      Duration.ofHours(3)
    )

    val inDb = Resql.get("SELECT * FROM java_time_duration") {
      it.instant("ts")
    }

    assertTrue(Instant.now().minus(Duration.ofHours(2)) > inDb)
  }

  @Test
  fun testTransformNullEquals() {
    val resql = object : Resql() {
      override val dataSource: DataSource
        get() {
          val cfg = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
            username = System.getProperty("user.name")
            password = ""

            connectionInitSql = "set transform_null_equals = on"
          }
          return HikariDataSource(cfg)
        }
    }
    assertEquals("on", resql.get("show transform_null_equals") { it.string(1) })
    assertTrue(resql.get("select null = null") { it.boolean(1) })
    assertTrue(resql.get("select null = ?::text", listOf(null)) { it.boolean(1) })

    resql.exec("create table null_equality(data text)")
    resql.exec("insert into null_equality values(null)")
    resql.exec("insert into null_equality values('hi')")

    fun doCount(where: String, vararg args: Any?): Int {
      return resql.get("select count(*) from null_equality $where", args.toList()) { it.int(1) }
    }

    assertEquals(1, doCount("where data = ?", "hi"))
    assertEquals(1, doCount("where data = null"))
    // transform_null_equals only works when null literally appears somewhere in the expression.
    // Leaving this here as a reminder.
    // https://www.postgresql.org/docs/10/runtime-config-compatible.html#RUNTIME-CONFIG-COMPATIBLE-CLIENTS
    assertEquals(0, doCount("where data = ?", null))
  }
}