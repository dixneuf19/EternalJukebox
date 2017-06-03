package org.abimon.eternalJukebox

import io.netty.handler.codec.http.HttpHeaderNames
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.abimon.visi.security.sha512Hash
import java.io.File
import java.io.FileInputStream
import java.util.*

fun HttpServerResponse.redirect(url: String) = putHeader("Location", url).setStatusCode(302).end()
fun Router.htmlRoute(optionalEndpoint: Optional<String>, file: String) = optionalEndpoint.ifPresent { endpoint -> this.route(endpoint).handler { context -> context.ifFileNotCached(File(file)) { response().httpsOnly().htmlContent().sendCachedFile(it) } } }
fun Router.popularHtmlRoute(optionalEndpoint: Optional<String>, file: String, jukebox: Boolean) = optionalEndpoint.ifPresent { endpoint ->
    this.route(endpoint).handler { context ->
        context.ifFileNotCached(File(file)) { response().httpsOnly().htmlContent().sendCachedFile(it) }
        if (jukebox)
            populariseJukebox(context)
        else
            populariseCanonizer(context)
    }
}

fun HttpServerResponse.optionalHeader(headerName: CharSequence, headerValue: CharSequence, addHeader: Boolean): HttpServerResponse {
    if(addHeader)
        putHeader(headerName, headerValue)
    return this
}
fun HttpServerResponse.optionalHeader(headerName: CharSequence, headerValue: CharSequence, addHeader: () -> Boolean): HttpServerResponse = optionalHeader(headerName, headerValue, addHeader())
fun HttpServerResponse.httpsOnly(): HttpServerResponse = this.optionalHeader("Strict-Transport-Security", "max-age=86400", config.enforceHttps)
fun HttpServerResponse.cache(): HttpServerResponse = this.optionalHeader("Cache-Control", "max-age=86400, public", config.cacheFiles)
fun HttpServerResponse.sendCachedFile(file: File): HttpServerResponse {
    if(config.cacheFiles)
        return this.cache().putHeader("ETag", FileInputStream(file).sha512Hash()).sendFile(file.absolutePath)
    return this.sendFile(file.absolutePath)
}
fun HttpServerResponse.sendCachedData(data: String) {
    if(config.cacheFiles)
        return this.cache().putHeader("ETag", data.sha512Hash()).end(data)
    this.end(data)
}

fun HttpServerResponse.htmlContent(): HttpServerResponse = this.putHeader(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=UTF-8")
fun HttpServerResponse.jsonContent(): HttpServerResponse = this.putHeader(HttpHeaderNames.CONTENT_TYPE, "application/json;charset=UTF-8")

fun RoutingContext.ifFileNotCached(file: File, wasntCached: RoutingContext.(File) -> Unit) {
    if (config.cacheFiles) {
        val hashOnDisk = FileInputStream(file).sha512Hash()
        val hashProvided = this.request().getHeader("If-None-Match") ?: ""

        if(hashOnDisk != hashProvided)
            wasntCached(file)
        else
            response().setStatusCode(304).end()
    } else
        wasntCached(file)
}
fun RoutingContext.ifDataNotCached(data: String, wasntCached: RoutingContext.(String) -> Unit) {
    if (config.cacheFiles) {
        val hashOnDisk = data.sha512Hash()
        val hashProvided = this.request().getHeader("ETag") ?: ""

        if(hashOnDisk != hashProvided)
            wasntCached(data)
        else
            response().setStatusCode(304).end()
    } else
        wasntCached(data)
}

fun Any.returnUnit(): Unit {}