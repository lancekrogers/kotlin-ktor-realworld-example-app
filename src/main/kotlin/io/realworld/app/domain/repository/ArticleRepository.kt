package io.realworld.app.domain.repository

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction

internal object Articles : LongIdTable() {
    val slug: Column<String> = varchar("slug", 255).uniqueIndex()
    val title: Column<String> = varchar("title", 255)
    val description: Column<String> = varchar("description", 1000)
    val body: Column<String> = text("body")
    val author: Column<EntityID<Long>> = reference("author", Users)
    val createdAt: Column<Long> = long("created_at")
    val updatedAt: Column<Long> = long("updated_at")
}

internal object ArticleTags : Table() {
    val article: Column<EntityID<Long>> = reference("article", Articles)
    val tag: Column<EntityID<Long>> = reference("tag", Tags)
    override val primaryKey = PrimaryKey(article, tag)
}

class ArticleRepository {
    init {
        transaction {
            // One call: SchemaUtils sorts by foreign-key references and skips existing tables,
            // so this is safe no matter which repository Kodein constructs first.
            SchemaUtils.create(Users, Tags, Articles, ArticleTags)
        }
    }
}
