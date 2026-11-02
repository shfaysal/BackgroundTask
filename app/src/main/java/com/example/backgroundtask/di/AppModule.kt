package com.example.backgroundtask.di

import com.example.backgroundtask.data.repository.TripRepository
import com.example.backgroundtask.data.repository.TripRepositoryImpl
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTripRepository(impl: TripRepositoryImpl): TripRepository
}


@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun providePusher(): Pusher {
        val options = PusherOptions().apply {
            setCluster("") // e.g., "ap2" or "mt1"
        }
        return Pusher("", options)
    }
}
