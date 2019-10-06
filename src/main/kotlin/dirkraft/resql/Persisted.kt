package dirkraft.resql

/**
 * A data class can extend this directly to get the corresponding auto-dao methods.
 */
@Suppress("UNCHECKED_CAST")
interface Persisted<T> where T : Persisted<T> {
  fun insert(): T = AutoDao.insert(this as T)
  fun update(): T = AutoDao.update(this as T)
  fun upsert(): T = AutoDao.upsert(this as T)
  fun delete() = AutoDao.delete(this as T)
}