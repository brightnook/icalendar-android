package com.brightnook.nookcalendar.caldav

import okhttp3.Interceptor
import okhttp3.Response

class SensitiveLoggingInterceptor(
    private val sink: (String) -> Unit = {},
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        sink("${request.method} ${url.scheme}://${url.host}")
        return chain.proceed(request)
    }
}
