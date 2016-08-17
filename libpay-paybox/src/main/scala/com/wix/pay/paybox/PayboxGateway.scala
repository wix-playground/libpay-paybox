package com.wix.pay.paybox


import java.util.{List => JList}

import com.google.api.client.http._
import com.wix.pay.creditcard.CreditCard
import com.wix.pay.model.{CurrencyAmount, Customer, Deal}
import com.wix.pay.paybox.model._
import com.wix.pay.{PaymentErrorException, PaymentException, PaymentGateway, PaymentRejectedException}

import scala.collection.JavaConversions._
import scala.collection.{JavaConversions, mutable}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object Endpoints {
  val development = "https://preprod-ppps.paybox.com/PPPS.php"
  val main = "https://ppps.paybox.com/PPPS.php"
  val secondary = "https://ppps1.paybox.com/PPPS.php"
}

class PayboxGateway(requestFactory: HttpRequestFactory,
                    connectTimeout: Option[Duration] = None,
                    readTimeout: Option[Duration] = None,
                    numberOfRetries: Int = 0,
                    endpointUrl: String = Endpoints.main,
                    merchantParser: PayboxMerchantParser = new JsonPayboxMerchantParser,
                    authorizationParser: PayboxAuthorizationParser = new JsonPayboxAuthorizationParser) extends PaymentGateway {

  override def authorize(merchantKey: String, creditCard: CreditCard, currencyAmount: CurrencyAmount, customer: Option[Customer], deal: Option[Deal]): Try[String] = {
    Try {
      require(creditCard.csc.isDefined, "CSC is mandatory for PayBox")

      val merchant = merchantParser.parse(merchantKey)

      val request = PayboxHelper.createAuthorizeRequest(
        site = merchant.site,
        rang = merchant.rang,
        cle = merchant.cle,
        card = creditCard,
        currencyAmount = currencyAmount
      )
      val response = doRequest(request)

      authorizationParser.stringify(PayboxAuthorization(
        numTrans = response(Fields.numTrans),
        numAppel = response(Fields.numAppel),
        numQuestion = response(Fields.numQuestion),
        devise = request(Fields.devise),
        reference = request(Fields.reference),
        dateQ = request(Fields.dateQ)
      ))
    } match {
      case Success(authorizationKey) => Success(authorizationKey)
      case Failure(e: PaymentException) => Failure(e)
      case Failure(e) => Failure(PaymentErrorException(e.getMessage, e))
    }
  }

  private def doRequest(params: Map[String, String]): Map[String, String] = {
    val httpRequest = requestFactory.buildPostRequest(
      new GenericUrl(endpointUrl),
      new UrlEncodedContent(JavaConversions.mapAsJavaMap(params))
    )

    connectTimeout foreach (to => httpRequest.setConnectTimeout(to.toMillis.toInt))
    readTimeout foreach (to => httpRequest.setReadTimeout(to.toMillis.toInt))
    httpRequest.setNumberOfRetries(numberOfRetries)

    val response = extractAndCloseResponse(httpRequest.execute())
    verifyPayboxResponse(response)
    response
  }

  private def extractAndCloseResponse(httpResponse: HttpResponse) = {
    try {
      val params = mutable.LinkedHashMap[String, JList[String]]()
      UrlEncodedParser.parse(httpResponse.parseAsString(), mutableMapAsJavaMap(params))
      params.mapValues( _(0) ).toMap
    } finally {
      httpResponse.ignore()
    }
  }

  override def capture(merchantKey: String, authorizationKey: String, amount: Double): Try[String] = {
    Try {
      val merchant = merchantParser.parse(merchantKey)
      val authorization = authorizationParser.parse(authorizationKey)


      val request = PayboxHelper.createCaptureRequest(
        site = merchant.site,
        rang = merchant.rang,
        cle = merchant.cle,
        numTrans = authorization.numTrans,
        numAppel = authorization.numAppel,
        numQuestion = authorization.numQuestion,
        devise = authorization.devise,
        reference = authorization.reference,
        dateQ = authorization.dateQ,
        amount = amount
      )
      val response = doRequest(request)

      response(Fields.numTrans)
    } match {
      case Success(numTrans) => Success(numTrans)
      case Failure(e: PaymentException) => Failure(e)
      case Failure(e) => Failure(PaymentErrorException(e.getMessage, e))
    }
  }

  override def sale(merchantKey: String, creditCard: CreditCard, currencyAmount: CurrencyAmount, customer: Option[Customer], deal: Option[Deal]): Try[String] = {
    Try {
      require(creditCard.csc.isDefined, "CSC is mandatory for PayBox")

      val merchant = merchantParser.parse(merchantKey)

      val request = PayboxHelper.createSaleRequest(
        site = merchant.site,
        rang = merchant.rang,
        cle = merchant.cle,
        card = creditCard,
        currencyAmount = currencyAmount
      )
      val response = doRequest(request)

      response(Fields.numTrans)
    } match {
      case Success(authorizationKey) => Success(authorizationKey)
      case Failure(e: PaymentException) => Failure(e)
      case Failure(e) => Failure(PaymentErrorException(e.getMessage, e))
    }
  }

  override def voidAuthorization(merchantKey: String, authorizationKey: String): Try[String] = {
    Try {
      val merchant = merchantParser.parse(merchantKey)
      val authorization = authorizationParser.parse(authorizationKey)

      val request = PayboxHelper.createCancelRequest(
        site = merchant.site,
        rang = merchant.rang,
        cle = merchant.cle,
        numTrans = authorization.numTrans,
        numAppel = authorization.numAppel,
        numQuestion = authorization.numQuestion,
        devise = authorization.devise,
        reference = authorization.reference,
        dateQ = authorization.dateQ
      )
      val response = doRequest(request)

      response(Fields.numTrans)
    } match {
      case Success(numTrans) => Success(numTrans)
      case Failure(e: PaymentException) => Failure(e)
      case Failure(e) => Failure(PaymentErrorException(e.getMessage, e))
    }
  }



  private def verifyPayboxResponse(response: Map[String, String]): Unit = {
    val code = response(Fields.codeResponse)
    val message = response(Fields.commentaire)

    code match {
      case ErrorCodes.SUCCESS => // Operation successful.
      case ErrorCodes.INVALID_CARDHOLDER_NUMBER|
           ErrorCodes.INVALID_EXPIRATION|
           ErrorCodes.UNAUTHORIZED_CARD|
           ErrorCodes.UNAUTHORIZED_COUNTRY => throw PaymentRejectedException(message)
      case IsAuthorizationError(authorizationCode) => throw PaymentRejectedException(message)
      case _ => throw PaymentErrorException(message)
    }
  }

  private object IsAuthorizationError {
    val AUTHORIZATION_ERROR_PREFIX = "001"

    def unapply(code: String): Option[String]= {
      code match {
        case s if s.startsWith(AUTHORIZATION_ERROR_PREFIX) => Some(s.stripPrefix(AUTHORIZATION_ERROR_PREFIX))
        case _ => None
      }
    }
  }
}
