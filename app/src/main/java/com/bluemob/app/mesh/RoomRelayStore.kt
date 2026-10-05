package com.bluemob.app.mesh

import com.bluemob.app.data.RelayDao
import com.bluemob.app.data.RelayRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Keeps carried messages in memory for the router, and writes them to the database so they survive a restart. */
class RoomRelayStore(private val dao: RelayDao, private val scope: CoroutineScope) : RelayStore {
    private val mem = LinkedHashMap<String, CarriedItem>()

    init {
        scope.launch {
            dao.all().forEach { r ->
                if (!mem.containsKey(r.key)) mem[r.key] = CarriedItem(r.key, r.toNode, r.origin, r.packet, r.copies,
                    r.givenTo.split(",").filter { it.isNotBlank() }.toSet(), r.expiresAt, r.receivedAt)
            }
        }
    }

    override fun all(): List<CarriedItem> = mem.values.toList()

    override fun put(item: CarriedItem) {
        mem[item.key] = item
        scope.launch { dao.put(RelayRow(item.key, item.to, item.origin, item.packet, item.copies, item.givenTo.joinToString(","), item.expiresAt, item.receivedAt)) }
    }

    override fun remove(key: String) {
        if (mem.remove(key) != null) scope.launch { dao.remove(key) }
    }
}
