package com.rokoc.blockslide.data

import android.content.Context
import com.rokoc.blockslide.R
import com.rokoc.blockslide.core.Level

class LevelRepository(
    private val context: Context,
) {
    fun loadLevels(): List<Level> {
        val json = context.resources.openRawResource(R.raw.levels).bufferedReader().use { it.readText() }
        return LevelPackParser.parse(json)
    }
}
