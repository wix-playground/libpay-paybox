package com.wix.pay.paybox

import org.specs2.matcher.{AlwaysMatcher, Matcher, Matchers}

trait PayboxMatchers extends Matchers {
  def authorizationParser: PayboxAuthorizationParser

  def beAuthorization(numTrans: Matcher[String] = AlwaysMatcher(),
                      numAppel: Matcher[String] = AlwaysMatcher(),
                      numQuestion: Matcher[String] = AlwaysMatcher(),
                      devise: Matcher[String] = AlwaysMatcher(),
                      reference: Matcher[String] = AlwaysMatcher(),
                      dateQ: Matcher[String] = AlwaysMatcher()): Matcher[PayboxAuthorization] = {
    numTrans ^^ { (_: PayboxAuthorization).numTrans aka "numTrans" } and
      numAppel ^^ { (_: PayboxAuthorization).numAppel aka "numAppel" } and
      numQuestion ^^ { (_: PayboxAuthorization).numQuestion aka "numQuestion" } and
      devise ^^ { (_: PayboxAuthorization).devise aka "devise" } and
      reference ^^ { (_: PayboxAuthorization).reference aka "reference" } and
      dateQ ^^ { (_: PayboxAuthorization).dateQ aka "dateQ" }
  }

  def beAuthorizationKey(authorization: Matcher[PayboxAuthorization]): Matcher[String] = {
    authorization ^^ { authorizationParser.parse(_: String) aka "parsed authorization"}
  }

}

object PayboxMatchers extends PayboxMatchers {
  override val authorizationParser = new JsonPayboxAuthorizationParser()
}