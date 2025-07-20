package org.milliytechnology.spiko.data.remote.models

/**
 * A generic response model for API calls that return a simple success message.
 * Used for actions like deleting or updating items where no specific data body is returned.
 */
data class GenericSuccessResponse(val message: String)