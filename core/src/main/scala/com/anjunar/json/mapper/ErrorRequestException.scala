package com.anjunar.json.mapper

import java.util

class ErrorRequestException(val errors: util.List[ErrorRequest]) extends RuntimeException
