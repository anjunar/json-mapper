package com.anjunar.json.mapper.provider

import java.util.UUID

trait EntityProvider {

  def id: UUID

  def version: Long

}
