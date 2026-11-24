package com.calcsuits.di

import android.content.Context
import androidx.room.Room
import com.calcsuits.data.local.room.CalculatorDatabase
import com.calcsuits.data.local.room.HistoryDao
import com.calcsuits.data.remote.api.ExchangeRateApi
import com.calcsuits.data.remote.repository.CurrencyRepositoryImpl
import com.calcsuits.data.repository.CalculatorRepositoryImpl
import com.calcsuits.domain.repository.CalculatorRepository
import com.calcsuits.domain.repository.CurrencyRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

    companion object {
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
        fun provideExchangeRateApi(): ExchangeRateApi {
            return Retrofit.Builder()
                .baseUrl(ExchangeRateApi.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ExchangeRateApi::class.java)
        }
    }
}
