package com.example.sci

object NavRoutes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val DETAIL = "detail/{deviceId}"

    fun detailRoute(deviceId: String): String = "detail/$deviceId"
}