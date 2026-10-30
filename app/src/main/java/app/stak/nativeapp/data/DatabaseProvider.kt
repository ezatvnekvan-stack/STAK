package app.stak.nativeapp.data

import android.content.Context
import androidx.room.Room
import app.stak.nativeapp.data.db.AppDatabase

object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "stak.db").addMigrations(LIBRARY_JOURNAL_MIGRATION).build().also { instance = it }
        }
}
val LIBRARY_JOURNAL_MIGRATION=object:androidx.room.migration.Migration(3,4) {
    override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE watchlist_new (imdbId TEXT NOT NULL,type TEXT NOT NULL,name TEXT NOT NULL,year TEXT,poster TEXT,progress REAL NOT NULL,addedAt INTEGER NOT NULL DEFAULT 0,mediaJson TEXT,PRIMARY KEY(imdbId,type))")
        db.execSQL("INSERT INTO watchlist_new (imdbId,type,name,year,poster,progress,addedAt) SELECT imdbId,type,name,year,poster,progress,rowid FROM watchlist")
        db.execSQL("DROP TABLE watchlist")
        db.execSQL("ALTER TABLE watchlist_new RENAME TO watchlist")
        db.execSQL("CREATE TABLE tracking_new (id TEXT NOT NULL,type TEXT NOT NULL,name TEXT NOT NULL,played INTEGER NOT NULL,progress REAL NOT NULL,lastPlayed INTEGER,mediaJson TEXT,scope TEXT NOT NULL DEFAULT 'local',updatedAt INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(id,scope))")
        db.execSQL("INSERT INTO tracking_new (id,type,name,played,progress,lastPlayed,updatedAt) SELECT id,type,name,played,progress,lastPlayed,COALESCE(lastPlayed,0) FROM tracking")
        db.execSQL("DROP TABLE tracking")
        db.execSQL("ALTER TABLE tracking_new RENAME TO tracking")
    }
}
