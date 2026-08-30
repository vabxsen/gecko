package com.gecko.core.common.util

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
