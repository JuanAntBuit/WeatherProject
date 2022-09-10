package com.juanantbuit.weatherproject.data.datasources.openweather

import com.juanantbuit.weatherproject.domain.models.CityInfoModel
import com.juanantbuit.weatherproject.domain.models.ForecastResponseModel
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiClient {

    @GET("/data/2.5/weather")
    suspend fun getCityInfo(@Query("lat") latitude: Double?,
                            @Query("lon") longitude: Double?,
                            @Query("appid") apiKey: String,
                            @Query("units") units: String)
                            :Response<CityInfoModel>

    @GET("/data/2.5/forecast")
    suspend fun getForecastResponse(@Query("lat") latitude: Double?,
                                    @Query("lon") longitude: Double?,
                                    @Query("appid") apiKey: String,
                                    @Query("units") units: String)
                                :Response<ForecastResponseModel>

}