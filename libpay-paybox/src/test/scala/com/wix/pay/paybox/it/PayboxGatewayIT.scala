package com.wix.pay.paybox.it

import com.google.api.client.http.javanet.NetHttpTransport
import com.wix.pay.creditcard.{CreditCard, CreditCardOptionalFields, YearMonth}
import com.wix.pay.model.CurrencyAmount
import com.wix.pay.paybox.PayboxMatchers._
import com.wix.pay.paybox._
import com.wix.pay.paybox.model._
import com.wix.pay.paybox.testkit.PayboxDriver
import com.wix.pay.{PaymentErrorException, PaymentGateway, PaymentRejectedException}
import org.specs2.mutable.SpecWithJUnit
import org.specs2.specification.Scope


class PayboxGatewayIT extends SpecWithJUnit {
  val payboxPort = 10006

  val requestFactory = new NetHttpTransport().createRequestFactory()
  val driver = new PayboxDriver(port = payboxPort)
  step {
    driver.startProbe()
  }

  sequential

  trait Ctx extends Scope {
    val merchantParser = new JsonPayboxMerchantParser()
    val authorizationParser = new JsonPayboxAuthorizationParser()

    val paybox: PaymentGateway = new PayboxGateway(
      requestFactory = requestFactory,
      endpointUrl = s"http://localhost:$payboxPort/",
      merchantParser = merchantParser,
      authorizationParser = authorizationParser)

    driver.resetProbe()
  }

  "authorize request via PayBox gateway" should {
    "gracefully fail on invalid merchant key" in new Ctx {
      val someMerchant = PayboxMerchant(
        site = "someSite",
        rang = "someRang",
        cle = "someCle"
      )
      val merchantKey = merchantParser.stringify(someMerchant)

      val someCurrencyAmount = CurrencyAmount("USD", 33.3)
      val someAdditionalFields = CreditCardOptionalFields(csc = Some("123"))
      val someCreditCard = CreditCard(
        number = "4012888818888",
        expiration = YearMonth(2020, 12),
        additionalFields = Some(someAdditionalFields))

      driver.aRequestFor(Map(
        Fields.version -> Some(Versions.PAYBOX_DIRECT),
        Fields.`type` -> Some(RequestTypes.AUTHORIZATION_ONLY),
        Fields.site -> Some(someMerchant.site),
        Fields.rang -> Some(someMerchant.rang),
        Fields.cle -> Some(someMerchant.cle),
        Fields.numQuestion -> None,
        Fields.montant -> Some(Conversions.toPayboxAmount(someCurrencyAmount.amount)),
        Fields.devise -> Some(Conversions.toPayboxCurrency(someCurrencyAmount.currency)),
        Fields.reference -> None,
        Fields.porteur -> Some(someCreditCard.number),
        Fields.dateVal -> Some(Conversions.toPayboxYearMonth(
          year = someCreditCard.expiration.year,
          month = someCreditCard.expiration.month
        )),
        Fields.cvv -> Some(someCreditCard.csc.get),
        Fields.dateQ -> None
      )) returns Map(
        Fields.numTrans -> "0000000000",
        Fields.numAppel -> "0000000000",
        Fields.numQuestion -> "0000000001", // Just some random value
        Fields.site -> "0000000",
        Fields.rang -> "00",
        Fields.authorisation -> "000000",
        Fields.codeResponse -> ErrorCodes.NO_ACCESS,
        Fields.commentaire -> "Non autorise"
      )

      paybox.authorize(
        merchantKey = merchantKey,
        creditCard = someCreditCard,
        currencyAmount = someCurrencyAmount
      ) must beAFailedTry(
        check = beAnInstanceOf[PaymentErrorException]
      )
    }

    "successfully yield an authorization key on valid request" in new Ctx {
      val someMerchant = PayboxMerchant(
        site = "someSite",
        rang = "someRang",
        cle = "someCle"
      )
      val someMerchantKey = merchantParser.stringify(someMerchant)
      val someCurrencyAmount = CurrencyAmount("USD", 33.3)
      val someCreditCard = CreditCard(
        number = "4012888818888",
        expiration = YearMonth(2020, 12),
        additionalFields = Some(CreditCardOptionalFields(
          csc = Some("123"))))

      val someNumTrans = "someNumTrans"
      val someNumAppel = "someNumAppel"
      val someNumQuestion = "someNumQuestion"

      driver.aRequestFor(Map(
        Fields.version -> Some(Versions.PAYBOX_DIRECT),
        Fields.`type` -> Some(RequestTypes.AUTHORIZATION_ONLY),
        Fields.site -> Some(someMerchant.site),
        Fields.rang -> Some(someMerchant.rang),
        Fields.cle -> Some(someMerchant.cle),
        Fields.numQuestion -> None,
        Fields.montant -> Some(Conversions.toPayboxAmount(someCurrencyAmount.amount)),
        Fields.devise -> Some(Conversions.toPayboxCurrency(someCurrencyAmount.currency)),
        Fields.reference -> None,
        Fields.porteur -> Some(someCreditCard.number),
        Fields.dateVal -> Some(Conversions.toPayboxYearMonth(
          year = someCreditCard.expiration.year,
          month = someCreditCard.expiration.month
        )),
        Fields.cvv -> Some(someCreditCard.csc.get),
        Fields.dateQ -> None
      )) returns Map(
        Fields.numTrans -> someNumTrans,
        Fields.numAppel -> someNumAppel,
        Fields.numQuestion -> someNumQuestion, // In a perfect world, this should use the client supplied value
        Fields.site -> someMerchant.site,
        Fields.rang -> someMerchant.rang,
        Fields.authorisation -> "someAuthorization",
        Fields.codeResponse -> ErrorCodes.SUCCESS,
        Fields.commentaire -> "Demande traitée avec succès",
        Fields.refabonne -> "",
        Fields.porteur -> ""
      )

      paybox.authorize(
        merchantKey = someMerchantKey,
        creditCard = someCreditCard,
        currencyAmount = someCurrencyAmount
      ) must beASuccessfulTry(
        check = beAuthorizationKey(
          authorization = beAuthorization(
            numTrans = ===(someNumTrans),
            numAppel = ===(someNumAppel),
            numQuestion = ===(someNumQuestion),
            devise = ===(Conversions.toPayboxCurrency(someCurrencyAmount.currency)),
            reference = not(beEmpty),
            dateQ = not(beEmpty)
          )
        )
      )
    }

    "gracefully fail on rejected card" in new Ctx {
      val someMerchant = PayboxMerchant(
        site = "someSite",
        rang = "someRang",
        cle = "someCle"
      )
      val merchantKey = merchantParser.stringify(someMerchant)

      val someCurrencyAmount = CurrencyAmount("USD", 33.3)

      val someCreditCard = CreditCard(
        number = "4012888818888",
        expiration = YearMonth(2020, 12),
        additionalFields = Some(CreditCardOptionalFields(
          csc = Some("123"))))

      val someNumTrans = "someNumTrans"
      val someNumAppel = "someNumAppel"
      val commentaire = "PAYBOX : Numéro de porteur invalide"

      driver.aRequestFor(Map(
        Fields.version -> Some(Versions.PAYBOX_DIRECT),
        Fields.`type` -> Some(RequestTypes.AUTHORIZATION_ONLY),
        Fields.site -> Some(someMerchant.site),
        Fields.rang -> Some(someMerchant.rang),
        Fields.cle -> Some(someMerchant.cle),
        Fields.numQuestion -> None,
        Fields.montant -> Some(Conversions.toPayboxAmount(someCurrencyAmount.amount)),
        Fields.devise -> Some(Conversions.toPayboxCurrency(someCurrencyAmount.currency)),
        Fields.reference -> None,
        Fields.porteur -> Some(someCreditCard.number),
        Fields.dateVal -> Some(Conversions.toPayboxYearMonth(
          year = someCreditCard.expiration.year,
          month = someCreditCard.expiration.month
        )),
        Fields.cvv -> Some(someCreditCard.csc.get),
        Fields.dateQ -> None
      )) returns Map(
        Fields.numTrans -> someNumTrans,
        Fields.numAppel -> someNumAppel,
        Fields.numQuestion -> "0000000001", // Just some random value
        Fields.site -> someMerchant.site,
        Fields.rang -> someMerchant.rang,
        Fields.authorisation -> "XXXXXX",
        Fields.codeResponse -> ErrorCodes.INVALID_CARDHOLDER_NUMBER,
        Fields.commentaire -> commentaire,
        Fields.refabonne -> "",
        Fields.porteur -> ""
      )

      paybox.authorize(
        merchantKey = merchantKey,
        creditCard = someCreditCard,
        currencyAmount = someCurrencyAmount
      ) must beAFailedTry.like {
        case e: PaymentRejectedException => e.message must beEqualTo(commentaire)
      }
    }
  }

  "capture request via PayBox gateway" should {
    "successfully yield a transaction ID on valid request" in new Ctx {
      val someMerchant = PayboxMerchant(
        site = "someSite",
        rang = "someRang",
        cle = "someCle"
      )
      val merchantKey = merchantParser.stringify(someMerchant)

      val someAuthorization = PayboxAuthorization(
        numTrans = "someNumTrans",
        numAppel = "someNumAppel",
        numQuestion = "someNumQuestion",
        devise = "someDevise",
        reference = "someReference",
        dateQ = "someDateQ"
      )
      val authorizationKey = authorizationParser.stringify(someAuthorization)

      val someAmount = 11.1

      driver.aRequestFor(Map(
        Fields.version -> Some(Versions.PAYBOX_DIRECT),
        Fields.`type` -> Some(RequestTypes.CAPTURE),
        Fields.site -> Some(someMerchant.site),
        Fields.rang -> Some(someMerchant.rang),
        Fields.cle -> Some(someMerchant.cle),
        Fields.numQuestion -> Some(someAuthorization.numQuestion),
        Fields.montant -> Some(Conversions.toPayboxAmount(someAmount)),
        Fields.devise -> Some(someAuthorization.devise),
        Fields.reference -> Some(someAuthorization.reference),
        Fields.numTrans -> Some(someAuthorization.numTrans),
        Fields.numAppel -> Some(someAuthorization.numAppel),
        Fields.dateQ -> Some(someAuthorization.dateQ)
      )) returns Map(
        Fields.numTrans -> someAuthorization.numTrans,
        Fields.numAppel -> someAuthorization.numAppel,
        Fields.numQuestion -> someAuthorization.numQuestion,
        Fields.site -> someMerchant.site,
        Fields.rang -> someMerchant.rang,
        Fields.authorisation -> "XXXXXX",
        Fields.codeResponse -> ErrorCodes.SUCCESS,
        Fields.commentaire -> "Demande traitée avec succès",
        Fields.refabonne -> "",
        Fields.porteur -> ""
      )

      paybox.capture(
        merchantKey = merchantKey,
        authorizationKey = authorizationKey,
        amount = someAmount
      ) must beASuccessfulTry(
        check = ===(someAuthorization.numTrans)
      )
    }
  }

  "voidAuthorization request via PayBox gateway" should {
    "successfully yield a transaction ID on valid request" in new Ctx {
      val someMerchant = PayboxMerchant(
        site = "someSite",
        rang = "someRang",
        cle = "someCle"
      )
      val merchantKey = merchantParser.stringify(someMerchant)

      val someAuthorization = PayboxAuthorization(
        numTrans = "someNumTrans",
        numAppel = "someNumAppel",
        numQuestion = "someNumQuestion",
        devise = "someDevise",
        reference = "someReference",
        dateQ = "someDateQ"
      )
      val authorizationKey = authorizationParser.stringify(someAuthorization)

      driver.aRequestFor(Map(
        Fields.version -> Some(Versions.PAYBOX_DIRECT),
        Fields.`type` -> Some(RequestTypes.CANCEL),
        Fields.site -> Some(someMerchant.site),
        Fields.rang -> Some(someMerchant.rang),
        Fields.cle -> Some(someMerchant.cle),
        Fields.numQuestion -> Some(someAuthorization.numQuestion),
        Fields.montant -> Some(Conversions.toPayboxAmount(0)),
        Fields.devise -> Some(someAuthorization.devise),
        Fields.reference -> Some(someAuthorization.reference),
        Fields.numTrans -> Some(someAuthorization.numTrans),
        Fields.numAppel -> Some(someAuthorization.numAppel),
        Fields.dateQ -> Some(someAuthorization.dateQ)
      )) returns Map(
        Fields.numTrans -> someAuthorization.numTrans,
        Fields.numAppel -> someAuthorization.numAppel,
        Fields.numQuestion -> someAuthorization.numQuestion,
        Fields.site -> someMerchant.site,
        Fields.rang -> someMerchant.rang,
        Fields.authorisation -> "XXXXXX",
        Fields.codeResponse -> ErrorCodes.SUCCESS,
        Fields.commentaire -> "Demande traitée avec succès",
        Fields.refabonne -> "",
        Fields.porteur -> ""
      )

      paybox.voidAuthorization(
        merchantKey = merchantKey,
        authorizationKey = authorizationKey
      ) must beASuccessfulTry(
        check = ===(someAuthorization.numTrans)
      )
    }
  }

  step {
    driver.stopProbe()
  }
}
