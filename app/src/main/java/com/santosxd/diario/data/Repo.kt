package com.santosxd.diario.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

class Repo(private val db: DiaryDb) {
    private val dao = db.dao()

    val entries = dao.observeEntries()
    val activities = dao.observeActivities()

    suspend fun seedIfEmpty() {
        if (dao.activityCount() == 0) {
            listOf("Trabajo", "Estudio", "Ejercicio", "Amigos", "Familia", "Descanso", "Juegos", "Música")
                .forEach { dao.insertActivity(ActivityEntity(name = it)) }
        }
    }

    suspend fun addActivity(name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) dao.insertActivity(ActivityEntity(name = clean))
    }

    suspend fun saveEntry(mood: Int, note: String, activityIds: Set<Long>) {
        db.withTransaction {
            val id = dao.insertEntry(
                EntryEntity(
                    createdAt = System.currentTimeMillis(),
                    mood = mood,
                    note = note.trim()
                )
            )
            dao.insertRefs(activityIds.map { EntryActivityRef(id, it) })
        }
    }

    suspend fun delete(id: Long) = dao.deleteEntry(id)

    suspend fun exportJson(): String {
        val acts = dao.allActivities()
        val namesById = acts.associate { it.id to it.name }
        val root = JSONObject()
        root.put("version", 1)
        root.put("activities", JSONArray(acts.map { it.name }))
        val arr = JSONArray()
        for (e in dao.allEntries()) {
            val o = JSONObject()
            o.put("createdAt", e.entry.createdAt)
            o.put("mood", e.entry.mood)
            o.put("note", e.entry.note)
            o.put("activities", JSONArray(e.activities.mapNotNull { namesById[it.id] }))
            arr.put(o)
        }
        root.put("entries", arr)
        return root.toString()
    }

    suspend fun importJson(json: String) {
        val root = JSONObject(json)
        val acts = root.getJSONArray("activities")
        val list = root.getJSONArray("entries")
        db.withTransaction {
            dao.clearEntries()
            dao.clearActivities()
            for (i in 0 until acts.length()) {
                dao.insertActivity(ActivityEntity(name = acts.getString(i)))
            }
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                val id = dao.insertEntry(
                    EntryEntity(
                        createdAt = o.getLong("createdAt"),
                        mood = o.getInt("mood"),
                        note = o.getString("note")
                    )
                )
                val names = o.getJSONArray("activities")
                val refs = ArrayList<EntryActivityRef>()
                for (j in 0 until names.length()) {
                    val actId = dao.activityIdByName(names.getString(j))
                    if (actId != null) refs.add(EntryActivityRef(id, actId))
                }
                dao.insertRefs(refs)
            }
        }
    }
}
