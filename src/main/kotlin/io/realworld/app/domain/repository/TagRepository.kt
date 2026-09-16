package io.realworld.app.domain.repository

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

internal object Tags : LongIdTable() {
    val name: Column<String> = varchar("name", 100).uniqueIndex()

    /** Ids for [names], inserting any missing tag. Call inside an open transaction. */
    fun idsFor(names: Collection<String>): List<EntityID<Long>> =
        names.map { tagName ->
            select { name eq tagName }.singleOrNull()?.get(id)
                ?: insertAndGetId { it[name] = tagName }
        }
}

class TagRepository {
    init {
        transaction {
            SchemaUtils.create(Tags)
        }
    }

    fun findAll(): List<String> = transaction {
        Tags.selectAll().map { it[Tags.name] }
    }
}
