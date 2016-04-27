package com.wix.pay.paybox.testkit


import java.io.ByteArrayOutputStream
import java.util.{List => JList}

import com.google.api.client.http.{UrlEncodedContent, UrlEncodedParser}
import com.wix.hoopoe.http.testkit.EmbeddedHttpProbe
import spray.http._

import scala.collection.JavaConversions._
import scala.collection.mutable

class PayboxDriver(port: Int) {
  val probe = new EmbeddedHttpProbe(port, EmbeddedHttpProbe.NotFoundHandler)

  def startProbe() {
    probe.doStart()
  }

  def stopProbe() {
    probe.doStop()
  }

  def resetProbe() {
    probe.handlers.clear()
  }

  def aRequestFor(params: Map[String, Option[String]]): RequestCtx = {
    new RequestCtx(params)
  }

  class RequestCtx(params: Map[String, Option[String]]) {
    def returns(responseParams: Map[String, String]) {
      probe.handlers += {
        case HttpRequest(
        HttpMethods.POST,
        Uri.Path("/"),
        _,
        entity,
        _) if isStubbedRequestEntity(entity) =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`), urlEncode(responseParams)))
      }
    }

    private def isStubbedRequestEntity(entity: HttpEntity): Boolean = {
      val requestParams = urlDecode(entity.asString)

      params.forall {
        case (k, v) => requestParams.contains(k) && v.fold(true)(_ == requestParams(k))
      }
    }

    private def urlEncode(params: Map[String, String]): String = {
      val baos = new ByteArrayOutputStream()
      new UrlEncodedContent(mapAsJavaMap(params)).writeTo(baos)
      new String(baos.toByteArray, "UTF-8")
    }

    private def urlDecode(str: String): Map[String, String] = {
      val params = mutable.LinkedHashMap[String, JList[String]]()
      UrlEncodedParser.parse(str, mutableMapAsJavaMap(params))
      params.mapValues( _(0) ).toMap
    }
  }
}
