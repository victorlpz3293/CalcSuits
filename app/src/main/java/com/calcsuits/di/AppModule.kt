package com.calcsuits.di

import android.content.Context
import androidx.room.Room
import com.calcsuits.data.local.room.CalculatorDatabase
import com.calcsuits.data.local.room.HistoryDao
import com.calcsuits.data.remote.api.ExchangeRateApi
import com.calcsuits.data.remote.cache.CurrencyRatesCache
import com.calcsuits.data.remote.cache.CurrencyRatesDataStore
import com.calcsuits.data.remote.repository.CurrencyRepositoryImpl
import com.calcsuits.data.repository.CalculatorRepositoryImpl
import com.calcsuits.domain.repository.CalculatorRepository
import com.calcsuits.domain.repository.CurrencyRepository
import com.google.gson.Gson
import dagger.Binds
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
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindCalculatorRepository(
        calculatorRepositoryImpl: CalculatorRepositoryImpl
    ): CalculatorRepository

    @Binds
    @Singleton
    abstract fun bindCurrencyRepository(
        currencyRepositoryImpl: CurrencyRepositoryImpl
    ): CurrencyRepository

    @Binds
    @Singleton
    abstract fun bindCurrencyRatesCache(
        currencyRatesDataStore: CurrencyRatesDataStore
    ): CurrencyRatesCache

    companion object {
        private const val NETWORK_TIMEOUT_SECONDS = 10L
        private const val CALL_TIMEOUT_SECONDS = 15L

        @Provides
        @Singleton
        fun provideCalculatorDatabase(@ApplicationContext context: Context): CalculatorDatabase {
            return Room.databaseBuilder(
                context,
                CalculatorDatabase::class.java,
                "calcsuits_db"
            ).build()
        }

        @Provides
        fun provideHistoryDao(database: CalculatorDatabase): HistoryDao {
            return database.historyDao
        }

        @Provides
        @Singleton
        fun provideGson(): Gson = Gson()

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }

        @Provides
        @Singleton
        fun provideExchangeRateApi(
            okHttpClient: OkHttpClient,
            gson: Gson
        ): ExchangeRateApi {
            return Retrofit.Builder()
                .baseUrl(ExchangeRateApi.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(ExchangeRateApi::class.java)
        }
    }
}
