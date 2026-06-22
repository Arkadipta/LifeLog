package com.lifelog.app.di

import android.content.Context
import androidx.room.Room
import com.lifelog.app.data.db.LifeLogDatabase
import com.lifelog.app.data.db.dao.ChartConfigDao
import com.lifelog.app.data.db.dao.EventEntryDao
import com.lifelog.app.data.db.dao.EventFieldDao
import com.lifelog.app.data.db.dao.EventTypeDao
import com.lifelog.app.data.db.dao.ReminderDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LifeLogDatabase =
        Room.databaseBuilder(context, LifeLogDatabase::class.java, LifeLogDatabase.DATABASE_NAME)
            .build()

    @Provides fun provideEventTypeDao(db: LifeLogDatabase): EventTypeDao = db.eventTypeDao()
    @Provides fun provideEventFieldDao(db: LifeLogDatabase): EventFieldDao = db.eventFieldDao()
    @Provides fun provideEventEntryDao(db: LifeLogDatabase): EventEntryDao = db.eventEntryDao()
    @Provides fun provideReminderDao(db: LifeLogDatabase): ReminderDao = db.reminderDao()
    @Provides fun provideChartConfigDao(db: LifeLogDatabase): ChartConfigDao = db.chartConfigDao()
}
