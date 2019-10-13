package dirkraft.resql

import org.junit.Assert
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ResqlTest : TestInstance() {
  @Test
  fun javaTimeDuration() {
    Resql.exec("CREATE TABLE java_time_duration (ts TIMESTAMPTZ)")

    Resql.exec(
      "INSERT INTO java_time_duration VALUES (now() - ?::INTERVAL)",
      Duration.ofHours(3)
    )

    val inDb = Resql.get("SELECT * FROM java_time_duration") {
      it.instant("ts")
    }

    Assert.assertTrue(Instant.now().minus(Duration.ofHours(2)) > inDb)
  }
}