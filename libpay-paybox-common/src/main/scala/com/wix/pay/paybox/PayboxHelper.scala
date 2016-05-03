package com.wix.pay.paybox

import com.wix.pay.creditcard.CreditCard
import com.wix.pay.model.CurrencyAmount
import com.wix.pay.paybox.model._

object PayboxHelper {
  def createSaleRequest(site: String,
                        rang: String,
                        cle: String,
                        card: CreditCard,
                        currencyAmount: CurrencyAmount): Map[String, String] = {
    createAuthorizeOrSaleRequest(
      requestType = RequestTypes.AUTHORIZATION_CAPTURE,
      site = site,
      rang = rang,
      cle = cle,
      card = card,
      currencyAmount = currencyAmount
    )
  }

  def createAuthorizeRequest(site: String,
                             rang: String,
                             cle: String,
                             card: CreditCard,
                             currencyAmount: CurrencyAmount): Map[String, String] = {
    createAuthorizeOrSaleRequest(
      requestType = RequestTypes.AUTHORIZATION_ONLY,
      site = site,
      rang = rang,
      cle = cle,
      card = card,
      currencyAmount = currencyAmount
    )

  }

  def createCaptureRequest(site: String,
                           rang: String,
                           cle: String,
                           numTrans: String,
                           numAppel: String,
                           numQuestion: String,
                           devise: String,
                           reference: String,
                           dateQ: String,
                           amount: Double): Map[String, String] = {
    createCaptureOrCancelRequest(
      requestType = RequestTypes.CAPTURE,
      site = site,
      rang = rang,
      cle = cle,
      numTrans = numTrans,
      numAppel = numAppel,
      numQuestion = numQuestion,
      devise = devise,
      reference = reference,
      dateQ = dateQ,
      amount = amount
    )
  }

  def createCancelRequest(site: String,
                          rang: String,
                          cle: String,
                          numTrans: String,
                          numAppel: String,
                          numQuestion: String,
                          devise: String,
                          reference: String,
                          dateQ: String): Map[String, String] = {
    createCaptureOrCancelRequest(
      requestType = RequestTypes.CANCEL,
      site = site,
      rang = rang,
      cle = cle,
      numTrans = numTrans,
      numAppel = numAppel,
      numQuestion = numQuestion,
      devise = devise,
      reference = reference,
      dateQ = dateQ
    )
  }

  private def createAuthorizeOrSaleRequest(requestType: String,
                                           site: String,
                                           rang: String,
                                           cle: String,
                                           card: CreditCard,
                                           currencyAmount: CurrencyAmount): Map[String, String] = {
    val timestamp = System.currentTimeMillis
    Map(
      Fields.version -> Versions.PAYBOX_DIRECT,
      Fields.`type` -> requestType,
      Fields.site -> site,
      Fields.rang -> rang,
      Fields.cle -> cle,
      Fields.numQuestion -> (timestamp / 1000).toString, // numQuestion must be in the range 1-2147483647
      Fields.montant -> Conversions.toPayboxAmount(currencyAmount.amount),
      Fields.devise -> Conversions.toPayboxCurrency(currencyAmount.currency),
      Fields.reference -> timestamp.toString,
      Fields.porteur -> card.number,
      Fields.dateVal -> Conversions.toPayboxYearMonth(
        year = card.expiration.year,
        month = card.expiration.month
      ),
      Fields.cvv -> card.csc.get,
      Fields.activite -> Sources.INTERNET, // optional
      Fields.dateQ -> Conversions.toPayboxDateTime(timestamp)
    )
  }

  private def createCaptureOrCancelRequest(requestType: String,
                                           site: String,
                                           rang: String,
                                           cle: String,
                                           numTrans: String,
                                           numAppel: String,
                                           numQuestion: String,
                                           devise: String,
                                           reference: String,
                                           dateQ: String,
                                           amount: Double = 0): Map[String, String] = {
    Map(
      Fields.version -> Versions.PAYBOX_DIRECT,
      Fields.`type` -> requestType,
      Fields.site -> site,
      Fields.rang -> rang,
      Fields.cle -> cle,
      Fields.numQuestion -> numQuestion,
      Fields.montant -> Conversions.toPayboxAmount(amount),
      Fields.devise -> devise,
      Fields.reference -> reference,
      Fields.numTrans -> numTrans,
      Fields.numAppel -> numAppel,
      Fields.dateQ -> dateQ
    )
  }
}
