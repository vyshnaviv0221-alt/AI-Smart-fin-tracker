package com.example.aismartexpensetracker.network

import com.example.aismartexpensetracker.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    /**
     * Set with `server.baseUrl` in client/local.properties; defaults to
     * http://127.0.0.1:8000/.
     *
     *   REAL PHONE  -> http://127.0.0.1:8000/ plus `adb reverse tcp:8000 tcp:8000`,
     *                  which tunnels the phone's localhost to the laptop over USB.
     *                  This needs no Wi-Fi and is immune to networks that block
     *                  client-to-client traffic, so it is the reliable demo path.
     *   EMULATOR    -> http://10.0.2.2:8000/  (the emulator's alias for the host)
     *   SAME WI-FI  -> http://<laptop-LAN-IP>:8000/, with the server started as
     *                  `uvicorn app.main:app --host 0.0.0.0 --port 8000`
     *
     * Cleartext HTTP is permitted only for these local hosts -- see
     * res/xml/network_security_config.xml.
     */
    const val BASE_URL: String = BuildConfig.ML_SERVER_URL

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
