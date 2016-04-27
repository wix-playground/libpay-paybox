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

      val request = createAuthorizeOrSaleRequest(
        requestType = RequestTypes.AUTHORIZATION_ONLY,
        merchant = merchant,
        creditCard = creditCard,
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
      case Failure(e) => Failure(new PaymentErrorException(e.getMessage, e))
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

      val request = createCaptureOrCancelRequest(
        requestType = RequestTypes.CAPTURE,
        merchant = merchant,
        authorization = authorization,
        amount = amount
      )
      val response = doRequest(request)

      response(Fields.numTrans)
    } match {
      case Success(numTrans) => Success(numTrans)
      case Failure(e: PaymentException) => Failure(e)
      case Failure(e) => Failure(new PaymentErrorException(e.getMessage, e))
    }
  }

  override def sale(merchantKey: String, creditCard: CreditCard, currencyAmount: CurrencyAmount, customer: Option[Customer], deal: Option[Deal]): Try[String] = {
    Try {
      require(creditCard.csc.isDefined, "CSC is mandatory for PayBox")

      val merchant = merchantParser.parse(merchantKey)

      val request = createAuthorizeOrSaleRequest(
        requestType = RequestTypes.AUTHORIZATION_CAPTURE,
        merchant = merchant,
        creditCard = creditCard,
        currencyAmount = currencyAmount
      )
      val response = doRequest(request)

      response(Fields.numTrans)
    } match {
      case Success(authorizationKey) => Success(authorizationKey)
      case Failure(e: PaymentException) => Failure(e)
      case Failure(e) => Failure(new PaymentErrorException(e.getMessage, e))
    }
  }

  override def voidAuthorization(merchantKey: String, authorizationKey: String): Try[String] = {
    Try {
      val merchant = merchantParser.parse(merchantKey)
      val authorization = authorizationParser.parse(authorizationKey)

      val request = createCaptureOrCancelRequest(
        requestType = RequestTypes.CANCEL,
        merchant = merchant,
        authorization = authorization
      )
      val response = doRequest(request)

      response(Fields.numTrans)
    } match {
      case Success(numTrans) => Success(numTrans)
      case Failure(e: PaymentException) => Failure(e)
      case Failure(e) => Failure(new PaymentErrorException(e.getMessage, e))
    }
  }

  private def createAuthorizeOrSaleRequest(requestType: String, merchant: PayboxMerchant, creditCard: CreditCard,
                                           currencyAmount: CurrencyAmount): Map[String, String] = {
    val timestamp = System.currentTimeMillis
    Map(
      Fields.version -> Versions.PAYBOX_DIRECT,
      Fields.`type` -> requestType,
      Fields.site -> merchant.site,
      Fields.rang -> merchant.rang,
      Fields.cle -> merchant.cle,
      Fields.numQuestion -> (timestamp / 1000).toString, // numQuestion must be in the range 1-2147483647
      Fields.montant -> Conversions.toPayboxAmount(currencyAmount.amount),
      Fields.devise -> Conversions.toPayboxCurrency(currencyAmount.currency),
      Fields.reference -> timestamp.toString,
      Fields.porteur -> creditCard.number,
      Fields.dateVal -> Conversions.toPayboxYearMonth(
        year = creditCard.expiration.year,
        month = creditCard.expiration.month
      ),
      Fields.cvv -> creditCard.csc.get,
      Fields.activite -> Sources.INTERNET, // optional
      Fields.dateQ -> Conversions.toPayboxDateTime(timestamp)
    )
  }

  private def createCaptureOrCancelRequest(requestType: String, merchant: PayboxMerchant, authorization: PayboxAuthorization,
                                           amount: Double = 0): Map[String, String] = {
    Map(
      Fields.version -> Versions.PAYBOX_DIRECT,
      Fields.`type` -> requestType,
      Fields.site -> merchant.site,
      Fields.rang -> merchant.rang,
      Fields.cle -> merchant.cle,
      Fields.numQuestion -> authorization.numQuestion,
      Fields.montant -> Conversions.toPayboxAmount(amount),
      Fields.devise -> authorization.devise,
      Fields.reference -> authorization.reference,
      Fields.numTrans -> authorization.numTrans,
      Fields.numAppel -> authorization.numAppel,
      Fields.dateQ -> authorization.dateQ
    )
  }

  private def verifyPayboxResponse(response: Map[String, String]): Unit = {
    val code = response(Fields.codeResponse)
    val message = response(Fields.commentaire)

    code match {
      case ErrorCodes.SUCCESS => // Operation successful.
      case ErrorCodes.INVALID_CARDHOLDER_NUMBER|
           ErrorCodes.INVALID_EXPIRATION|
           ErrorCodes.UNAUTHORIZED_CARD|
           ErrorCodes.UNAUTHORIZED_COUNTRY => throw new PaymentRejectedException(message)
      case IsAuthorizationError(authorizationCode) => throw new PaymentRejectedException(message)
      case _ => throw new PaymentErrorException(message)
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
