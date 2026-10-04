package com.gecko.core.provider.internal

import com.gecko.core.model.error.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpErrorsTest {
    @Test
    fun permissionFailureIsNotMistakenForAnInvalidKey() {
        assertEquals(ErrorKind.PermissionDenied, classifyProviderError(403, "Access denied", null).kind)
        assertEquals(ErrorKind.InvalidApiKey, classifyProviderError(401, "Invalid key", null).kind)
        assertEquals(ErrorKind.InvalidApiKey, classifyProviderError(400, "API key not valid", null).kind)
    }
}
