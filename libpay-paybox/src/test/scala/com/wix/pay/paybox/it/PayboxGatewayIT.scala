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

    val someAuthorization = PayboxAuthorization(
      numTrans = "someNumTrans",
      numAppel = "someNumAppel",
      numQuestion = "someNumQuestion",
      devise = "someDevise",
      reference = "someReference",
      dateQ = "someDateQ"
    )
    val authorizationKey = authorizationParser.stringify(someAuthorization)

    val paybox: PaymentGateway = new PayboxGateway(
      requestFactory = requestFactory,
      endpointUrl = s"http://localhost:$payboxPort/",
      merchantParser = merchantParser,
      authorizationParser = authorizationParser)

    driver.resetProbe()
  }

  "authorize request via PayBox gateway" should {
    "gracefully fail on invalid merchant key" in new Ctx {
      driver.anAuthorizeFor(
        site = someMerchant.site,
        rang = someMerchant.rang,
        cle = someMerchant.cle,
        card = someCreditCard,
        currencyAmount = someCurrencyAmount
      ) isUnauthorized()

      paybox.authorize(
        merchantKey = merchantKey,
        creditCard = someCreditCard,
        currencyAmount = someCurrencyAmount
      ) must beAFailedTry(
        check = beAnInstanceOf[PaymentErrorException]
      )
    }

    "successfully yield an authorization key on valid request" in new Ctx {
      driver.anAuthorizeFor(
        site = someMerchant.site,
        rang = someMerchant.rang,
        cle = someMerchant.cle,
        card = someCreditCard,
        currencyAmount = someCurrencyAmount
      ) returns(
        numTrans = someAuthorization.numTrans,
        numAppel = someAuthorization.numAppel,
        numQuestion = someAuthorization.numQuestion // In a perfect world, this should use the client supplied value
      )

      paybox.authorize(
        merchantKey = merchantKey,
        creditCard = someCreditCard,
        currencyAmount = someCurrencyAmount
      ) must beASuccessfulTry(
        check = beAuthorizationKey(
          authorization = beAuthorization(
            numTrans = ===(someAuthorization.numTrans),
            numAppel = ===(someAuthorization.numAppel),
            numQuestion = ===(someAuthorization.numQuestion),
            devise = ===(Conversions.toPayboxCurrency(someCurrencyAmount.currency)),
            reference = not(beEmpty),
            dateQ = not(beEmpty)
          )
        )
      )
    }

    "gracefully fail on rejected card" in new Ctx {
      driver.anAuthorizeFor(
        site = someMerchant.site,
        rang = someMerchant.rang,
        cle = someMerchant.cle,
        card = someCreditCard,
        currencyAmount = someCurrencyAmount
      ) isRejected()

      paybox.authorize(
        merchantKey = merchantKey,
        creditCard = someCreditCard,
        currencyAmount = someCurrencyAmount
      ) must beAFailedTry(
        check = beAnInstanceOf[PaymentRejectedException]
      )
    }
  }

  "capture request via PayBox gateway" should {
    "successfully yield a transaction ID on valid request" in new Ctx {
      val someAmount = 11.1

      driver.aCaptureFor(
        site = someMerchant.site,
        rang = someMerchant.rang,
        cle = someMerchant.cle,
        numTrans = someAuthorization.numTrans,
        numAppel = someAuthorization.numAppel,
        numQuestion = someAuthorization.numQuestion,
        devise = someAuthorization.devise,
        reference = someAuthorization.reference,
        dateQ = someAuthorization.dateQ,
        amount = someAmount
      ) returns(
        numAppel = someAuthorization.numAppel,
        numTrans = someAuthorization.numTrans,
        numQuestion = someAuthorization.numQuestion
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
      driver.aVoidAuthorizationFor(
        site = someMerchant.site,
        rang = someMerchant.rang,
        cle = someMerchant.cle,
        numTrans = someAuthorization.numTrans,
        numAppel = someAuthorization.numAppel,
        numQuestion = someAuthorization.numQuestion,
        devise = someAuthorization.devise,
        reference = someAuthorization.reference,
        dateQ = someAuthorization.dateQ
      ) returns(
        numTrans = someAuthorization.numTrans,
        numAppel = someAuthorization.numAppel,
        numQuestion = someAuthorization.numQuestion
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
