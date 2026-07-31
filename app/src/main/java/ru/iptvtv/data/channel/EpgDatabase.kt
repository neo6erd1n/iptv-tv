package ru.iptvtv.data.channel

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import ru.iptvtv.domain.model.Program

internal class EpgDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE metadata (
                source TEXT PRIMARY KEY,
                updated_at INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE aliases (
                source TEXT NOT NULL,
                alias TEXT NOT NULL,
                channel_id TEXT NOT NULL,
                PRIMARY KEY (source, alias)
            ) WITHOUT ROWID""",
        )
        db.execSQL(
            """CREATE TABLE programs (
                source TEXT NOT NULL,
                channel_id TEXT NOT NULL,
                start INTEGER NOT NULL,
                end INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                PRIMARY KEY (source, channel_id, start)
            ) WITHOUT ROWID""",
        )
        db.execSQL(
            "CREATE INDEX programs_time ON programs(source, start, end)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS programs")
        db.execSQL("DROP TABLE IF EXISTS aliases")
        db.execSQL("DROP TABLE IF EXISTS metadata")
        onCreate(db)
    }

    fun lastUpdatedAt(source: String): Long? =
        readableDatabase.rawQuery(
            "SELECT updated_at FROM metadata WHERE source = ?",
            arrayOf(source),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }

    fun currentPrograms(source: String, now: Long): Map<String, Program> {
        val result = mutableMapOf<String, Program>()
        readableDatabase.rawQuery(
            """SELECT a.alias, p.title, p.description, p.start, p.end
               FROM programs p
               JOIN aliases a
                 ON a.source = p.source AND a.channel_id = p.channel_id
               WHERE p.source = ? AND p.start <= ? AND p.end > ?""",
            arrayOf(source, now.toString(), now.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getString(0)] = Program(
                    title = cursor.getString(1),
                    description = cursor.getString(2),
                    start = cursor.getLong(3),
                    end = cursor.getLong(4),
                )
            }
        }
        return result
    }

    fun programsForChannel(
        source: String,
        alias: String,
        archiveStart: Long,
        now: Long,
    ): List<Program> =
        readableDatabase.rawQuery(
            """SELECT p.title, p.description, p.start, p.end
               FROM programs p
               JOIN aliases a
                 ON a.source = p.source AND a.channel_id = p.channel_id
               WHERE p.source = ? AND a.alias = ? AND p.start >= ? AND p.start <= ?
               ORDER BY p.start DESC""",
            arrayOf(source, alias, archiveStart.toString(), now.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Program(
                            title = cursor.getString(0),
                            description = cursor.getString(1),
                            start = cursor.getLong(2),
                            end = cursor.getLong(3),
                        ),
                    )
                }
            }
        }

    fun searchPrograms(
        source: String,
        query: String,
        archiveStart: Long,
        now: Long,
    ): List<Pair<String, Program>> =
        readableDatabase.rawQuery(
            """SELECT a.alias, p.title, p.description, p.start, p.end
               FROM programs p
               JOIN aliases a
                 ON a.source = p.source AND a.channel_id = p.channel_id
               WHERE p.source = ? AND p.start >= ? AND p.start <= ?
                 AND p.title LIKE ? ESCAPE '\'
               ORDER BY p.start DESC
               LIMIT 100""",
            arrayOf(
                source,
                archiveStart.toString(),
                now.toString(),
                "%${query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%",
            ),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        cursor.getString(0) to Program(
                            title = cursor.getString(1),
                            description = cursor.getString(2),
                            start = cursor.getLong(3),
                            end = cursor.getLong(4),
                        ),
                    )
                }
            }
        }

    fun beginRefresh(source: String): RefreshSession {
        val db = writableDatabase
        db.beginTransaction()
        db.delete("programs", "source = ?", arrayOf(source))
        db.delete("aliases", "source = ?", arrayOf(source))
        return RefreshSession(db, source)
    }

    internal class RefreshSession(
        private val db: SQLiteDatabase,
        private val source: String,
    ) {
        private val aliasStatement: SQLiteStatement = db.compileStatement(
            "INSERT OR REPLACE INTO aliases(source, alias, channel_id) VALUES (?, ?, ?)",
        )
        private val programStatement: SQLiteStatement = db.compileStatement(
            """INSERT OR REPLACE INTO programs
               (source, channel_id, start, end, title, description)
               VALUES (?, ?, ?, ?, ?, ?)""",
        )
        fun insertAlias(alias: String, channelId: String) {
            aliasStatement.clearBindings()
            aliasStatement.bindString(1, source)
            aliasStatement.bindString(2, alias)
            aliasStatement.bindString(3, channelId)
            aliasStatement.executeInsert()
        }

        fun insertProgram(
            channelId: String,
            title: String,
            description: String,
            start: Long,
            end: Long,
        ) {
            programStatement.clearBindings()
            programStatement.bindString(1, source)
            programStatement.bindString(2, channelId)
            programStatement.bindLong(3, start)
            programStatement.bindLong(4, end)
            programStatement.bindString(5, title)
            programStatement.bindString(6, description)
            programStatement.executeInsert()
        }

        fun commit() {
            db.execSQL(
                "INSERT OR REPLACE INTO metadata(source, updated_at) VALUES (?, ?)",
                arrayOf<Any>(source, System.currentTimeMillis()),
            )
            db.setTransactionSuccessful()
            close()
        }

        fun rollback() {
            close()
        }

        private fun close() {
            aliasStatement.close()
            programStatement.close()
            if (db.inTransaction()) db.endTransaction()
        }
    }

    private companion object {
        const val DATABASE_NAME = "epg.db"
        const val DATABASE_VERSION = 1
    }
}
