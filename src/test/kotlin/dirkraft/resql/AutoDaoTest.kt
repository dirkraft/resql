package dirkraft.resql

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class AutoDaoTest : AutoDao<Thing> by AutoDao.self(), TestInstance() {
  @Test
  fun testDuration() {
    Resql.exec("CREATE TABLE thing (duration INTERVAL)")

    AutoDao.insert(Thing(
      duration = Duration.ofMinutes(15)
    ))

    val rows = list()
    assertEquals(1, rows.size)
    assertEquals(Duration.ofMinutes(15), rows.first().duration)
  }
}

data class Thing(
  var duration: Duration
)