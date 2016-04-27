package com.wix.pay.paybox


import org.specs2.matcher.{AlwaysMatcher, Matcher}
import org.specs2.matcher.MustMatchers._
import org.specs2.mutable.SpecWithJUnit
import org.specs2.specification.Scope


class JsonPayboxMerchantParserTest extends SpecWithJUnit {
  trait Ctx extends Scope {
    val merchantParser: PayboxMerchantParser = new JsonPayboxMerchantParser
  }

  def bePayboxMerchant(site: Matcher[String] = AlwaysMatcher(),
                 rang: Matcher[String] = AlwaysMatcher(),
                 cle: Matcher[String] = AlwaysMatcher()): Matcher[PayboxMerchant] = {
    site ^^ { (_: PayboxMerchant).site aka "site" } and
      rang ^^ { (_: PayboxMerchant).rang aka "rang" } and
      cle ^^ { (_: PayboxMerchant).cle aka "cle" }
  }

  "stringify and then parse" should {
    "yield a merchant similar to the original one" in new Ctx {
      val someMerchant = PayboxMerchant(
        site = "some site",
        rang = "some rang",
        cle = "some cle"
      )

      val merchantKey = merchantParser.stringify(someMerchant)
      merchantParser.parse(merchantKey) must bePayboxMerchant(
        site = ===(someMerchant.site),
        rang = ===(someMerchant.rang),
        cle = ===(someMerchant.cle)
      )
    }
  }
}
