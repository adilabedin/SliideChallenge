package com.androidlearning.sliide_challenge.core.data.networking

object ApiConstants {
    const val BASE_URL = "https://gorest.co.in/public/v2/"
    const val TIMEOUT_MILLIS = 20_000L

    const val USERS_ROUTE = "users"

    /** GoRest accepts up to 100; larger values silently fall back to its default of 10. */
    const val USERS_PAGE_SIZE = 20

    const val PAGE_PARAM = "page"
    const val PER_PAGE_PARAM = "per_page"
    const val PAGE_COUNT_HEADER = "x-pagination-pages"
}
