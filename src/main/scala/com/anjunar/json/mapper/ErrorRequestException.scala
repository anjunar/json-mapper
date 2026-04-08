package com.anjunar.json.mapper

class ErrorRequestException(val errors: java.util.List[ErrorRequest]) extends RuntimeException
