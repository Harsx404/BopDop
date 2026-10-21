package com.dopdop.bopdop.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BackupTest {
    private fun file(version: Int = Backup.VERSION) = JSONObject()
        .put("format", "bopdop-backup").put("version", version).put("createdAt", 5L)
        .put("library", JSONObject().put("liked", JSONArray().put(JSONObject()).put(JSONObject())).put("playlists", JSONArray().put(JSONObject())))
        .put("plays", JSONObject().put("events", JSONArray().put(JSONArray()).put(JSONArray()).put(JSONArray())))

    @Test fun acceptsOwnBackupsOnly() {
        assertNotNull(Backup.parse(file().toString()))
        assertNull(Backup.parse("{\"liked\":[]}"))
        assertNull(Backup.parse("not json"))
        assertNull(Backup.parse(file(version = Backup.VERSION + 1).toString()))
    }

    @Test fun summarisesBeforeRestoring() {
        assertEquals(BackupSummary(5L, liked = 2, playlists = 1, plays = 3), Backup.summary(file()))
    }
}
