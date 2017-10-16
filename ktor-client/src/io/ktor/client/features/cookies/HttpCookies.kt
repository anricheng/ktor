package io.ktor.client.features.cookies

import io.ktor.client.features.HttpClientFeature
import io.ktor.client.features.feature
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.header
import io.ktor.client.request.host
import io.ktor.client.response.HttpResponsePipeline
import io.ktor.client.response.cookies
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.renderSetCookieHeader
import io.ktor.util.AttributeKey
import io.ktor.util.safeAs


class HttpCookies(private val storage: CookiesStorage) {

    operator fun get(host: String): Map<String, Cookie>? = storage[host]

    operator fun get(host: String, name: String): Cookie? = storage[host, name]

    fun forEach(host: String, block: (Cookie) -> Unit) = storage.forEach(host, block)

    class Configuration {
        private val defaults = mutableListOf<CookiesStorage.() -> Unit>()

        var storage: CookiesStorage = AcceptAllCookiesStorage()

        fun default(block: CookiesStorage.() -> Unit) {
            defaults.add(block)
        }

        fun build(): HttpCookies {
            defaults.forEach { storage.apply(it) }
            return HttpCookies(storage)
        }
    }

    companion object Feature : HttpClientFeature<Configuration, HttpCookies> {
        override fun prepare(block: Configuration.() -> Unit): HttpCookies = Configuration().apply(block).build()

        override val key: AttributeKey<HttpCookies> = AttributeKey("HttpCookies")

        override fun install(feature: HttpCookies, scope: HttpClient) {

            scope.requestPipeline.intercept(HttpRequestPipeline.State) { requestData ->
                val request = requestData.safeAs<HttpRequestBuilder>() ?: return@intercept
                feature.forEach(request.host) {
                    request.header(HttpHeaders.Cookie, renderSetCookieHeader(it))
                }
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.State) { (_, request, response) ->
                response.cookies().forEach {
                    feature.storage[request.host] = it
                }
            }
        }
    }
}

fun HttpClient.cookies(host: String): Map<String, Cookie> = feature(HttpCookies)?.get(host) ?: mapOf()
