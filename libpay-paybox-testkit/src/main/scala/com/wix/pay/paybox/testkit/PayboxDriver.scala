package com.wix.pay.paybox.testkit


import scala.collection.JavaConversions._
import scala.collection.mutable
import java.io.ByteArrayOutputStream
import java.util.{List => JList}
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import com.google.api.client.http.{UrlEncodedContent, UrlEncodedParser}
import com.wix.e2e.http.api.StubWebServer
import com.wix.e2e.http.client.extractors.HttpMessageExtractors._
import com.wix.e2e.http.server.WebServerFactory.aStubWebServer
import com.wix.pay.creditcard.CreditCard
import com.wix.pay.model.CurrencyAmount
import com.wix.pay.paybox.PayboxHelper
import com.wix.pay.paybox.model.{ErrorCodes, Fields}


class PayboxDriver(port: Int) {
  private val server: StubWebServer = aStubWebServer.onPort(port).build

  def start(): Unit = server.start()
  def stop(): Unit = server.stop()
  def reset(): Unit = server.replaceWith()


  def anAuthorizeFor(site: String,
                     rang: String,
                     cle: String,
                     card: CreditCard,
                     currencyAmount: CurrencyAmount): RequestCtx = {
    val params = PayboxHelper.createAuthorizeRequest(
      site = site,
      rang = rang,
      cle = cle,
      card = card,
      currencyAmount = currencyAmount).map {
        case (Fields.numQuestion, _) => (Fields.numQuestion, None)
        case (Fields.reference, _) => (Fields.reference, None)
        case (field, value) => (field, Some(value))
      }

    new RequestCtx(
      site = site,
      rang = rang,
      params = params)
  }

  def aVoidAuthorizationFor(site: String,
                            rang: String,
                            cle: String,
                            numTrans: String,
                            numAppel: String,
                            numQuestion: String,
                            devise: String,
                            reference: String,
                            dateQ: String): RequestCtx = {
    val params = PayboxHelper.createCancelRequest(
      site = site,
      rang = rang,
      cle = cle,
      numTrans = numTrans,
      numAppel = numAppel,
      numQuestion = numQuestion,
      devise = devise,
      reference = reference,
      dateQ = dateQ).mapValues(Some(_))

    new RequestCtx(
      site = site,
      rang = rang,
      params = params)
  }

  def aCaptureFor(site: String,
                  rang: String,
                  cle: String,
                  numTrans: String,
                  numAppel: String,
                  numQuestion: String,
                  devise: String,
                  reference: String,
                  dateQ: String,
                  amount: Double): RequestCtx = {
    val params = PayboxHelper.createCaptureRequest(
      site = site,
      rang = rang,
      cle = cle,
      numTrans = numTrans,
      numAppel = numAppel,
      numQuestion = numQuestion,
      devise = devise,
      reference = reference,
      dateQ = dateQ,
      amount = amount).mapValues(Some(_))

    new RequestCtx(
      site = site,
      rang = rang,
      params = params)
  }

  class RequestCtx(site: String,
                   rang: String,
                   params: Map[String, Option[String]]) {
    def returns(numTrans: String,
                numAppel: String,
                numQuestion: String): Unit = {
      returns(Map(
        Fields.numTrans -> numTrans,
        Fields.numAppel -> numAppel,
        Fields.numQuestion -> numQuestion,
        Fields.site -> site,
        Fields.rang -> rang,
        Fields.authorisation -> "someAuthorization",
        Fields.codeResponse -> ErrorCodes.SUCCESS,
        Fields.commentaire -> "Demande traitée avec succès",
        Fields.refabonne -> "",
        Fields.porteur -> ""))
    }

    def getsRejected(): Unit = {
      returns(Map(
        Fields.numTrans -> "someNumTrans",
        Fields.numAppel -> "someNumAppel",
        Fields.numQuestion -> "0000000001", // Just some random value
        Fields.site -> site,
        Fields.rang -> rang,
        Fields.authorisation -> "XXXXXX",
        Fields.codeResponse -> ErrorCodes.INVALID_CARDHOLDER_NUMBER,
        Fields.commentaire -> "PAYBOX : Numéro de porteur invalide",
        Fields.refabonne -> "",
        Fields.porteur -> ""))
    }

    def getsUnauthorized(): Unit = {
      returns(Map(
        Fields.numTrans -> "0000000000",
        Fields.numAppel -> "0000000000",
        Fields.numQuestion -> "0000000001", // Just some random value
        Fields.site -> site,
        Fields.rang -> rang,
        Fields.authorisation -> "000000",
        Fields.codeResponse -> ErrorCodes.NO_ACCESS,
        Fields.commentaire -> "Non autorise"))
    }

    def returns(responseParams: Map[String, String]): Unit = {
      server.appendAll {
        case HttpRequest(
          HttpMethods.POST,
          Path("/"),
          _,
          entity,
          _) if isStubbedRequestEntity(entity) =>
            HttpResponse(
              status = StatusCodes.OK,
              entity = HttpEntity(
                ContentType(MediaTypes.`application/x-www-form-urlencoded`, HttpCharsets.`UTF-8`),
                urlEncode(responseParams)))
      }
    }

    private def isStubbedRequestEntity(entity: HttpEntity): Boolean = {
      val requestParams = urlDecode(entity.extractAsString)

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
