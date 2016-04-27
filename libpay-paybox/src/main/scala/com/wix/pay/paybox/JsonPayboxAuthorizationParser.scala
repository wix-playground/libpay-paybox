package com.wix.pay.paybox

import org.json4s.DefaultFormats
import org.json4s.native.Serialization

class JsonPayboxAuthorizationParser() extends PayboxAuthorizationParser {
  implicit val formats = DefaultFormats

  override def parse(authorizationKey: String): PayboxAuthorization = {
    Serialization.read[PayboxAuthorization](authorizationKey)
  }

  override def stringify(authorization: PayboxAuthorization): String = {
    Serialization.write(authorization)
  }
}
