package com.wix.pay.paybox

trait PayboxAuthorizationParser {
  def parse(authorizationKey: String): PayboxAuthorization
  def stringify(authorization: PayboxAuthorization): String
}
