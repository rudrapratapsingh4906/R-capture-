package com.rcapture.data

import com.rcapture.intelligence.ExtractedEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object EntityJsonAdapter {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, ExtractedEntity::class.java)
    private val adapter = moshi.adapter<List<ExtractedEntity>>(listType)

    fun toJson(entities: List<ExtractedEntity>): String {
        return adapter.toJson(entities)
    }

    fun fromJson(json: String?): List<ExtractedEntity> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
