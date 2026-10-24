package com.anjunar.json.mapper

import java.util

/**
 * A value the client sent that its field cannot take. The bean that owns the field records it and goes on with its
 * other fields, so one request reports every invalid value at once.
 */
private[mapper] final class InvalidValue(path: util.List[Any], message: String)
    extends RuntimeException(message, null, false, false) {

  def error: ErrorRequest = new ErrorRequest(path, message)

}
