package com.wix.pay.paybox


import org.specs2.mutable.SpecWithJUnit
import org.specs2.specification.Scope
import com.wix.pay.paybox.PayboxMatchers._


class JsonPayboxAuthorizationParserTest extends SpecWithJUnit {
  trait Ctx extends Scope {
    val authorizationParser: PayboxAuthorizationParser = new JsonPayboxAuthorizationParser
  }

  "stringify and then parse" should {
    "yield an authorization similar to the original one" in new Ctx {
      val someAuthorization = PayboxAuthorization(
        numTrans = "some numTrans",
        numAppel = "some numAppel",
        numQuestion = "some numQuestion",
        devise = "some devise",
        reference = "some reference",
        dateQ = "some dateQ"
      )

      val authorizationKey = authorizationParser.stringify(someAuthorization)
      authorizationParser.parse(authorizationKey) must beAuthorization(
        numTrans = ===(someAuthorization.numTrans),
        numAppel = ===(someAuthorization.numAppel),
        numQuestion = ===(someAuthorization.numQuestion),
        devise = ===(someAuthorization.devise),
        reference = ===(someAuthorization.reference),
        dateQ = ===(someAuthorization.dateQ)
      )
    }
  }
}
