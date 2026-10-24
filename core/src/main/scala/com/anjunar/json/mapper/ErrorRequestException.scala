package com.anjunar.json.mapper

import java.util

/**
 * Every invalid value of one request, each with the path of the field it belongs to. It is an
 * IllegalArgumentException because the input, not the application, is at fault.
 */
class ErrorRequestException(val errors: util.List[ErrorRequest])
    extends IllegalArgumentException(ErrorRequestException.summary(errors))

private object ErrorRequestException {
  private def summary(errors: util.List[ErrorRequest]): String =
    if (errors.isEmpty) "Invalid request"
    else {
      val first = errors.get(0)
      val rest = if (errors.size() > 1) s" (and ${errors.size() - 1} more)" else ""
      s"${String.join(".", first.path.stream().map(String.valueOf).toList)}: ${first.message}$rest"
    }
}
