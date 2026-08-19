package com.pockethost.app.di

import android.content.Context
import androidx.room.Room
import com.pockethost.app.data.api.MojangApiService
import com.pockethost.app.data.db.VersionDao
import com.pockethost.app.data.db.VersionDatabase
import com.pockethost.app.data.repository.ServerConfigRepository
import com.pockethost.app.data.repository.VersionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Provides
    @Singleton
    fun provideServerConfigRepository(
        @ApplicationContext context: Context
    ): ServerConfigRepository = ServerConfigRepository(context)

    @Provides
    @Singleton
    fun provideVersionDatabase(
        @ApplicationContext context: Context
    ): VersionDatabase = Room.databaseBuilder(
        context,
        VersionDatabase::class.java,
        "pocketcraft_versions.db"
    ).fallbackToDestructiveMigration().build()

    @Provides
    @Singleton
    fun provideVersionDao(db: VersionDatabase): VersionDao = db.versionDao()

    @Provides
    @Singleton
    fun provideMojangApiService(okHttpClient: OkHttpClient): MojangApiService =
        Retrofit.Builder()
            .baseUrl("https://launchermeta.mojang.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MojangApiService::class.java)

    @Provides
    @Singleton
    fun provideVersionRepository(
        @ApplicationContext context: Context,
        mojangApiService: MojangApiService,
        versionDao: VersionDao,
        okHttpClient: OkHttpClient
    ): VersionRepository = VersionRepository(context, mojangApiService, versionDao, okHttpClient)
}
