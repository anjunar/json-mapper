package com.anjunar.json.mapper.provider

import java.util

trait EntityProvider {

  def id: util.UUID

  def version: Long

}
